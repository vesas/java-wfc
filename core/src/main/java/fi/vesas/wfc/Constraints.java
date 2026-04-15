package fi.vesas.wfc;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Constraints {
    private Map<SimpleWFC.StateDir, Integer> ports = new HashMap<>();

    public void addPort(int source, SimpleWFC.DIR dir, int portId) {
        SimpleWFC.StateDir stateDir = new SimpleWFC.StateDir();
        stateDir.state = source;
        stateDir.dir = dir;
        ports.put(stateDir, portId);
    }

    public int getPort(int source, SimpleWFC.DIR dir, int targetRots) {
        SimpleWFC.StateDir stateDir = new SimpleWFC.StateDir();
        stateDir.state = source;
        stateDir.dir = dir;

        Integer port = ports.get(stateDir);
        return port != null ? port : -1;
    }

    public int getPort(int source, SimpleWFC.DIR dir) {
        SimpleWFC.StateDir stateDir = new SimpleWFC.StateDir();
        stateDir.state = source;
        stateDir.dir = dir;

        Integer port = ports.get(stateDir);
        return port != null ? port : -1;
    }

    public void printConstraints() {
        Set<Entry<SimpleWFC.StateDir, Integer>> temp = this.ports.entrySet();
        System.out.println("constraints: ");
        for (Entry<SimpleWFC.StateDir, Integer> entry : temp) {
            System.out.println("" + entry.getKey() + " -> " + entry.getValue());
        }
    }
} 