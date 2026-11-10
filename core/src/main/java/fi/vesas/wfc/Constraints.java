package fi.vesas.wfc;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Constraints {
    private Map<SimpleWFC.StateDir, Integer> ports = new HashMap<>();

    // Flat lookup table built from the map: index = tile * 4 + dir.ordinal(), -1 = no port.
    // Rebuilt lazily after addPort, so getPort avoids allocation and boxing in the solver's hot loop.
    private int[] portTable;
    private int minPort, maxPort;

    public void addPort(int source, SimpleWFC.DIR dir, int portId) {
        SimpleWFC.StateDir stateDir = new SimpleWFC.StateDir();
        stateDir.state = source;
        stateDir.dir = dir;
        ports.put(stateDir, portId);
        portTable = null;
    }

    private void buildTable() {
        int maxTile = -1;
        int lo = -1, hi = -1;
        for (Entry<SimpleWFC.StateDir, Integer> e : ports.entrySet()) {
            maxTile = Math.max(maxTile, e.getKey().state);
            lo = Math.min(lo, e.getValue());
            hi = Math.max(hi, e.getValue());
        }
        int[] table = new int[(maxTile + 1) * 4];
        java.util.Arrays.fill(table, -1);
        for (Entry<SimpleWFC.StateDir, Integer> e : ports.entrySet()) {
            if (e.getKey().state >= 0) table[e.getKey().state * 4 + e.getKey().dir.ordinal()] = e.getValue();
        }
        minPort = lo;
        maxPort = hi;
        portTable = table;
    }

    /** Smallest port id in use, or -1 (the "no port" value) if smaller. */
    public int getMinPort() {
        if (portTable == null) buildTable();
        return minPort;
    }

    /** Largest port id in use, or -1 if there are no ports. */
    public int getMaxPort() {
        if (portTable == null) buildTable();
        return maxPort;
    }

    public int getPort(int source, SimpleWFC.DIR dir, int targetRots) {
        SimpleWFC.StateDir stateDir = new SimpleWFC.StateDir();
        stateDir.state = source;
        stateDir.dir = dir;

        Integer port = ports.get(stateDir);
        return port != null ? port : -1;
    }

    public int getPort(int source, SimpleWFC.DIR dir) {
        return getPort(source, dir.ordinal());
    }

    /** Port of {@code source} facing direction ordinal {@code dir} (0..3), or -1. */
    public int getPort(int source, int dir) {
        int[] table = portTable;
        if (table == null) { buildTable(); table = portTable; }
        int idx = source * 4 + dir;
        return (source >= 0 && idx < table.length) ? table[idx] : -1;
    }

    public void printConstraints() {
        Set<Entry<SimpleWFC.StateDir, Integer>> temp = this.ports.entrySet();
        System.out.println("constraints: ");
        for (Entry<SimpleWFC.StateDir, Integer> entry : temp) {
            System.out.println("" + entry.getKey() + " -> " + entry.getValue());
        }
    }
} 