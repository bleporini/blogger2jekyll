package io.blep;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

class DomUtils {

    public static List<Node> asList(NodeList nl) {
        final ArrayList<Node> nodeList = new ArrayList<>();
        for (int i = 0; i < nl.getLength(); i++) {
            nodeList.add(nl.item(i));
        }
        return nodeList;
    }
}
