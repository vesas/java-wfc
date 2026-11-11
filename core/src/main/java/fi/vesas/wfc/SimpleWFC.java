package fi.vesas.wfc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BooleanSupplier;

public class SimpleWFC {
    
    private static class Coord {
        public int x;
        public int y;
    }

    public static class CoordFromTo {

        public CoordFromTo(int x, int y, int toX, int toY, DIR dir) {
            this.from.x = x;
            this.from.y = y;
            this.to.x = toX;
            this.to.y = toY;
            this.dir = dir;
        }

        public DIR fromToDir() {
            return this.dir;
        }

        public DIR dir = DIR.N;
        public Coord from = new Coord();
        public Coord to = new Coord();
    }

    public enum DIR {
        N,E,S,W;

        // values() clones the array on every call; cache it
        private static final DIR[] VALUES = values();

        // Returns direction value rotated counter clockwise
        public DIR rotateCCW(int times) {
            int newOrd = (this.ordinal() - times + 4) % 4;
            return VALUES[newOrd];
        }

        // rotates clockwise
        public DIR rotateCW(int times) {
            int newOrd = (this.ordinal() + times) % 4;
            return VALUES[newOrd];
        }

        public DIR opposite() {
            return VALUES[(ordinal() + 2) % 4];
        }
    }

    //  Combination of state and direction allowed from that state
    public static class StateDir {
        public int state;
        public DIR dir;

        @Override
        public boolean equals(Object o) {
            
            if (this == o) {
                return true;
            }

            if (o == null) {
                return false;
            }
            
            if (getClass() != o.getClass()) {
                return false;
            }
            
            StateDir stateDir = (StateDir)o;
        
            return  this.state == stateDir.state && 
                    this.dir == stateDir.dir;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int hash = 7;
            hash += prime * hash + this.state;
            hash += prime * hash + this.dir.ordinal();
            return hash;

        }

        public String toString() {
            return "" + this.state + "[" + this.dir + "]";
        }
    }

    private Constraints constraints;

    public Constraints getConstraints() {
        return constraints;
    }

    public void setConstraints(Constraints constraints) {
        this.constraints = constraints;
    }

    private AllowedTileSource allowedTileSource;

    public AllowedTileSource getAllowedTileSource() {
        return allowedTileSource;
    }

    /**
     * Narrow each cell's candidate tile IDs to those allowed by the source.
     * Applied immediately to the current grid state; cells whose source returns
     * {@code null} are untouched. Setting a new source re-applies it on top of
     * the existing grid — calling this after construction and before
     * {@link #run()} is the intended flow.
     *
     * If every tile ID in a cell is disallowed, {@link #isContradiction()}
     * flips true and {@link #run()} returns immediately without a solution.
     */
    private BooleanSupplier cancellationCheck;

    /**
     * Install a cancellation probe. {@link #run()} polls this at the top of
     * each observe/propagate iteration; if it returns {@code true}, the solver
     * stops immediately and leaves the grid in its current (partial) state.
     *
     * Wire this to {@code ctx::isCancelled} from a {@link fi.vesas.wfc.gen.GenContext}
     * when running on a background thread so the game can abandon generation
     * when the player leaves the region.
     */
    public void setCancellationCheck(BooleanSupplier check) {
        this.cancellationCheck = check;
    }

    private boolean shouldAbort() {
        return cancellationCheck != null && cancellationCheck.getAsBoolean();
    }

