import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import java.util.Arrays;

public class UserInterface {
    private JPanel mainPanel;
    private JCheckBox putCheckBox;
    private JCheckBox deleteCheckBox;
    private JCheckBox headCheckBox;
    private JCheckBox optionsCheckBox;
    private JCheckBox connectCheckBox;
    private JCheckBox traceCheckBox;
    private JCheckBox patchCheckBox;
    private JCheckBox allCheckBox;
    private JButton clearButton;
    private JTree logTree;
    private DefaultMutableTreeNode rootNode;
    private DefaultTreeModel treeModel;
    private MontoyaApi api;

    private final Map<String, DefaultMutableTreeNode> domainNodes = new HashMap<>();
    private final Map<String, DefaultMutableTreeNode> segmentNodes = new HashMap<>();
    private final List<DefaultMutableTreeNode> highlightedNodes = new ArrayList<>();  // Track highlighted nodes

    public UserInterface(MontoyaApi api) {
        this.api = api;
        mainPanel = new JPanel(new BorderLayout());
        putCheckBox = new JCheckBox("PUT");
        deleteCheckBox = new JCheckBox("DELETE");
        headCheckBox = new JCheckBox("HEAD");
        optionsCheckBox = new JCheckBox("OPTIONS");
        connectCheckBox = new JCheckBox("CONNECT");
        traceCheckBox = new JCheckBox("TRACE");
        patchCheckBox = new JCheckBox("PATCH");
        allCheckBox = new JCheckBox("All");
        clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clearLog());

        JPanel checkBoxPanel = new JPanel();
        checkBoxPanel.add(new JLabel("Select HTTP Verbs for Replay:"));
        checkBoxPanel.add(putCheckBox);
        checkBoxPanel.add(deleteCheckBox);
        checkBoxPanel.add(headCheckBox);
        checkBoxPanel.add(optionsCheckBox);
        checkBoxPanel.add(connectCheckBox);
        checkBoxPanel.add(traceCheckBox);
        checkBoxPanel.add(patchCheckBox);
        checkBoxPanel.add(allCheckBox);
        checkBoxPanel.add(clearButton);

        rootNode = new DefaultMutableTreeNode("Replayed Requests");
        treeModel = new DefaultTreeModel(rootNode);
        logTree = new JTree(treeModel);
        logTree.setRootVisible(true);

