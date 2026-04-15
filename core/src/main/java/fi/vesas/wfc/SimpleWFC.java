package fi.vesas.wfc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

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
        
        // Returns direction value rotated counter clockwise 
        public DIR rotateCCW(int times) {
            int newOrd = (this.ordinal() - times + 4) % 4;
            return DIR.values()[newOrd];
        }

        // rotates clockwise
        public DIR rotateCW(int times) {
            int newOrd = (this.ordinal() + times) % 4;
            return DIR.values()[newOrd];
        }

        public DIR opposite() {
            return DIR.values()[(ordinal() + 2) % 4];
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

    private Random random = new Random();

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

    public void setValuesAt(int x, int y, int ... values)
    {
        idGrid[x][y] = values;
    }

    public void setRotationsAt(int x, int y, int ... values)
    {
        rotationGrid[x][y] = values;
    }

    private void setValuesAt(int index, int [] values) {

        int stride = this.width;
        int y = index / stride;
        int x = index % stride;

        idGrid[x][y] = values;
    }

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
        int [] temp = { value };
        idGrid[x][y] = temp;
    }

    public void setRotation(int x, int y, int value) {
        int [] temp = { value };
        rotationGrid[x][y] = temp;
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

        // Ok, multiple choices available, now OBSERVE (choose random one) and collapse to one choice
        if(allAllowedTiles.size() > 1) {

            int rand = random.nextInt(allAllowedTiles.size());

            int currentIndex = 0;
            for(TileAndRotation in : allAllowedTiles) {

                if(rand == currentIndex) {

                    this.setPos(x,y, in.tile);

                    int bitsOn = bitsOn(in.rot);
                    int randRot = random.nextInt(bitsOn);

                    int counter = 0;
                    if((in.rot & 8) == 8) {
                        if(counter == randRot) {
                            this.setRotation(x,y, 8);
                        }
                        counter++;
                    }
                    if((in.rot & 4) == 4) {
                        if(counter == randRot) {
                            this.setRotation(x,y, 4);
                        }
                        counter++;
                    }
                    if((in.rot & 2) == 2) {
                        if(counter == randRot) {
                            this.setRotation(x,y,2);
                        }
                        counter++;
                    }
                    if((in.rot & 1) == 1) {
                        if(counter == randRot) {
                            this.setRotation(x,y, 1);
                        }
                        counter++;
                    }

                    this.lastModifiedX = x;
                    this.lastModifiedY = y;
                    break;
                }

                currentIndex++;
            }
        }
        else if(allAllowedTiles.size() == 1) {

            for(TileAndRotation in : allAllowedTiles) {

                this.setPos(x,y, in.tile);

                int bitsOn = bitsOn(in.rot);
                int randRot = random.nextInt(bitsOn);

                int counter = 0;
                if((in.rot & 8) == 8) {
                    if(counter == randRot) {
                        this.setRotation(x,y, 8);
                    }
                    counter++;
                }
                if((in.rot & 4) == 4) {
                    if(counter == randRot) {
                        this.setRotation(x,y, 4);
                    }
                    counter++;
                }
                if((in.rot & 2) == 2) {
                    if(counter == randRot) {
                        this.setRotation(x,y, 2);
                    }
                    counter++;
                }
                if((in.rot & 1) == 1) {
                    if(counter == randRot) {
                        this.setRotation(x,y, 1);
                    }
                    counter++;
                }

                this.lastModifiedX = x;
                this.lastModifiedY = y;
                break;
            }
        }
        else {
            System.out.println("ERROR: 0 tiles possible");
        }

    }

    private int bitsOn(int val) {
        int count = 0;
        while (val > 0) {
            count += val & 1;
            val >>= 1;
        }
        return count;
    }

    private Queue<CoordFromTo> coords = new LinkedList<CoordFromTo>();

    public void propagate() {

        // Take the last modified position
        int x = this.lastModifiedX;
        int y = this.lastModifiedY;

        // Add all possible tiles leading out from the last modified position
        // 
        // north direction
        if(y == height-1) {
            // at top, only add if wraparound is specified
            if(this.tilingVertical) {
                CoordFromTo fromTo = new CoordFromTo(x,y, x, 0, DIR.N);
                coords.add(fromTo);
            }

        }
        else {
            CoordFromTo fromTo = new CoordFromTo(x,y, x, y+1, DIR.N);
            coords.add(fromTo);
        }

        // south direction
        if(y == 0) {
            // at bottom, only add if wraparound is specified
            if(this.tilingVertical) {
                CoordFromTo fromTo = new CoordFromTo(x,y, x, height-1, DIR.S);
                coords.add(fromTo);
            }

        }
        else {
            CoordFromTo fromTo = new CoordFromTo(x,y, x, y-1, DIR.S);
            coords.add(fromTo);
        }
        // west direction
        if(x == 0) {
            // at left side, only add if wraparound is specified
            if(this.tilingHorizontal) {
                CoordFromTo fromTo = new CoordFromTo(x,y, width-1,y, DIR.W);
                coords.add(fromTo);
            }

        }
        else {
            CoordFromTo fromTo = new CoordFromTo(x,y, x-1, y, DIR.W);
            coords.add(fromTo);
        }

        // east
        if(x == width-1) {
            // at right side, only add if wraparound is specified
            if(this.tilingHorizontal) {
                CoordFromTo fromTo = new CoordFromTo(x,y, 0,y, DIR.E);
                coords.add(fromTo);
            }

        }
        else {
            CoordFromTo fromTo = new CoordFromTo(x,y, x+1, y, DIR.E);
            coords.add(fromTo);
        }

        // Propagate the changes
        while(!coords.isEmpty()) {

            CoordFromTo co = coords.poll();
            
            boolean changed = checkSinglePropTile(co);

            if(changed) {
                // add all tiles leading out from the changed cell, except back toward source
                int toX = co.to.x;
                int toY = co.to.y;

                // north
                // do not go back to the same tile
                if(co.dir != DIR.S) {
                    if(toY == height-1) {
                        // at top, only add if wraparound is specified
                        if(this.tilingVertical) {
                            CoordFromTo fromTo = new CoordFromTo(toX, toY, toX, 0, DIR.N);
                            coords.add(fromTo);
                        }

                    }
                    else {
                        CoordFromTo fromTo = new CoordFromTo(toX, toY, toX, toY+1, DIR.N);
                        coords.add(fromTo);
                    }
                }

                // south direction
                // do not go back to the same tile
                if(co.dir != DIR.N) {
                    if(toY == 0) {
                        // at bottom, only add if wraparound is specified
                        if(this.tilingVertical) {
                            CoordFromTo fromTo = new CoordFromTo(toX, toY, toX, height-1, DIR.S);
                            coords.add(fromTo);
                        }

                    }
                    else {
                        CoordFromTo fromTo = new CoordFromTo(toX, toY, toX, toY-1, DIR.S);
                        coords.add(fromTo);
                    }
                }

                // west direction
                // do not go back to the same tile
                if(co.dir != DIR.E) {
                    if(toX == 0) {
                        // at left side, only add if wraparound is specified
                        if(this.tilingHorizontal) {
                            CoordFromTo fromTo = new CoordFromTo(toX, toY, width-1, toY, DIR.W);
                            coords.add(fromTo);
                        }

                    }
                    else {
                        CoordFromTo fromTo = new CoordFromTo(toX, toY, toX-1, toY, DIR.W);
                        coords.add(fromTo);
                    }
                }

                // east
                // do not go back to the same tile
                if(co.dir != DIR.W) {
                    if(toX == (width-1)) {
                        // at right side, only add if wraparound is specified
                        if(this.tilingHorizontal) {
                            CoordFromTo fromTo = new CoordFromTo(toX, toY, 0, toY, DIR.E);
                            coords.add(fromTo);
                        }

                    }
                    else {
                        CoordFromTo fromTo = new CoordFromTo(toX, toY, toX+1, toY, DIR.E);
                        coords.add(fromTo);
                    }
                }
            }
        }

        coords.clear();

        finished = true;
    }

    private Set<Integer> allowedPorts(int [] checktilesFrom, int [] checkrotsFrom, DIR dir)  {
        // int [] ports = new int[checktilesFrom.length];
        Set<Integer> ports = new HashSet<Integer>();

        for(int i = 0; i < checktilesFrom.length; i++) {

            int tile = checktilesFrom[i];
            int rot = checkrotsFrom[i];

            if((rot & 1) == 1) { 
                int port = constraints.getPort(tile, dir);
                ports.add(port);
            }
            if((rot & 2) == 2) { 
                int port = constraints.getPort(tile, dir.rotateCCW(1));
                ports.add(port);
            }
            if((rot & 4) == 4) { 
                int port = constraints.getPort(tile, dir.rotateCCW(2));
                ports.add(port);
            }
            if((rot & 8) == 8) { 
                int port = constraints.getPort(tile, dir.rotateCCW(3));
                ports.add(port);
            }

        }
        
        return ports;
    }

    // narrows tiles, and 
    // returns true if something changed, add all other dirs except from
    public boolean checkSinglePropTile(CoordFromTo coordFromTo) {

        boolean changed = false;

        int [] checktilesTo = idGrid[coordFromTo.to.x][coordFromTo.to.y];
        int [] checkrotsTo = rotationGrid[coordFromTo.to.x][coordFromTo.to.y];

        int [] checktilesFrom = idGrid[coordFromTo.from.x][coordFromTo.from.y];
        int [] checkrotsFrom = rotationGrid[coordFromTo.from.x][coordFromTo.from.y];

        DIR dir = coordFromTo.dir;
        DIR opposite = dir.opposite();

        int [] newtiles = new int[checktilesTo.length];
        int [] newrots = new int[checktilesTo.length];
        int newlen = 0;
        int newrot = 0;

        Set<Integer> allowedPortsFrom = allowedPorts(checktilesFrom, checkrotsFrom, dir);

        for(int i = 0; i < checktilesTo.length; i++) {
            int tileTo = checktilesTo[i];
            int rotTo = checkrotsTo[i];

            // remove rots, check at end if rots is 0
            newrot = rotTo;

            if((rotTo & 1)== 1) {

                int port = constraints.getPort(tileTo, opposite);

                if(!allowedPortsFrom.contains(port)) {
                    newrot = newrot & ~1;
                    changed = true;
                }
            }

            if((rotTo & 2)== 2) {

                int port = constraints.getPort(tileTo, opposite.rotateCCW(1));

                if(!allowedPortsFrom.contains(port)) {
                    newrot = newrot & ~2;
                    changed = true;
                }
            }

            if((rotTo & 4) == 4) {

                int port = constraints.getPort(tileTo, opposite.rotateCCW(2));

                if(!allowedPortsFrom.contains(port)) {
                    newrot = newrot & ~4;
                    changed = true;
                }
            }

            if((rotTo & 8) == 8) {

                int port = constraints.getPort(tileTo, opposite.rotateCCW(3));

                if(!allowedPortsFrom.contains(port)) {
                    newrot = newrot & ~8;
                    changed = true;
                }
            }

            if(newrot > 0)  {
                newtiles[newlen] = tileTo;
                newrots[newlen] = newrot;
                newlen++;
            }
        }

        changed = newlen != checktilesTo.length;

        int [] tempres = new int[newlen];
        System.arraycopy(newtiles, 0, tempres, 0, newlen);
        idGrid[coordFromTo.to.x][coordFromTo.to.y] = tempres;

        int [] temprots = new int[newlen];
        System.arraycopy(newrots, 0, temprots, 0, newlen);
        rotationGrid[coordFromTo.to.x][coordFromTo.to.y] = temprots;
        
        return changed;
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

        if(!finished) {
            propagate();
        }

        finished = this.isFinished();
    }

    // runs the observe -> propagate cycle until finished
    public void run() {

        this.finished = false;

        while(!finished) {
            observe();

            if(!finished) {
                propagate();
            }

            finished = this.isFinished();
        }

    }

    private boolean finished = false;

}
