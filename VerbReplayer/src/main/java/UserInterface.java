import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;  // using java.util.List explicitly
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;

/**
 * This class implements the UI for the VerbReplayer extension.
 * It persists logged entries (both Results and History) to files in the same directory as the current project,
 * clears only the Results (successful requests) when requested, and preserves highlighting.
 */
public class UserInterface {

    private final MontoyaApi api;
    private final JPanel mainPanel;
    private final JTabbedPane tabbedPane;

    // --- Persistence file names (saved in the project directory) ---
    private static final String SUCCESS_FILE = "VerbReplayer_success.dat";
    private static final String ERROR_FILE   = "VerbReplayer_error.dat";

    // --- Master lists (persisted) ---
    private final List<ReplayedRequestEntry> successEntries = new ArrayList<>();
    private final List<ReplayedRequestEntry> errorEntries = new ArrayList<>();

    // ---------------------------
    // Nested class for logged entries.
    // (We store the HTTP request as a String because the HttpRequest object is not serializable.)
    // ---------------------------
    public static class ReplayedRequestEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        public String verb;
        public String url;
        public int statusCode;
        public String requestText;  // textual representation of the HTTP request
        public String response;
        public String timestamp;
        public boolean highlighted; // flag for highlighting

        public ReplayedRequestEntry(String verb, String url, int statusCode,
                                    String requestText, String response, String timestamp) {
            this.verb = verb;
            this.url = url;
            this.statusCode = statusCode;
            this.requestText = requestText;
            this.response = response;
            this.timestamp = timestamp;
            this.highlighted = false;
        }
        @Override
        public String toString() {
            return String.format("[%s] %s %s", timestamp, verb, url);
        }
    }

    // ---------------------------
    // TAB 1: Friendly Layout for Successful Requests ("Results")
    // ---------------------------
    private final JPanel resultsPanelFriendly;
    private final DefaultMutableTreeNode resultsFriendlyRoot; // built from successEntries
    private final DefaultTreeModel resultsFriendlyTreeModel;
    private final JTree resultsFriendlyTree;
    private final JTextArea resultsFriendlyRequestTextArea;
    private final JTextArea resultsFriendlyResponseTextArea;
    private final JButton resultsFriendlySendButton;
    private final JPanel resultsFriendlyFilterPanel;
    // These checkboxes control which HTTP verbs are allowed for replay.
    private final JCheckBox resultsFriendlyAllFilter;
    private final JCheckBox resultsFriendlyPutFilter;
    private final JCheckBox resultsFriendlyDeleteFilter;
    private final JCheckBox resultsFriendlyHeadFilter;
    private final JCheckBox resultsFriendlyOptionsFilter;
    private final JCheckBox resultsFriendlyConnectFilter;
    private final JCheckBox resultsFriendlyTraceFilter;
    private final JCheckBox resultsFriendlyPatchFilter;
    private final JButton clearButton; // clears non-highlighted entries from successEntries only

    // ---------------------------
    // TAB 2: Grouped Log for Non‑successful Requests ("History")
    // ---------------------------
    private final JPanel historyPanel;
    private final DefaultMutableTreeNode historyRoot; // built from errorEntries; root label "Log of all requests"
    private final DefaultTreeModel historyTreeModel;
    private final JTree historyTree;
    private final JTextArea historyRequestTextArea;
    private final JTextArea historyResponseTextArea;
    private final JButton historySendButton;
    private final JPanel historyFilterPanel;
    // These checkboxes control which HTTP verbs are allowed for replay.
    private final JCheckBox historyAllFilter;
    private final JCheckBox historyPutFilter;
    private final JCheckBox historyDeleteFilter;
    private final JCheckBox historyHeadFilter;
    private final JCheckBox historyOptionsFilter;
    private final JCheckBox historyConnectFilter;
    private final JCheckBox historyTraceFilter;
    private final JCheckBox historyPatchFilter;

    // ---------------------------
    // A constant list of all possible verbs.
    // ---------------------------
    private static final List<String> ALL_VERBS = Arrays.asList("PUT", "DELETE", "HEAD", "OPTIONS", "CONNECT", "TRACE", "PATCH");

    public UserInterface(MontoyaApi api) {
        this.api = api;
        mainPanel = new JPanel(new BorderLayout());
        tabbedPane = new JTabbedPane();

        // Load persisted data from the project directory.
        loadData();

        // ============================================================
        // Build TAB 1 – Friendly Layout for Successful Requests ("Results")
        // ============================================================
        resultsPanelFriendly = new JPanel(new BorderLayout());
        resultsFriendlyFilterPanel = new JPanel();
        resultsFriendlyFilterPanel.add(new JLabel("Allowed HTTP Verbs:"));
        resultsFriendlyAllFilter = new JCheckBox("All", true);
        resultsFriendlyPutFilter = new JCheckBox("PUT", true);
        resultsFriendlyDeleteFilter = new JCheckBox("DELETE", true);
        resultsFriendlyHeadFilter = new JCheckBox("HEAD", true);
        resultsFriendlyOptionsFilter = new JCheckBox("OPTIONS", true);
        resultsFriendlyConnectFilter = new JCheckBox("CONNECT", true);
        resultsFriendlyTraceFilter = new JCheckBox("TRACE", true);
        resultsFriendlyPatchFilter = new JCheckBox("PATCH", true);
        resultsFriendlyFilterPanel.add(resultsFriendlyAllFilter);
        resultsFriendlyFilterPanel.add(resultsFriendlyPutFilter);
        resultsFriendlyFilterPanel.add(resultsFriendlyDeleteFilter);
        resultsFriendlyFilterPanel.add(resultsFriendlyHeadFilter);
        resultsFriendlyFilterPanel.add(resultsFriendlyOptionsFilter);
        resultsFriendlyFilterPanel.add(resultsFriendlyConnectFilter);
        resultsFriendlyFilterPanel.add(resultsFriendlyTraceFilter);
        resultsFriendlyFilterPanel.add(resultsFriendlyPatchFilter);
        // Synchronize "All" checkbox.
        resultsFriendlyAllFilter.addItemListener(e -> {
            boolean sel = resultsFriendlyAllFilter.isSelected();
            resultsFriendlyPutFilter.setSelected(sel);
            resultsFriendlyDeleteFilter.setSelected(sel);
            resultsFriendlyHeadFilter.setSelected(sel);
            resultsFriendlyOptionsFilter.setSelected(sel);
            resultsFriendlyConnectFilter.setSelected(sel);
            resultsFriendlyTraceFilter.setSelected(sel);
            resultsFriendlyPatchFilter.setSelected(sel);
        });
        ItemListener syncFriendlyListener = e -> {
            if (!resultsFriendlyPutFilter.isSelected() ||
                    !resultsFriendlyDeleteFilter.isSelected() ||
                    !resultsFriendlyHeadFilter.isSelected() ||
                    !resultsFriendlyOptionsFilter.isSelected() ||
                    !resultsFriendlyConnectFilter.isSelected() ||
                    !resultsFriendlyTraceFilter.isSelected() ||
                    !resultsFriendlyPatchFilter.isSelected()) {
                resultsFriendlyAllFilter.setSelected(false);
            } else {
                resultsFriendlyAllFilter.setSelected(true);
            }
        };
        resultsFriendlyPutFilter.addItemListener(syncFriendlyListener);
        resultsFriendlyDeleteFilter.addItemListener(syncFriendlyListener);
        resultsFriendlyHeadFilter.addItemListener(syncFriendlyListener);
        resultsFriendlyOptionsFilter.addItemListener(syncFriendlyListener);
        resultsFriendlyConnectFilter.addItemListener(syncFriendlyListener);
        resultsFriendlyTraceFilter.addItemListener(syncFriendlyListener);
        resultsFriendlyPatchFilter.addItemListener(syncFriendlyListener);

        // Add the Clear button to the Results tab – it clears only the successful entries.
        clearButton = new JButton("Clear Non-Highlighted");
        clearButton.addActionListener(e -> {
            clearNonHighlightedEntries();
            saveData();
            updateFriendlyTree();
        });
        resultsFriendlyFilterPanel.add(clearButton);

        resultsPanelFriendly.add(resultsFriendlyFilterPanel, BorderLayout.NORTH);

        // Build the friendly tree from successEntries.
        resultsFriendlyRoot = new DefaultMutableTreeNode("Successful Requests");
        resultsFriendlyTreeModel = new DefaultTreeModel(resultsFriendlyRoot);
        resultsFriendlyTree = new JTree(resultsFriendlyTreeModel);
        resultsFriendlyTree.setRootVisible(true);
        resultsFriendlyTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                          boolean sel, boolean expanded,
                                                          boolean leaf, int row, boolean hasFocus) {
                Component c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                if (node.isLeaf() && node.getUserObject() instanceof ReplayedRequestEntry) {
                    ReplayedRequestEntry entry = (ReplayedRequestEntry) node.getUserObject();
                    c.setForeground(entry.highlighted ? Color.RED : Color.BLACK);
                } else {
                    c.setForeground(nodeHasHighlightedDescendant(node) ? Color.RED : Color.BLACK);
                }
                return c;
            }
        });
        addFriendlyTreeContextMenu();
        JScrollPane resultsFriendlyTreeScroll = new JScrollPane(resultsFriendlyTree);

        // Build details panel for the friendly tab.
        JPanel friendlyDetailsPanel = new JPanel(new BorderLayout());
        JPanel friendlyRequestPanel = new JPanel(new BorderLayout());
        friendlyRequestPanel.add(new JLabel("Request:"), BorderLayout.NORTH);
        resultsFriendlyRequestTextArea = new JTextArea();
        resultsFriendlyRequestTextArea.setEditable(false);
        resultsFriendlyRequestTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        friendlyRequestPanel.add(new JScrollPane(resultsFriendlyRequestTextArea), BorderLayout.CENTER);
        JPanel friendlyResponsePanel = new JPanel(new BorderLayout());
        friendlyResponsePanel.add(new JLabel("Response:"), BorderLayout.NORTH);
        resultsFriendlyResponseTextArea = new JTextArea();
        resultsFriendlyResponseTextArea.setEditable(false);
        resultsFriendlyResponseTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        friendlyResponsePanel.add(new JScrollPane(resultsFriendlyResponseTextArea), BorderLayout.CENTER);
        JSplitPane friendlyDetailSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, friendlyRequestPanel, friendlyResponsePanel);
        friendlyDetailSplit.setResizeWeight(0.5);

        resultsFriendlySendButton = new JButton("Send Request to Repeater");
        resultsFriendlySendButton.addActionListener(e -> {
            ReplayedRequestEntry entry = getSelectedEntry(resultsFriendlyTree);
            if (entry != null && entry.requestText != null) {
                HttpRequest req = parseHttpRequest(entry.requestText);
                if (req != null) {
                    VerbReplayer.sendToRepeater(req);
                } else {
                    JOptionPane.showMessageDialog(mainPanel, "Error re-creating HTTP request.");
                }
            } else {
                JOptionPane.showMessageDialog(mainPanel, "Selected item is not a valid HTTP request.");
            }
        });

        JPanel friendlyRightPanel = new JPanel(new BorderLayout());
        friendlyRightPanel.add(friendlyDetailSplit, BorderLayout.CENTER);
        friendlyRightPanel.add(resultsFriendlySendButton, BorderLayout.SOUTH);

        JSplitPane friendlySplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, resultsFriendlyTreeScroll, friendlyRightPanel);
        friendlySplitPane.setResizeWeight(0.3);
        resultsPanelFriendly.add(friendlySplitPane, BorderLayout.CENTER);

        resultsFriendlyTree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                ReplayedRequestEntry entry = getSelectedEntry(resultsFriendlyTree);
                if (entry != null) {
                    resultsFriendlyRequestTextArea.setText(entry.requestText);
                    resultsFriendlyResponseTextArea.setText(entry.response);
                }
            }
        });

        // ============================================================
        // Build TAB 2 – Grouped Log for Non‑successful Requests ("History")
        // ============================================================
        historyPanel = new JPanel(new BorderLayout());
        historyFilterPanel = new JPanel();
        historyFilterPanel.add(new JLabel("Allowed HTTP Verbs:"));
        historyAllFilter = new JCheckBox("All", true);
        historyPutFilter = new JCheckBox("PUT", true);
        historyDeleteFilter = new JCheckBox("DELETE", true);
        historyHeadFilter = new JCheckBox("HEAD", true);
        historyOptionsFilter = new JCheckBox("OPTIONS", true);
        historyConnectFilter = new JCheckBox("CONNECT", true);
        historyTraceFilter = new JCheckBox("TRACE", true);
        historyPatchFilter = new JCheckBox("PATCH", true);
        historyFilterPanel.add(historyAllFilter);
        historyFilterPanel.add(historyPutFilter);
        historyFilterPanel.add(historyDeleteFilter);
        historyFilterPanel.add(historyHeadFilter);
        historyFilterPanel.add(historyOptionsFilter);
        historyFilterPanel.add(historyConnectFilter);
        historyFilterPanel.add(historyTraceFilter);
        historyFilterPanel.add(historyPatchFilter);
        // Synchronize "All" checkbox for History tab.
        historyAllFilter.addItemListener(e -> {
            boolean sel = historyAllFilter.isSelected();
            historyPutFilter.setSelected(sel);
            historyDeleteFilter.setSelected(sel);
            historyHeadFilter.setSelected(sel);
            historyOptionsFilter.setSelected(sel);
            historyConnectFilter.setSelected(sel);
            historyTraceFilter.setSelected(sel);
            historyPatchFilter.setSelected(sel);
        });
        ItemListener syncHistoryListener = e -> {
            if (!historyPutFilter.isSelected() ||
                    !historyDeleteFilter.isSelected() ||
                    !historyHeadFilter.isSelected() ||
                    !historyOptionsFilter.isSelected() ||
                    !historyConnectFilter.isSelected() ||
                    !historyTraceFilter.isSelected() ||
                    !historyPatchFilter.isSelected()) {
                historyAllFilter.setSelected(false);
            } else {
                historyAllFilter.setSelected(true);
            }
        };
        historyPutFilter.addItemListener(syncHistoryListener);
        historyDeleteFilter.addItemListener(syncHistoryListener);
        historyHeadFilter.addItemListener(syncHistoryListener);
        historyOptionsFilter.addItemListener(syncHistoryListener);
        historyConnectFilter.addItemListener(syncHistoryListener);
        historyTraceFilter.addItemListener(syncHistoryListener);
        historyPatchFilter.addItemListener(syncHistoryListener);

        historyPanel.add(historyFilterPanel, BorderLayout.NORTH);

        // Build the grouped tree from all errorEntries.
        historyRoot = new DefaultMutableTreeNode("Log of all requests");
        historyTreeModel = new DefaultTreeModel(historyRoot);
        historyTree = new JTree(historyTreeModel);
        historyTree.setRootVisible(true);
        historyTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                          boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                Component c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                if (node.isLeaf() && node.getUserObject() instanceof ReplayedRequestEntry) {
                    ReplayedRequestEntry entry = (ReplayedRequestEntry) node.getUserObject();
                    c.setForeground(entry.highlighted ? Color.RED : Color.BLACK);
                } else {
                    c.setForeground(nodeHasHighlightedDescendant(node) ? Color.RED : Color.BLACK);
                }
                return c;
            }
        });
        addHistoryTreeContextMenu();
        JScrollPane historyTreeScroll = new JScrollPane(historyTree);

        // Build details panel for History tab.
        JPanel historyDetailsPanel = new JPanel(new BorderLayout());
        JPanel historyRequestPanel = new JPanel(new BorderLayout());
        historyRequestPanel.add(new JLabel("Request:"), BorderLayout.NORTH);
        historyRequestTextArea = new JTextArea();
        historyRequestTextArea.setEditable(false);
        historyRequestTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        historyRequestPanel.add(new JScrollPane(historyRequestTextArea), BorderLayout.CENTER);
        JPanel historyResponsePanel = new JPanel(new BorderLayout());
        historyResponsePanel.add(new JLabel("Response:"), BorderLayout.NORTH);
        historyResponseTextArea = new JTextArea();
        historyResponseTextArea.setEditable(false);
        historyResponseTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        historyResponsePanel.add(new JScrollPane(historyResponseTextArea), BorderLayout.CENTER);
        JSplitPane historyDetailSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, historyRequestPanel, historyResponsePanel);
        historyDetailSplit.setResizeWeight(0.5);

        historySendButton = new JButton("Send Request to Repeater");
        historySendButton.addActionListener(e -> {
            ReplayedRequestEntry entry = getSelectedEntry(historyTree);
            if (entry != null && entry.requestText != null) {
                HttpRequest req = parseHttpRequest(entry.requestText);
                if (req != null) {
                    VerbReplayer.sendToRepeater(req);
                } else {
                    JOptionPane.showMessageDialog(mainPanel, "Error re-creating HTTP request.");
                }
            } else {
                JOptionPane.showMessageDialog(mainPanel, "Selected item is not a valid HTTP request.");
            }
        });

        JPanel historyRightPanel = new JPanel(new BorderLayout());
        historyRightPanel.add(historyDetailSplit, BorderLayout.CENTER);
        historyRightPanel.add(historySendButton, BorderLayout.SOUTH);

        JSplitPane historySplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, historyTreeScroll, historyRightPanel);
        historySplitPane.setResizeWeight(0.3);
        historyPanel.add(historySplitPane, BorderLayout.CENTER);

        historyTree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                ReplayedRequestEntry entry = getSelectedEntry(historyTree);
                if (entry != null) {
                    historyRequestTextArea.setText(entry.requestText);
                    historyResponseTextArea.setText(entry.response);
                }
            }
        });

        // ============================================================
        // Add the two tabs to the tabbed pane.
        // "Results" (friendly view) is the first tab; "History" (grouped log) is the second.
        // ============================================================
        tabbedPane.addTab("Results", resultsPanelFriendly);
        tabbedPane.addTab("History", historyPanel);
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Finally, update the trees.
        updateFriendlyTree();
        updateHistoryTree();
    }

    /**
     * Returns the main panel for registration.
     */
    public JPanel getMainPanel() {
        return mainPanel;
    }

    /**
     * Helper: Returns the selected ReplayedRequestEntry from the given tree.
     */
    private ReplayedRequestEntry getSelectedEntry(JTree tree) {
        TreePath path = tree.getSelectionPath();
        if (path == null) return null;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object obj = node.getUserObject();
        if (obj instanceof ReplayedRequestEntry) {
            return (ReplayedRequestEntry) obj;
        }
        return null;
    }

    /**
     * Called by VerbReplayer for every replayed request.
     * If the status code is 200–399 (excluding 204) the entry is added to successEntries;
     * otherwise to errorEntries.
     * Then updates the appropriate tree and saves data.
     */
    public void logTraffic(String verb, String url, int statusCode,
                           HttpRequest httpRequest, String response) {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String requestText = httpRequest.toString();
        ReplayedRequestEntry entry = new ReplayedRequestEntry(verb, url, statusCode, requestText, response, timestamp);

        if (statusCode >= 200 && statusCode < 400 && statusCode != 204) {
            successEntries.add(entry);
            updateFriendlyTree();
        } else {
            errorEntries.add(entry);
            updateHistoryTree();
        }
        saveData();
    }

    /**
     * Rebuilds the friendly (Results) tree from successEntries while preserving expansion/selection.
     */
    private void updateFriendlyTree() {
        List<List<String>> expanded = getExpandedPaths(resultsFriendlyTree);
        TreePath currentSelection = resultsFriendlyTree.getSelectionPath();
        DefaultMutableTreeNode newRoot = buildGroupedTree(successEntries, ALL_VERBS, "Successful Requests");
        resultsFriendlyTreeModel.setRoot(newRoot);
        restoreExpansion(resultsFriendlyTree, expanded);
        if (currentSelection != null) {
            List<String> selStrings = pathToStringList(currentSelection);
            TreePath newSelection = findPathByStringList(resultsFriendlyTree, selStrings);
            if (newSelection != null) {
                resultsFriendlyTree.setSelectionPath(newSelection);
            }
        }
    }

    /**
     * Rebuilds the History tree from errorEntries while preserving expansion/selection.
     */
    private void updateHistoryTree() {
        List<List<String>> expanded = getExpandedPaths(historyTree);
        TreePath currentSelection = historyTree.getSelectionPath();
        DefaultMutableTreeNode newRoot = buildGroupedTree(errorEntries, ALL_VERBS, "Log of all requests");
        historyTreeModel.setRoot(newRoot);
        restoreExpansion(historyTree, expanded);
        if (currentSelection != null) {
            List<String> selStrings = pathToStringList(currentSelection);
            TreePath newSelection = findPathByStringList(historyTree, selStrings);
            if (newSelection != null) {
                historyTree.setSelectionPath(newSelection);
            }
        }
    }

    /**
     * Builds a grouped tree from the given list of entries.
     * Grouping is done by domain (first token of the URL) and then by first URL segment.
     */
    private DefaultMutableTreeNode buildGroupedTree(List<ReplayedRequestEntry> entries, List<String> allowedVerbs, String rootName) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(rootName);
        Map<String, DefaultMutableTreeNode> domainNodes = new HashMap<>();
        Map<String, DefaultMutableTreeNode> segmentNodes = new HashMap<>();
        for (ReplayedRequestEntry entry : entries) {
            if (!allowedVerbs.contains(entry.verb)) continue;
            String[] parts = entry.url.split("/", 2);
            String domain = parts[0];
            String path = (parts.length > 1) ? parts[1] : "";
            String firstSegment = "";
            if (!path.isEmpty()) {
                String[] pathParts = path.split("/");
                if (pathParts.length > 0) {
                    firstSegment = pathParts[0];
                }
            }
            DefaultMutableTreeNode domainNode = domainNodes.get(domain);
            if (domainNode == null) {
                domainNode = new DefaultMutableTreeNode(domain);
                domainNodes.put(domain, domainNode);
                root.add(domainNode);
            }
            String segKey = domain + "/" + firstSegment;
            DefaultMutableTreeNode segmentNode = segmentNodes.get(segKey);
            if (segmentNode == null) {
                segmentNode = new DefaultMutableTreeNode(firstSegment.isEmpty() ? "/" : firstSegment);
                segmentNodes.put(segKey, segmentNode);
                domainNode.add(segmentNode);
            }
            DefaultMutableTreeNode entryNode = new DefaultMutableTreeNode(entry);
            segmentNode.add(entryNode);
        }
        return root;
    }

    /**
     * Returns a list of expanded paths in the given tree.
     */
    private List<List<String>> getExpandedPaths(JTree tree) {
        List<List<String>> expandedPaths = new ArrayList<>();
        int rowCount = tree.getRowCount();
        for (int i = 0; i < rowCount; i++) {
            TreePath path = tree.getPathForRow(i);
            if (tree.isExpanded(path)) {
                expandedPaths.add(pathToStringList(path));
            }
        }
        return expandedPaths;
    }

    /**
     * Converts a TreePath to a List of Strings.
     */
    private List<String> pathToStringList(TreePath path) {
        List<String> list = new ArrayList<>();
        for (Object node : path.getPath()) {
            list.add(node.toString());
        }
        return list;
    }

    /**
     * Searches for a TreePath in the tree matching the given list of Strings.
     */
    private TreePath findPathByStringList(JTree tree, List<String> pathList) {
        Object root = tree.getModel().getRoot();
        TreePath path = new TreePath(root);
        return findPathRecursive(tree, path, pathList, 0);
    }

    private TreePath findPathRecursive(JTree tree, TreePath currentPath, List<String> pathList, int index) {
        if (index >= pathList.size()) return currentPath;
        Object currentNode = currentPath.getLastPathComponent();
        if (!currentNode.toString().equals(pathList.get(index))) return null;
        if (index == pathList.size() - 1) return currentPath;
        int childCount = tree.getModel().getChildCount(currentNode);
        for (int i = 0; i < childCount; i++) {
            Object child = tree.getModel().getChild(currentNode, i);
            TreePath childPath = currentPath.pathByAddingChild(child);
            TreePath result = findPathRecursive(tree, childPath, pathList, index + 1);
            if (result != null) return result;
        }
        return null;
    }

    /**
     * Restores the expansion state of the tree given a list of expanded paths.
     */
    private void restoreExpansion(JTree tree, List<List<String>> expandedPaths) {
        for (List<String> pathList : expandedPaths) {
            TreePath path = findPathByStringList(tree, pathList);
            if (path != null) {
                tree.expandPath(path);
            }
        }
    }

    /**
     * In the friendly tree, adds a context menu with Send and Highlight actions.
     */
    private void addFriendlyTreeContextMenu() {
        JPopupMenu contextMenu = new JPopupMenu();
        JMenuItem sendItem = new JMenuItem("Send to Repeater");
        sendItem.addActionListener(e -> {
            ReplayedRequestEntry entry = getSelectedEntry(resultsFriendlyTree);
            if (entry != null && entry.requestText != null) {
                HttpRequest req = parseHttpRequest(entry.requestText);
                if (req != null) {
                    VerbReplayer.sendToRepeater(req);
                } else {
                    JOptionPane.showMessageDialog(mainPanel, "Error re-creating HTTP request.");
                }
            } else {
                JOptionPane.showMessageDialog(mainPanel, "Selected item is not a valid HTTP request.");
            }
        });
        JMenuItem highlightItem = new JMenuItem("Highlight/Unhighlight");
        highlightItem.addActionListener(e -> toggleHighlight(resultsFriendlyTree));
        contextMenu.add(sendItem);
        contextMenu.add(highlightItem);

        resultsFriendlyTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { checkForPopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { checkForPopup(e); }
            private void checkForPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = resultsFriendlyTree.getClosestRowForLocation(e.getX(), e.getY());
                    resultsFriendlyTree.setSelectionRow(row);
                    contextMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    /**
     * In the History tree, adds a context menu with Send and Highlight actions.
     */
    private void addHistoryTreeContextMenu() {
        JPopupMenu contextMenu = new JPopupMenu();
        JMenuItem sendItem = new JMenuItem("Send to Repeater");
        sendItem.addActionListener(e -> {
            ReplayedRequestEntry entry = getSelectedEntry(historyTree);
            if (entry != null && entry.requestText != null) {
                HttpRequest req = parseHttpRequest(entry.requestText);
                if (req != null) {
                    VerbReplayer.sendToRepeater(req);
                } else {
                    JOptionPane.showMessageDialog(mainPanel, "Error re-creating HTTP request.");
                }
            } else {
                JOptionPane.showMessageDialog(mainPanel, "Selected item is not a valid HTTP request.");
            }
        });
        JMenuItem highlightItem = new JMenuItem("Highlight/Unhighlight");
        highlightItem.addActionListener(e -> toggleHighlight(historyTree));
        contextMenu.add(sendItem);
        contextMenu.add(highlightItem);

        historyTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { checkForPopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { checkForPopup(e); }
            private void checkForPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = historyTree.getClosestRowForLocation(e.getX(), e.getY());
                    historyTree.setSelectionRow(row);
                    contextMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    /**
     * Toggles the highlight state of the selected node in the given tree.
     * For leaf nodes, toggles the entry’s highlighted flag.
     * For grouping nodes, recursively toggles all descendant entries.
     */
    private void toggleHighlight(JTree tree) {
        TreePath path = tree.getSelectionPath();
        if (path == null) return;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node.isLeaf() && node.getUserObject() instanceof ReplayedRequestEntry) {
            ReplayedRequestEntry entry = (ReplayedRequestEntry) node.getUserObject();
            entry.highlighted = !entry.highlighted;
        } else {
            boolean newState = !nodeHasHighlightedDescendant(node);
            setHighlightRecursive(node, newState);
        }
        tree.repaint();
    }

    /**
     * Returns true if any descendant leaf of the node is highlighted.
     */
    private boolean nodeHasHighlightedDescendant(DefaultMutableTreeNode node) {
        if (node.isLeaf() && node.getUserObject() instanceof ReplayedRequestEntry) {
            return ((ReplayedRequestEntry) node.getUserObject()).highlighted;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            if (nodeHasHighlightedDescendant(child)) return true;
        }
        return false;
    }

    /**
     * Recursively sets the highlighted flag for all descendant leaf nodes.
     */
    private void setHighlightRecursive(DefaultMutableTreeNode node, boolean state) {
        if (node.isLeaf() && node.getUserObject() instanceof ReplayedRequestEntry) {
            ((ReplayedRequestEntry) node.getUserObject()).highlighted = state;
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
                setHighlightRecursive(child, state);
            }
        }
    }

    /**
     * PUBLIC METHOD: Returns the list of allowed HTTP verbs from the History tab's checkboxes.
     * (These control which verbs are replayed.)
     */
    public List<String> getSelectedVerbs() {
        List<String> selectedVerbs = new ArrayList<>();
        if (historyAllFilter.isSelected()) {
            selectedVerbs = new ArrayList<>(ALL_VERBS);
        } else {
            if (historyPutFilter.isSelected()) selectedVerbs.add("PUT");
            if (historyDeleteFilter.isSelected()) selectedVerbs.add("DELETE");
            if (historyHeadFilter.isSelected()) selectedVerbs.add("HEAD");
            if (historyOptionsFilter.isSelected()) selectedVerbs.add("OPTIONS");
            if (historyConnectFilter.isSelected()) selectedVerbs.add("CONNECT");
            if (historyTraceFilter.isSelected()) selectedVerbs.add("TRACE");
            if (historyPatchFilter.isSelected()) selectedVerbs.add("PATCH");
        }
        return selectedVerbs;
    }

    /**
     * Clears (removes) from the successEntries list all entries that are not highlighted.
     * The errorEntries (History log) remain intact.
     */
    private void clearNonHighlightedEntries() {
        successEntries.removeIf(entry -> !entry.highlighted);
    }

    /**
     * Saves the master lists to disk in the same directory as the current project file.
     */
    private void saveData() {
        try {
            File dir = getProjectDirectory();
            File fSuccess = new File(dir, SUCCESS_FILE);
            File fError = new File(dir, ERROR_FILE);
            try (ObjectOutputStream outSuccess = new ObjectOutputStream(new FileOutputStream(fSuccess))) {
                outSuccess.writeObject(successEntries);
            }
            try (ObjectOutputStream outError = new ObjectOutputStream(new FileOutputStream(fError))) {
                outError.writeObject(errorEntries);
            }
        } catch (IOException e) {
            api.logging().logToError("Error saving data: " + e.getMessage());
        }
    }

    /**
     * Loads the master lists from disk in the same directory as the current project file.
     */
    @SuppressWarnings("unchecked")
    private void loadData() {
        File dir = getProjectDirectory();
        File fSuccess = new File(dir, SUCCESS_FILE);
        File fError = new File(dir, ERROR_FILE);
        if (fSuccess.exists()) {
            try (ObjectInputStream inSuccess = new ObjectInputStream(new FileInputStream(fSuccess))) {
                List<ReplayedRequestEntry> loaded = (List<ReplayedRequestEntry>) inSuccess.readObject();
                successEntries.addAll(loaded);
            } catch (Exception e) {
                api.logging().logToError("Error loading success data: " + e.getMessage());
            }
        }
        if (fError.exists()) {
            try (ObjectInputStream inError = new ObjectInputStream(new FileInputStream(fError))) {
                List<ReplayedRequestEntry> loaded = (List<ReplayedRequestEntry>) inError.readObject();
                errorEntries.addAll(loaded);
            } catch (Exception e) {
                api.logging().logToError("Error loading error data: " + e.getMessage());
            }
        }
    }

    /**
     * Attempts to get the directory of the current project file.
     * If unavailable, returns the current working directory.
     * (Replace with the appropriate Montoya API call if available.)
     */
    private File getProjectDirectory() {
        File homeDir = new File(System.getProperty("user.home"));
        api.logging().logToOutput("Using persistent storage directory: " + homeDir.getAbsolutePath());
        return homeDir;
    }

    /**
     * A helper method to (re)create an HttpRequest from its string representation.
     * You must supply an implementation appropriate for your environment.
     */
    private HttpRequest parseHttpRequest(String requestText) {
        // IMPLEMENTATION NOTE:
        // The Montoya API does not provide a built-in method to create an HttpRequest from a String.
        // Replace this with your own code that parses requestText and returns an HttpRequest.
        throw new UnsupportedOperationException("parseHttpRequest(String) is not implemented. Please provide an implementation.");
    }
}