    public void setAllowedTileSource(AllowedTileSource source) {
        this.allowedTileSource = source;
        if (source == null) {
            return;
        }

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int[] allowed = source.allowedTileIds(x, y);
                if (allowed == null) {
                    continue;
                }

                Set<Integer> allowedSet = new HashSet<>(allowed.length * 2);
                for (int id : allowed) {
                    allowedSet.add(id);
                }

                int[] currentTiles = idGrid[x][y];
                int[] currentRots = rotationGrid[x][y];
                int[] newTiles = new int[currentTiles.length];
                int[] newRots = new int[currentTiles.length];
                int newLen = 0;
                for (int i = 0; i < currentTiles.length; i++) {
                    if (allowedSet.contains(currentTiles[i])) {
                        newTiles[newLen] = currentTiles[i];
                        newRots[newLen] = currentRots[i];
                        newLen++;
                    }
                }

                setCell(x, y, Arrays.copyOf(newTiles, newLen), Arrays.copyOf(newRots, newLen));

                if (newLen == 0) {
                    contradiction = true;
                }
            }
        }
    }

    // grid of arrays of still possible tile id's
    private int idGrid[][][] = null;

    // grid of arrays of still possible tile rotations
    // rotation array contains 1,2,4,8 values superimposed, 8+4+2+1 = 15 means all rotations still possible
    // 1 = no rotation
    // 2 = one rotation counter clockwise (CCW)
    // 4 = two rotations CCW
    // 8 = three rotations CCW
    // x, y, [array of rotation values]
    private int rotationGrid[][][] = null;

    // width and height of grid
    private int width;
    private int height;


    // should the output texture tile?
    private boolean tilingVertical = false;
    private boolean tilingHorizontal = false;

    private boolean emptyAllowed = true;

    public void setEmptyAllowed(boolean emptyAllowed) {
        this.emptyAllowed = emptyAllowed;
    }

    public void setTilingVertical(boolean tilingVertical) {
        this.tilingVertical = tilingVertical;
    }

    public void setTilingHorizontal(boolean tilingHorizontal) {
        this.tilingHorizontal = tilingHorizontal;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isEmptyAllowed() {
        return emptyAllowed;
    }

    private Random random = new Random();

    // Per-tile-id selection weights. null = uniform (original behaviour, same RNG sequence).
    private double[] weights;
    private double[] weightLogWeights;

    /**
     * Set relative selection weights, indexed by tile ID. Missing entries
     * default to 1. A weight of 0 means "only if nothing else fits".
     * With weights set, cell selection uses Shannon entropy instead of the
     * candidate count. Pass null to go back to uniform choice.
     */
    public void setWeights(double[] weights) {
        if (weights == null) {
            this.weights = null;
            this.weightLogWeights = null;
            return;
        }
        int maxId = 0;
        for (int[][] col : idGrid) for (int[] cell : col) for (int t : cell) maxId = Math.max(maxId, t);
        int n = Math.max(maxId + 1, weights.length);
        this.weights = new double[n];
        this.weightLogWeights = new double[n];
        for (int i = 0; i < n; i++) {
            double w = i < weights.length ? weights[i] : 1.0;
            if (w < 0 || Double.isNaN(w)) {
                throw new IllegalArgumentException("Tile " + i + " weight must be >= 0, got " + w);
            }
            this.weights[i] = w;
            this.weightLogWeights[i] = w > 0 ? w * Math.log(w) : 0;
        }
    }

    public double[] getWeights() {
        return weights;
    }

    public int [][][] getRots() {
        return rotationGrid;
    }

    public int [][][] getGrid() {
        return idGrid;
    }

    public int [] getGrid(int x, int y) {
        return idGrid[x][y];
    }

    public int [] getRots(int x, int y) {
        return rotationGrid[x][y];
    }

    public SimpleWFC(int w, int h, int tileCount, boolean emptyAllowed, boolean rotationsAllowed) {
        
        this.emptyAllowed = emptyAllowed;
        this.width = w;
        this.height = h;

        idGrid = new int[w][h][tileCount];
        rotationGrid = new int[w][h][tileCount];

        for(int i = 0; i < tileCount;i++) {
            // 1 = no rot
            // 2 = one rotation (CCW)
            // 4 = two CCW
            // 8 = three CCW
            if(rotationsAllowed) {
                ALL_TILES.add(new TileAndRotation(i, 15));
            }
            else {
                ALL_TILES.add(new TileAndRotation(i, 1));
            }
        }

        for(int tempH = 0; tempH < this.height;tempH++) {
            for(int tempW = 0; tempW < this.width;tempW++) {

                int [] possibilities = idGrid[tempW][tempH];

                for(int i= 0; i < possibilities.length;i++) {
                    if(emptyAllowed) {
                        possibilities[i] = i;
                    }
                    else {
                        possibilities[i] = i + 1;
                    }
                }

                int [] rotations = rotationGrid[tempW][tempH];

                // 8 + 4 + 2 + 1 = 15 (means: all rotations still possible)
                for(int i= 0; i < rotations.length;i++) {
                    if(rotationsAllowed) {
                        rotations[i] = 15;
                    }
                    else {
                        rotations[i] = 1;
                    }
                }
            }
        }

    }

    private int lastModifiedX;
    private int lastModifiedY;

    public int getLastModifiedX() {
        return lastModifiedX;
    }

    public int getLastModifiedY() {
        return lastModifiedY;
    }

    // To force deterministic output, set the seed
    public void setSeed(int val) {
        random = new Random(val);
    }

    // "local" entropy means entropy just for this square
    public int getLocalEntrypyAt(int x, int y) {
        int [] possibilities = idGrid[x][y];
        return possibilities == null ? 0 : possibilities.length;
    }

    // "local" entropy means entropy just for this square
    public int getLocalEntrypyAt(int pos) {

        int stride = this.width;
        int y = pos / stride;
        int x = pos % stride;

        int [] possibilities = idGrid[x][y];
        return possibilities == null ? 0 : possibilities.length;
    }

    /**
     * Find position which has lowest number of choices, but higher than 1
     * @return position index in the grid (height * stride + width)
     */
    public int findLowEntropy() {

        if (weights != null) {
            return findLowShannonEntropy();
        }

        int stride = this.width;

        int lowestEntropy = 1231231231;
        int lowestEntropyPos = -1;

        for(int h = 0; h < this.height;h++) {
            for(int w = 0; w < this.width;w++) {

                int [] possibilities = idGrid[w][h];

                int temp = possibilities.length;

                if(temp > 1 && temp < lowestEntropy) {
                    lowestEntropy = temp;
                    lowestEntropyPos = h * stride + w;
                }
            }
        }

        return lowestEntropyPos;
    }

    // Weighted mode: H = log(sumW) - sum(w log w) / sumW over the cell's candidate tiles.
    // Collapsed cells (one candidate) are skipped. Zero-weight tiles add nothing to H.
    private int findLowShannonEntropy() {
        double lowest = Double.MAX_VALUE;
        int lowestPos = -1;
        for (int h = 0; h < this.height; h++) {
            for (int w = 0; w < this.width; w++) {
                int[] possibilities = idGrid[w][h];
                if (possibilities.length <= 1) continue;
                double sumW = 0, sumWLogW = 0;
                for (int t : possibilities) {
                    sumW += weights[t];
                    sumWLogW += weightLogWeights[t];
                }
                double entropy = sumW > 0 ? Math.log(sumW) - sumWLogW / sumW : 0;
                if (entropy < lowest) {
                    lowest = entropy;
                    lowestPos = h * this.width + w;
                }
            }
        }
        return lowestPos;
    }

    // Pick an index from choices. Uniform when no weights are set; otherwise weighted
    // by tile ID, falling back to uniform when every candidate has weight 0.
    private int pickIndex(List<TileAndRotation> choices) {
        if (weights == null) {
            return random.nextInt(choices.size());
        }
        double total = 0;
        for (TileAndRotation c : choices) total += weights[c.tile];
        if (total <= 0) {
            return random.nextInt(choices.size());
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < choices.size(); i++) {
            roll -= weights[choices.get(i).tile];
            if (roll < 0) return i;
        }
        // rounding: last candidate with non-zero weight
        for (int i = choices.size() - 1; i >= 0; i--) {
            if (weights[choices.get(i).tile] > 0) return i;
        }
        return choices.size() - 1;
    }

    public void setValuesAt(int x, int y, int ... values)
    {
        setCell(x, y, values, rotationGrid[x][y]);
    }

    public void setRotationsAt(int x, int y, int ... values)
    {
        setCell(x, y, idGrid[x][y], values);
    }

    /*
     * All cell writes go through here. Cell arrays are never modified in place,
     * only replaced, so while a backtrack point exists we record the old array
     * references on a trail. Backtracking undoes the trail back to the snapshot's
     * mark instead of deep-copying the whole grid at every decision.
     */
    private void setCell(int x, int y, int[] ids, int[] rots) {
        if (!backtrackStack.isEmpty()) {
            if (trailSize == trailCell.length) {
                int n = trailCell.length * 2;
                trailCell = Arrays.copyOf(trailCell, n);
                trailIds = Arrays.copyOf(trailIds, n);
                trailRots = Arrays.copyOf(trailRots, n);
            }
            trailCell[trailSize] = x * height + y;
            trailIds[trailSize] = idGrid[x][y];
            trailRots[trailSize] = rotationGrid[x][y];
            trailSize++;
        }
        idGrid[x][y] = ids;
        rotationGrid[x][y] = rots;
    }

    private int trailSize = 0;
    private int[] trailCell = new int[1024];
    private int[][] trailIds = new int[1024][];
    private int[][] trailRots = new int[1024][];

    private int [] getRandomValueAt(int index) {

        int stride = this.width;
        int y = index / stride;
        int x = index % stride;

        int [] temp = idGrid[x][y];

        int rand = random.nextInt(temp.length);

        int [] ret = { temp[rand] };
        return ret;
    }

    public void printConstraints() {
        this.constraints.printConstraints();
    }

    public void printGrid() {

        System.out.println("WFC GRID:");
        for(int h = this.height-1; h >= 0;h--) {
            for(int w = 0; w < this.width;w++) {

                int [] possibilities = idGrid[w][h];
                int posLen = possibilities.length;

                System.out.print(" [");
                for(int i = 0; i < posLen; i++) {
                    System.out.print("" + possibilities[i] );
                    if(i < (posLen-1)) {
                        System.out.print(",");
                    }
                }
                System.out.print("]");
            }
            System.out.println("");
        }
    }

    public void printRotations() {
        System.out.println("ROTATION GRID:");
        for(int h = this.height-1; h >= 0;h--) {
            for(int w = 0; w < this.width;w++) {

                int [] rots = rotationGrid[w][h];
                int rotsLen = rots.length;

                System.out.print(" [");
                int counter = 0;
                for (int rot : rots) {
                    System.out.print("" + rot );
                    if(counter < (rotsLen-1)) {
                        System.out.print(",");
                    }
                    counter++;
                }
                System.out.print("]");
            }
            System.out.println("");
        }
    }

    private List<TileAndRotation> ALL_TILES = new ArrayList<TileAndRotation>();

    private List<TileAndRotation> allTiles() {

        List<TileAndRotation> allTiles = new ArrayList<TileAndRotation>();
        allTiles.addAll(ALL_TILES);
        return allTiles;
    }

    private Set<TileAndRotation> allTilesSet() {

        Set<TileAndRotation> allTiles = new HashSet<TileAndRotation>();
        allTiles.addAll(ALL_TILES);
        return allTiles;
    }

    // In x, y position, looking at target dir, which tiles are allowed at the target position?
    private void filterAllowedTilesByTarget(int x, int y, DIR dir, Set<TileAndRotation> currentAllowedTiles) {

        Iterator<TileAndRotation> it = currentAllowedTiles.iterator();

        int [] targetTiles = idGrid[x][y];
        int [] targetRots = rotationGrid[x][y];
        
        while(it.hasNext()) {

            TileAndRotation tileAndRot = it.next();
            
            // int retRots = tileAndRot.rot;
            int retRots = 0;

            for(int t = 0; t < targetTiles.length; t++) 
            {
                int targetTile = targetTiles[t];
                int targetRot = targetRots[t];

                if((targetRot & 1)== 1) {
                
                    int port = constraints.getPort(targetTile, dir);
                    
                    // match allowed tiles witht the target, remove any that don't match
                    if(port > -1 && contains(port, tileAndRot.tile, tileAndRot.rot, dir)) {
                        retRots = retRots | ~1;
                    }
                }
                if((targetRot & 2)== 2) {
                    int port = constraints.getPort(targetTile, dir.rotateCCW(1));
                    
                    // match allowed tiles witht the target, remove any that don't match
                    if(port > -1 && contains(port, tileAndRot.tile, tileAndRot.rot, dir)) {
                        retRots = retRots | ~2;
                    }
                }
    
                if((targetRot & 4)== 4) {
                    int port = constraints.getPort(targetTile, dir.rotateCCW(2));
                    
                    // match allowed tiles witht the target, remove any that don't match
                    if(port > -1 && contains(port, tileAndRot.tile, tileAndRot.rot, dir)) {
                        retRots = retRots | ~4;
                    }
                }
    
                if((targetRot & 8)== 8) {
                    int port = constraints.getPort(targetTile, dir.rotateCCW(3));
                    
                    // match allowed tiles witht the target, remove any that don't match
                    if(port > -1 && contains(port, tileAndRot.tile, tileAndRot.rot, dir)) {
                        retRots = retRots | ~8;
                    }
                }
            }

            if(retRots == 0) {
                it.remove();
            }
            else {
                tileAndRot.rot = retRots;
            }
        }
    }

    private boolean contains(int port, int targetTile, int targetRot, DIR dir) {
        
        if((targetRot & 1) == 1) {
            int targetPort = constraints.getPort(targetTile, dir);
            if(targetPort == port) {
                return true;
            }
        }
        if((targetRot & 2) == 2) {
            int targetPort = constraints.getPort(targetTile, dir.rotateCCW(1));
            if(targetPort == port) {
                return true;
            }
        }
        if((targetRot & 4) == 4) {
            int targetPort = constraints.getPort(targetTile, dir.rotateCCW(2));
            if(targetPort == port) {
                return true;
            }
        }
        if((targetRot & 8) == 8) {
            int targetPort = constraints.getPort(targetTile, dir.rotateCCW(3));
            if(targetPort == port) {
                return true;
            }
        }

        return false;
    }

    private boolean containsOnlyPorts(int [] targetTiles, int[] targetRots, DIR dir) {

        for(int i = 0; i < targetTiles.length; i++) {

            int targetTile = targetTiles[i];
            int targetRot = targetRots[i];

            if((targetRot & 1) == 1) {
                int targetPort = constraints.getPort(targetTile, dir);
                if(targetPort == -1) {
                    return false;
                }
            }
            if((targetRot & 2) == 2) {
                int targetPort = constraints.getPort(targetTile, dir.rotateCCW(1));
                if(targetPort == -1) {
                    return false;
                }
            }
            if((targetRot & 4) == 4) {
                int targetPort = constraints.getPort(targetTile, dir.rotateCCW(2));
                if(targetPort == -1) {
                    return false;
                }
            }
            if((targetRot & 8) == 8) {
                int targetPort = constraints.getPort(targetTile, dir.rotateCCW(3));
                if(targetPort == -1) {
                    return false;
                }
            }
        }
        
        return true;
    }

    private boolean containsAny(int port, int [] targetTiles, int[] targetRots, DIR dir) {
        
        // for each target tile check all rots and see if it's ports contains the requested port
        for(int i = 0; i < targetTiles.length; i++) {

            int targetTile = targetTiles[i];
            int targetRot = targetRots[i];

            if((targetRot & 1) == 1) {
                int targetPort = constraints.getPort(targetTile, dir);
                if(targetPort == port) {
                    return true;
                }
            }
            if((targetRot & 2) == 2) {
                int targetPort = constraints.getPort(targetTile, dir.rotateCCW(1));
                if(targetPort == port) {
                    return true;
                }
            }
            if((targetRot & 4) == 4) {
                int targetPort = constraints.getPort(targetTile, dir.rotateCCW(2));
                if(targetPort == port) {
                    return true;
                }
            }
            if((targetRot & 8) == 8) {
                int targetPort = constraints.getPort(targetTile, dir.rotateCCW(3));
                if(targetPort == port) {
                    return true;
                }
            }
        }
        

        return false;
    }

    // In x, y position, looking at dir, which tiles are allowed at the x,y position?
    private void filterAllowedTilesAtSource(int x, int y, DIR dir, Set<TileAndRotation> currentAllowedTiles) {
       
        Iterator<TileAndRotation> it = currentAllowedTiles.iterator();
        while(it.hasNext()) {

            TileAndRotation tileAndRot = it.next();
            
            int retRots = tileAndRot.rot;

            // check each target 
            int [] targetTiles = null;
            int [] targetRots = null;

            int targetX, targetY;

            if(dir == DIR.N) {

                targetX = x;
                if(y == this.height-1) {
                    targetY = 0;
                }
                else {
                    targetY = y + 1;
                }
                targetTiles = idGrid[targetX][targetY];
                targetRots = rotationGrid[targetX][targetY];
            }
            else if(dir == DIR.S) {
                targetX = x;

                if(y == 0) {
                    targetY = this.height-1;
                }
                else {
                    targetY = y - 1;
                }

                targetTiles = idGrid[targetX][targetY];
                targetRots = rotationGrid[targetX][targetY];
            }
            else if(dir == DIR.W) {

                targetY = y;
                if(x == 0) {
                    targetX = this.width - 1;
                }
                else {
                    targetX = x - 1;
                }
                targetTiles = idGrid[targetX][targetY];
                targetRots = rotationGrid[targetX][targetY];
            }
            else if(dir == DIR.E) {

                targetY = y;
                if(x == this.width-1) {
                    targetX = 0;
                }
                else {
                    targetX = x + 1;
                }
                targetTiles = idGrid[targetX][targetY];
                targetRots = rotationGrid[targetX][targetY];
            }

            if((tileAndRot.rot & 1)== 1) {
                int port = constraints.getPort(tileAndRot.tile, dir);
                // match allowed tiles witht the target, remove any that don't match
                if(port > -1) {
                    if(!containsAny(port, targetTiles, targetRots, dir.opposite())) {
                        retRots = retRots & ~1;
                    }
                }
                else {
                    if(containsOnlyPorts(targetTiles, targetRots, dir.opposite())) {
                        retRots = retRots & ~1;
                    }
                }
            }
            if((tileAndRot.rot & 2)== 2) {
                int port = constraints.getPort(tileAndRot.tile, dir.rotateCCW(1));
                
                if(port > -1) {
                    if(!containsAny(port, targetTiles, targetRots, dir.opposite())) {
                        retRots = retRots & ~2;
                    }
                }
                else {
                    if(containsOnlyPorts(targetTiles, targetRots, dir.opposite())) {
                        retRots = retRots & ~2;
                    }
                }
            }

            if((tileAndRot.rot & 4)== 4) {
                int port = constraints.getPort(tileAndRot.tile, dir.rotateCCW(2));
                
                if(port > -1) {
                    if(!containsAny(port, targetTiles, targetRots, dir.opposite())) {
                        retRots = retRots & ~4;
                    }
                }
                else {
                    if(containsOnlyPorts(targetTiles, targetRots, dir.opposite())) {
                        retRots = retRots & ~4;
                    }
                }
            }

            if((tileAndRot.rot & 8)== 8) {
                int port = constraints.getPort(tileAndRot.tile, dir.rotateCCW(3));
                
                if(port > -1)
                {
                    if(!containsAny(port, targetTiles, targetRots, dir.opposite())) {
                        retRots = retRots & ~8;
                    }
                }
                else {
                    if(containsOnlyPorts(targetTiles, targetRots, dir.opposite())) {
                        retRots = retRots & ~8;
                    }
                } 
            }

            if(retRots == 0) {
                it.remove();
            }
            else {
                tileAndRot.rot = retRots;
            }
        }
    }


    public void setPos(int x, int y, int value) {
        setCell(x, y, new int[] { value }, rotationGrid[x][y]);
    }

    public void setRotation(int x, int y, int value) {
        setCell(x, y, idGrid[x][y], new int[] { value });
    }

    // filter out ports pointing at dir, because we are edge looking at dir
    private void filterOutPorts(int x, int y, DIR dir, Set<TileAndRotation> currentAllowedTiles) {

        Iterator<TileAndRotation> it = currentAllowedTiles.iterator();
        while(it.hasNext()) {

            TileAndRotation tileAndRot = it.next();
            
            int retRots = tileAndRot.rot;

            if((tileAndRot.rot & 1)== 1) {
                // List<Integer> transitions = constraints.getTransitions(tileAndRot.tile, dir);
                int port = constraints.getPort(tileAndRot.tile, dir);
                
                // we have a port
                if(port > -1) {
                    retRots = retRots & ~1;
                }
            }
            if((tileAndRot.rot & 2)== 2) {
                int port = constraints.getPort(tileAndRot.tile, dir.rotateCCW(1));
                
                // we have a port
                if(port > -1) {
                    retRots = retRots & ~2;
                }
            }

            if((tileAndRot.rot & 4)== 4) {
                int port = constraints.getPort(tileAndRot.tile, dir.rotateCCW(2));
                
                // we have a port
                if(port > -1) {
                    retRots = retRots & ~4;
                }
            }

            if((tileAndRot.rot & 8)== 8) {
                int port =  constraints.getPort(tileAndRot.tile, dir.rotateCCW(3));
                
                // we have a port
                if(port > -1) {
                    retRots = retRots & ~8;
                }
            }

            if(retRots == 0) {
                it.remove();
            }
            else {
                tileAndRot.rot = retRots;
            }
        }

    }

    private void filterTileChoices(int x, int y, DIR dir, Set<TileAndRotation> currentAllowedTiles) {

        if(dir == DIR.N) {
            
            if(y == (this.height-1) && !this.tilingVertical) {
                // At top edge
                filterOutPorts(x,y,dir, currentAllowedTiles);
            }
            else {
                // not at top edge or tiling
                filterAllowedTilesAtSource(x,y,dir, currentAllowedTiles);
                // filterAllowedTilesByTarget(x,y+1, dir.opposite(), currentAllowedTiles);
            }
        }
        else if(dir == DIR.S) {

            if(y == 0 && !this.tilingVertical) {
                // At bottom edge
                filterOutPorts(x,y,dir, currentAllowedTiles);
            }
            else {
                // not at bottom edge or tiling
                filterAllowedTilesAtSource(x,y,dir, currentAllowedTiles);
            }
        }
        else if(dir == DIR.W) {
            if(x == 0 && !this.tilingHorizontal) {
                // At left edge
                filterOutPorts(x,y,dir, currentAllowedTiles);
            }
            else {
                // not at left edge, or tiling
                filterAllowedTilesAtSource(x,y,dir, currentAllowedTiles);
            }
        }
        else if(dir == DIR.E) {
            if(x == (this.width-1) && !this.tilingHorizontal) {
                // At right edge
                filterOutPorts(x,y,dir, currentAllowedTiles);
            }
            else {
                // not at right edge, or tiling
                filterAllowedTilesAtSource(x,y,dir, currentAllowedTiles);
            }
        }
    }

    public class TileAndRotation {

        public TileAndRotation(int tile, int rotation) {
            this.tile = tile;
            this.rot = rotation;
        }
        
        // just tile id
        public int tile;
        // 0, 1, 2, 4 [N, E, S, W]
        public int rot;

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getEnclosingInstance().hashCode();
            result = prime * result + rot;
            result = prime * result + tile;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }

            TileAndRotation other = (TileAndRotation) obj;
            if (!getEnclosingInstance().equals(other.getEnclosingInstance())) {
                return false;
            }
            if (rot != other.rot) {
                return false;
            }
                
            if (tile != other.tile) {
                return false;
            }

            return true;
        }
        private SimpleWFC getEnclosingInstance() {
            return SimpleWFC.this;
        }

        @Override
        public String toString() {
            return "TileAndRotation [rot=" + rot + ", tile=" + tile + "]";
        }
    }

    private Set<TileAndRotation> tilesAndRotations(int x, int y) {
        int [] sourceTiles = idGrid[x][y];
        int [] sourceRotations = rotationGrid[x][y];

        // LinkedHashSet just for debugging repeatability with fixed seed
        Set<TileAndRotation> ret = new LinkedHashSet<TileAndRotation>();

        for(int i = 0; i < sourceTiles.length; i++) {
            int tile = sourceTiles[i];
            int rots = sourceRotations[i];

            ret.add(new TileAndRotation(tile, rots));
        }

        return ret;
    }

    public void observe() {

        int pos = this.findLowEntropy();

        int lowestEntropy = -1;
        if(pos > -1) {
            lowestEntropy = this.getLocalEntrypyAt(pos);
        }

        // not found, we finished?
        if(lowestEntropy == -1) {
            finished = true;
            return;
        }

        int stride = this.width;
        int y = pos / stride;
        int x = pos % stride;

        Set<TileAndRotation> allAllowedTiles = this.tilesAndRotations(x,y);

        // check allowedTiles set against all directions, and remove impossible ones
        filterTileChoices(x, y, DIR.N, allAllowedTiles);
        filterTileChoices(x, y, DIR.S, allAllowedTiles);
        filterTileChoices(x, y, DIR.E, allAllowedTiles);
        filterTileChoices(x, y, DIR.W, allAllowedTiles);

        if(allAllowedTiles.isEmpty()) {
            contradiction = true;
            return;
        }

        // Save snapshot before making a choice (only when there IS a choice)
        if(allAllowedTiles.size() > 1) {
            Snapshot snapshot = new Snapshot();
            snapshot.trailMark = trailSize;
            snapshot.x = x;
            snapshot.y = y;
            snapshot.remainingChoices = new ArrayList<>(allAllowedTiles);
            backtrackStack.push(snapshot);
        }

        // Pick one choice and collapse
        List<TileAndRotation> choices = new ArrayList<>(allAllowedTiles);
        int rand = pickIndex(choices);
        TileAndRotation chosen = choices.get(rand);

        // Remove the chosen option from the snapshot so we don't retry it
        if(!backtrackStack.isEmpty()) {
            Snapshot top = backtrackStack.peek();
            if(top.x == x && top.y == y) {
                top.remainingChoices.remove(chosen);
            }
        }

        collapseCell(x, y, chosen);

    }

    private void collapseCell(int x, int y, TileAndRotation choice) {
        int bitsOn = bitsOn(choice.rot);
        int randRot = random.nextInt(bitsOn);

        // pick the randRot-th set bit, counting from the highest (8, 4, 2, 1)
        int chosenRot = 0;
        int counter = 0;
        for(int bit = 8; bit >= 1; bit >>= 1) {
            if((choice.rot & bit) == bit) {
                if(counter == randRot) {
                    chosenRot = bit;
                }
                counter++;
            }
        }

        setCell(x, y, new int[] { choice.tile }, new int[] { chosenRot });

        this.lastModifiedX = x;
        this.lastModifiedY = y;
    }

    // Attempt to backtrack to a previous decision point and try a different choice.
    // Returns true if backtracking succeeded (a new choice was made), false if exhausted.
    private boolean backtrack() {

        while(!backtrackStack.isEmpty()) {
            Snapshot snapshot = backtrackStack.peek();

            if(snapshot.remainingChoices.isEmpty()) {
                // All choices exhausted at this level, go back further
                backtrackStack.pop();
                continue;
            }

            // Restore the grid state from before the failed choice
            undoTrail(snapshot.trailMark);
            contradiction = false;
            backtrackCount++;

            // Pick a different choice
            int rand = pickIndex(snapshot.remainingChoices);
            TileAndRotation chosen = snapshot.remainingChoices.remove(rand);

            collapseCell(snapshot.x, snapshot.y, chosen);
            return true;
        }

        // Stack empty — no solution exists
        return false;
    }

    private int bitsOn(int val) {
        int count = 0;
        while (val > 0) {
            count += val & 1;
            val >>= 1;
        }
        return count;
    }

    // Propagation work queue: entries of (fromCell, toCell, dir), cell = x * height + y.
    private int[] queue = new int[3 * 256];
    private int queueHead, queueTail;

    private void enqueue(int fromX, int fromY, int toX, int toY, int dir) {
        if (queueTail + 3 > queue.length) {
            queue = Arrays.copyOf(queue, queue.length * 2);
        }
        queue[queueTail++] = fromX * height + fromY;
        queue[queueTail++] = toX * height + toY;
        queue[queueTail++] = dir;
    }

    // DIR ordinals
    private static final int DN = 0, DE = 1, DS = 2, DW = 3;

    // Queue the neighbours of (x, y), skipping direction skipDir (-1 = none).
    // Order N, S, W, E matches the original propagation order.
    private void enqueueNeighbours(int x, int y, int skipDir) {
        if(skipDir != DN) {
            if(y == height-1) {
                if(this.tilingVertical) enqueue(x, y, x, 0, DN);
            }
            else enqueue(x, y, x, y+1, DN);
        }
        if(skipDir != DS) {
            if(y == 0) {
                if(this.tilingVertical) enqueue(x, y, x, height-1, DS);
            }
            else enqueue(x, y, x, y-1, DS);
        }
        if(skipDir != DW) {
            if(x == 0) {
                if(this.tilingHorizontal) enqueue(x, y, width-1, y, DW);
            }
            else enqueue(x, y, x-1, y, DW);
        }
        if(skipDir != DE) {
            if(x == width-1) {
                if(this.tilingHorizontal) enqueue(x, y, 0, y, DE);
            }
            else enqueue(x, y, x+1, y, DE);
        }
    }

    public void propagate() {

        queueHead = 0;
        queueTail = 0;

        // Add all neighbours of the last modified position
        enqueueNeighbours(this.lastModifiedX, this.lastModifiedY, -1);

        // Propagate the changes
        while(queueHead < queueTail) {

            int from = queue[queueHead++];
            int to = queue[queueHead++];
            int dir = queue[queueHead++];

            int toX = to / height, toY = to % height;
            boolean changed = checkProp(from / height, from % height, toX, toY, dir);

            if(contradiction) {
                queueHead = queueTail = 0;
                return;
            }

            if(changed) {
                // add all neighbours of the changed cell, except back toward the source
                enqueueNeighbours(toX, toY, (dir + 2) & 3);
            }
        }

        queueHead = queueTail = 0;

        finished = true;
    }

    // Reusable "set of ports" for checkProp: port p is in the set when portSeen[p - minPort] == portStamp.
    private int[] portSeen = new int[0];
    private int portStamp = 0;

    // narrows tiles, and
    // returns true if something changed, add all other dirs except from
    public boolean checkSinglePropTile(CoordFromTo coordFromTo) {
        return checkProp(coordFromTo.from.x, coordFromTo.from.y,
                coordFromTo.to.x, coordFromTo.to.y, coordFromTo.dir.ordinal());
    }

    private boolean checkProp(int fromX, int fromY, int toX, int toY, int dir) {

        int [] checktilesTo = idGrid[toX][toY];
        int [] checkrotsTo = rotationGrid[toX][toY];

        int [] checktilesFrom = idGrid[fromX][fromY];
        int [] checkrotsFrom = rotationGrid[fromX][fromY];

        int opposite = (dir + 2) & 3;

        int lo = constraints.getMinPort();
        int size = constraints.getMaxPort() - lo + 1;
        if (portSeen.length < size) {
            portSeen = new int[size];
            portStamp = 0;
        }
        if (++portStamp == 0) {
            Arrays.fill(portSeen, 0);
            portStamp = 1;
        }
        final int[] seen = portSeen;
        final int stamp = portStamp;

        // Collect the ports the source cell can present toward dir.
        // A tile rotated j times CCW faces world direction d with its own side (d - j) mod 4.
        for(int i = 0; i < checktilesFrom.length; i++) {
            int tile = checktilesFrom[i];
            int rot = checkrotsFrom[i];
            for(int j = 0; j < 4; j++) {
                if((rot & (1 << j)) != 0) {
                    seen[constraints.getPort(tile, (dir - j) & 3) - lo] = stamp;
                }
            }
        }

        // Find which target options survive. Allocate only if something changes.
        int [] newtiles = null;
        int [] newrots = null;
        int newlen = 0;

        for(int i = 0; i < checktilesTo.length; i++) {
            int tileTo = checktilesTo[i];
            int rotTo = checkrotsTo[i];

            int newrot = rotTo;
            for(int j = 0; j < 4; j++) {
                int bit = 1 << j;
                if((rotTo & bit) != 0
                        && seen[constraints.getPort(tileTo, (opposite - j) & 3) - lo] != stamp) {
                    newrot &= ~bit;
                }
            }

            if(newrot != rotTo && newtiles == null) {
                // first change: copy the unchanged prefix
                newtiles = new int[checktilesTo.length];
                newrots = new int[checktilesTo.length];
                System.arraycopy(checktilesTo, 0, newtiles, 0, i);
                System.arraycopy(checkrotsTo, 0, newrots, 0, i);
                newlen = i;
            }

            if(newtiles != null && newrot > 0) {
                newtiles[newlen] = tileTo;
                newrots[newlen] = newrot;
                newlen++;
            }
        }

        if(newtiles == null) {
            if(checktilesTo.length == 0) {
                contradiction = true;
            }
            return false;
        }

        setCell(toX, toY, Arrays.copyOf(newtiles, newlen), Arrays.copyOf(newrots, newlen));

        if(newlen == 0) {
            contradiction = true;
        }

        return true;
    }

    public boolean isFinished() {

        // Loop over the whole grid, if any tile has more than one possibility, we are not finished
        for(int h = 0; h < this.height;h++) {
            for(int w = 0; w < this.width;w++) {

                // Get values which are still possible 
                int [] possibilities = idGrid[w][h];

                // We still have multiple possibilities
                if(possibilities.length > 1) {
                    return false;
                }
            }
        }

        return true;
    }

    // runs only one round of observe -> propagate cycle
    public void runOneRound() {

        observe();

        if(contradiction) {
            if(!backtrack()) {
                finished = true;
                return;
            }
            // backtrack succeeded — propagate from the new choice
        }

        if(!finished) {
            propagate();
        }

        // contradiction during propagation — try to backtrack
        if(contradiction) {
            if(!backtrack()) {
                finished = true;
                return;
            }
            // backtrack restored state and made a new choice, propagate it
            propagate();
        }

        finished = this.isFinished();
    }

    // runs the observe -> propagate cycle until finished
    public void run() {

        this.finished = false;

        while(!finished) {

            if(shouldAbort()) {
                finished = true;
                return;
            }

            observe();

            if(contradiction) {
                if(!backtrack()) {
                    finished = true;
                    return;
                }
            }

            if(!finished) {
                propagate();
            }

            while(contradiction) {
                if(!backtrack()) {
                    finished = true;
                    return;
                }
                propagate();
            }

            finished = this.isFinished();
        }

    }

    private boolean finished = false;
    private boolean contradiction = false;
    private int backtrackCount = 0;

    public int getBacktrackCount() {
        return backtrackCount;
    }

    public boolean isContradiction() {
        return contradiction;
    }

    private static class Snapshot {
        // trail length when the snapshot was taken; undoing back to it restores the grid
        int trailMark;
        int x, y;
        List<TileAndRotation> remainingChoices;
    }

    private Deque<Snapshot> backtrackStack = new ArrayDeque<>();

    private void undoTrail(int mark) {
        while(trailSize > mark) {
            trailSize--;
            int cell = trailCell[trailSize];
            int x = cell / height;
            int y = cell % height;
            idGrid[x][y] = trailIds[trailSize];
            rotationGrid[x][y] = trailRots[trailSize];
            trailIds[trailSize] = null;
            trailRots[trailSize] = null;
        }
    }
}