        // Custom cell renderer for highlighting nodes
        logTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                Component c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                if (highlightedNodes.contains(node)) {
                    c.setForeground(Color.RED);  // Highlighted nodes in red
                } else {
                    c.setForeground(Color.BLACK);
                }
                return c;
            }
        });

        mainPanel.add(checkBoxPanel, BorderLayout.NORTH);
        mainPanel.add(new JScrollPane(logTree), BorderLayout.CENTER);

        // Add context menu with "Send to Repeater" and "Highlight/Unhighlight" options
        initializeContextMenu();
    }

    public List<String> getSelectedVerbs() {
        List<String> selectedVerbs = new ArrayList<>();
        if (allCheckBox.isSelected()) {
            selectedVerbs = Arrays.asList("PUT", "DELETE", "HEAD", "OPTIONS", "CONNECT", "TRACE", "PATCH");
        } else {
            if (putCheckBox.isSelected()) selectedVerbs.add("PUT");
            if (deleteCheckBox.isSelected()) selectedVerbs.add("DELETE");
            if (headCheckBox.isSelected()) selectedVerbs.add("HEAD");
            if (optionsCheckBox.isSelected()) selectedVerbs.add("OPTIONS");
            if (connectCheckBox.isSelected()) selectedVerbs.add("CONNECT");
            if (traceCheckBox.isSelected()) selectedVerbs.add("TRACE");
            if (patchCheckBox.isSelected()) selectedVerbs.add("PATCH");
        }
        return selectedVerbs;
    }

    public void logTraffic(String verb, String url, int statusCode, HttpRequest httpRequest, String response) {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String domain = url.split("/")[0];
        String path = url.substring(domain.length());
        String firstSegment = path.contains("/") ? path.split("/")[1] : path;

        DefaultMutableTreeNode domainNode = domainNodes.computeIfAbsent(domain, d -> {
            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(domain);
            rootNode.add(newNode);
            return newNode;
        });

        String segmentKey = domain + "/" + firstSegment;
        DefaultMutableTreeNode segmentNode = segmentNodes.computeIfAbsent(segmentKey, s -> {
            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(firstSegment);
            domainNode.add(newNode);
            return newNode;
        });

        DefaultMutableTreeNode requestNode = new DefaultMutableTreeNode(httpRequest);
        segmentNode.add(requestNode);

        DefaultMutableTreeNode requestDetailsNode = new DefaultMutableTreeNode("Request:");
        DefaultMutableTreeNode responseDetailsNode = new DefaultMutableTreeNode("Response:");

        for (String line : httpRequest.toString().split("\\n")) {
            requestDetailsNode.add(new DefaultMutableTreeNode(line));
        }
        for (String line : response.split("\\n")) {
            responseDetailsNode.add(new DefaultMutableTreeNode(line));
        }

        requestNode.add(requestDetailsNode);
        requestNode.add(responseDetailsNode);

        List<TreePath> expandedPaths = getExpandedPaths(logTree);
        TreePath selectedPath = logTree.getSelectionPath();

        SwingUtilities.invokeLater(() -> {
            treeModel.reload();
            expandPaths(logTree, expandedPaths);
            if (selectedPath != null) {
                logTree.setSelectionPath(selectedPath);
            }
        });
    }

    private void initializeContextMenu() {
        JPopupMenu contextMenu = new JPopupMenu();
        JMenuItem sendToRepeaterItem = new JMenuItem("Send to Repeater");
        sendToRepeaterItem.addActionListener(e -> sendSelectedRequestToRepeater());

        JMenuItem highlightMenuItem = new JMenuItem("Highlight/Unhighlight");
        highlightMenuItem.addActionListener(e -> toggleHighlight());

        contextMenu.add(sendToRepeaterItem);
        contextMenu.add(highlightMenuItem);

        logTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            private void showContextMenu(MouseEvent e) {
                int row = logTree.getClosestRowForLocation(e.getX(), e.getY());
                logTree.setSelectionRow(row);
                contextMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }

    private void sendSelectedRequestToRepeater() {
        TreePath selectedPath = logTree.getSelectionPath();
        if (selectedPath != null) {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
            if (selectedNode.getUserObject() instanceof HttpRequest) {
                HttpRequest httpRequest = (HttpRequest) selectedNode.getUserObject();
                VerbReplayer.sendToRepeater(httpRequest);
            } else {
                JOptionPane.showMessageDialog(mainPanel, "Selected item is not a valid HTTP request.");
            }
        }
    }

    private void toggleHighlight() {
        TreePath selectedPath = logTree.getSelectionPath();
        if (selectedPath != null) {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
            if (highlightedNodes.contains(selectedNode)) {
                highlightedNodes.remove(selectedNode);
            } else {
                highlightedNodes.add(selectedNode);
            }

            List<TreePath> expandedPaths = getExpandedPaths(logTree);
            TreePath currentSelection = logTree.getSelectionPath();

            SwingUtilities.invokeLater(() -> {
                treeModel.reload();
                expandPaths(logTree, expandedPaths);
                if (currentSelection != null) {
                    logTree.setSelectionPath(currentSelection);
                }
            });
        }
    }

    // Preserves highlighted nodes when clearing the log
    public void clearLog() {
        clearNonHighlightedNodes(rootNode, false);
        SwingUtilities.invokeLater(() -> treeModel.reload());
    }

    // Recursive method to clear only non-highlighted nodes
    private void clearNonHighlightedNodes(DefaultMutableTreeNode node, boolean hasHighlightedAncestor) {
        boolean currentNodeHighlighted = highlightedNodes.contains(node);
        boolean shouldPreserve = hasHighlightedAncestor || currentNodeHighlighted;

        for (int i = node.getChildCount() - 1; i >= 0; i--) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) node.getChildAt(i);
            clearNonHighlightedNodes(childNode, shouldPreserve);

            if (!highlightedNodes.contains(childNode) && childNode.getChildCount() == 0 && !shouldPreserve) {
                node.remove(childNode);
                domainNodes.values().remove(childNode);
                segmentNodes.values().remove(childNode);
            }
        }
    }

    private List<TreePath> getExpandedPaths(JTree tree) {
        List<TreePath> expandedPaths = new ArrayList<>();
        for (int i = 0; i < tree.getRowCount(); i++) {
            TreePath path = tree.getPathForRow(i);
            if (tree.isExpanded(path)) {
                expandedPaths.add(path);
            }
        }
        return expandedPaths;
    }

    private void expandPaths(JTree tree, List<TreePath> paths) {
        for (TreePath path : paths) {
            tree.expandPath(path);
        }
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }
}
