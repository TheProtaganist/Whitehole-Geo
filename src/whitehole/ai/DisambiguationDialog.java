/*
 * Copyright (C) 2022 Whitehole Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package whitehole.ai;

import whitehole.math.Vec3f;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.ArrayList;

/**
 * DisambiguationDialog helps users select specific objects when command references are ambiguous.
 * It provides a visual interface to choose from multiple matching objects.
 */
public class DisambiguationDialog extends JDialog {
    
    private final List<GalaxyContext.ObjectInfo> candidateObjects;
    private final String originalReference;
    private final String command;
    private List<GalaxyContext.ObjectInfo> selectedObjects;
    private boolean cancelled = false;
    
    private JList<ObjectDisplayItem> objectList;
    private DefaultListModel<ObjectDisplayItem> listModel;
    private JTextArea commandPreview;
    private JButton btnSelectAll;
    private JButton btnSelectNone;
    private JButton btnOK;
    private JButton btnCancel;
    
    /**
     * Creates a disambiguation dialog for ambiguous object references.
     * 
     * @param parent The parent component
     * @param candidateObjects List of objects that match the reference
     * @param originalReference The original object reference from the command
     * @param command The full command being processed
     */
    public DisambiguationDialog(Component parent, List<GalaxyContext.ObjectInfo> candidateObjects, 
                               String originalReference, String command) {
        super(SwingUtilities.getWindowAncestor(parent), "Select Objects", ModalityType.APPLICATION_MODAL);
        
        this.candidateObjects = new ArrayList<>(candidateObjects);
        this.originalReference = originalReference;
        this.command = command;
        this.selectedObjects = new ArrayList<>();
        
        initializeComponents();
        setupEventHandlers();
        pack();
        setLocationRelativeTo(parent);
    }
    
    private void initializeComponents() {
        setLayout(new BorderLayout(10, 10));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Header panel
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);
        
        // Object selection panel
        JPanel selectionPanel = createSelectionPanel();
        add(selectionPanel, BorderLayout.CENTER);
        
        // Command preview panel
        JPanel previewPanel = createPreviewPanel();
        add(previewPanel, BorderLayout.SOUTH);
        
        // Button panel
        JPanel buttonPanel = createButtonPanel();
        add(buttonPanel, BorderLayout.SOUTH);
        
        // Set default selection (first object)
        if (!candidateObjects.isEmpty()) {
            objectList.setSelectedIndex(0);
            updateSelectedObjects();
        }
    }
    
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JLabel titleLabel = new JLabel("Multiple objects match \"" + originalReference + "\"");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        
        JLabel instructionLabel = new JLabel("Select which objects you want to modify:");
        instructionLabel.setFont(instructionLabel.getFont().deriveFont(12f));
        
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(instructionLabel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createSelectionPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Available Objects (" + candidateObjects.size() + " found)"));
        
        // Create list model and populate with objects
        listModel = new DefaultListModel<>();
        for (GalaxyContext.ObjectInfo obj : candidateObjects) {
            listModel.addElement(new ObjectDisplayItem(obj));
        }
        
        // Create list with custom renderer
        objectList = new JList<>(listModel);
        objectList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        objectList.setCellRenderer(new ObjectListCellRenderer());
        objectList.setVisibleRowCount(Math.min(8, candidateObjects.size()));
        
        JScrollPane scrollPane = new JScrollPane(objectList);
        scrollPane.setPreferredSize(new Dimension(500, 200));
        
        // Selection buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnSelectAll = new JButton("Select All");
        btnSelectNone = new JButton("Select None");
        
        buttonPanel.add(btnSelectAll);
        buttonPanel.add(btnSelectNone);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Command Preview"));
        
        commandPreview = new JTextArea(3, 40);
        commandPreview.setEditable(false);
        commandPreview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        commandPreview.setBackground(getBackground());
        commandPreview.setText("Select objects to see how the command will be applied...");
        
        JScrollPane scrollPane = new JScrollPane(commandPreview);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        btnOK = new JButton("Apply to Selected");
        btnCancel = new JButton("Cancel");
        
        btnOK.setPreferredSize(new Dimension(140, btnOK.getPreferredSize().height));
        btnCancel.setPreferredSize(new Dimension(80, btnCancel.getPreferredSize().height));
        
        panel.add(btnOK);
        panel.add(btnCancel);
        
        return panel;
    }
    
    private void setupEventHandlers() {
        // List selection handler
        objectList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateSelectedObjects();
                updateCommandPreview();
                updateButtonStates();
            }
        });
        
        // Double-click to toggle selection
        objectList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = objectList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        if (objectList.isSelectedIndex(index)) {
                            objectList.removeSelectionInterval(index, index);
                        } else {
                            objectList.addSelectionInterval(index, index);
                        }
                    }
                }
            }
        });
        
        // Selection buttons
        btnSelectAll.addActionListener(e -> {
            objectList.setSelectionInterval(0, listModel.getSize() - 1);
        });
        
        btnSelectNone.addActionListener(e -> {
            objectList.clearSelection();
        });
        
        // Dialog buttons
        btnOK.addActionListener(e -> {
            if (!selectedObjects.isEmpty()) {
                cancelled = false;
                dispose();
            }
        });
        
        btnCancel.addActionListener(e -> {
            cancelled = true;
            selectedObjects.clear();
            dispose();
        });
        
        // Escape key to cancel
        getRootPane().registerKeyboardAction(
            e -> {
                cancelled = true;
                selectedObjects.clear();
                dispose();
            },
            KeyStroke.getKeyStroke("ESCAPE"),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        
        // Enter key to confirm
        getRootPane().registerKeyboardAction(
            e -> {
                if (!selectedObjects.isEmpty()) {
                    cancelled = false;
                    dispose();
                }
            },
            KeyStroke.getKeyStroke("ENTER"),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }
    
    private void updateSelectedObjects() {
        selectedObjects.clear();
        for (int index : objectList.getSelectedIndices()) {
            ObjectDisplayItem item = listModel.getElementAt(index);
            selectedObjects.add(item.getObjectInfo());
        }
    }
    
    private void updateCommandPreview() {
        if (selectedObjects.isEmpty()) {
            commandPreview.setText("Select objects to see how the command will be applied...");
            return;
        }
        
        StringBuilder preview = new StringBuilder();
        preview.append("The command will be applied to ").append(selectedObjects.size()).append(" object(s):\n\n");
        
        for (GalaxyContext.ObjectInfo obj : selectedObjects) {
                            preview.append("- ").append(obj.getDisplayName())
                   .append(" (").append(obj.getName()).append(")")
                   .append(" at ").append(formatPosition(obj.getPosition()))
                   .append("\n");
        }
        
        preview.append("\nOriginal command: \"").append(command).append("\"");
        
        commandPreview.setText(preview.toString());
        commandPreview.setCaretPosition(0);
    }
    
    private void updateButtonStates() {
        btnOK.setEnabled(!selectedObjects.isEmpty());
    }
    
    private String formatPosition(Vec3f position) {
        return String.format("(%.1f, %.1f, %.1f)", position.x, position.y, position.z);
    }
    
    /**
     * Shows the disambiguation dialog and returns the selected objects.
     * 
     * @return List of selected objects, or empty list if cancelled
     */
    public List<GalaxyContext.ObjectInfo> showDialog() {
        setVisible(true);
        return cancelled ? new ArrayList<>() : new ArrayList<>(selectedObjects);
    }
    
    /**
     * Returns true if the dialog was cancelled.
     */
    public boolean wasCancelled() {
        return cancelled;
    }
    
    /**
     * Wrapper class for displaying objects in the list.
     */
    private static class ObjectDisplayItem {
        private final GalaxyContext.ObjectInfo objectInfo;
        
        public ObjectDisplayItem(GalaxyContext.ObjectInfo objectInfo) {
            this.objectInfo = objectInfo;
        }
        
        public GalaxyContext.ObjectInfo getObjectInfo() {
            return objectInfo;
        }
        
        @Override
        public String toString() {
            return objectInfo.getDisplayName() + " (" + objectInfo.getName() + ")";
        }
    }
    
    /**
     * Custom cell renderer for the object list.
     */
    private static class ObjectListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                     boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof ObjectDisplayItem) {
                ObjectDisplayItem item = (ObjectDisplayItem) value;
                GalaxyContext.ObjectInfo obj = item.getObjectInfo();
                
                // Create detailed display text
                StringBuilder text = new StringBuilder();
                text.append("<html>");
                text.append("<b>").append(obj.getDisplayName()).append("</b>");
                text.append(" <i>(").append(obj.getName()).append(")</i>");
                text.append("<br>");
                text.append("Position: ").append(formatPosition(obj.getPosition()));
                text.append(" | Scale: ").append(formatPosition(obj.getScale()));
                if (!obj.getTags().isEmpty()) {
                    text.append("<br>");
                    text.append("Tags: ").append(String.join(", ", obj.getTags()));
                }
                text.append("</html>");
                
                setText(text.toString());
            }
            
            return this;
        }
        
        private String formatPosition(Vec3f position) {
            return String.format("(%.1f, %.1f, %.1f)", position.x, position.y, position.z);
        }
    }
    
    /**
     * Static method to show disambiguation dialog and get user selection.
     */
    public static List<GalaxyContext.ObjectInfo> disambiguateObjects(Component parent, 
                                                                    List<GalaxyContext.ObjectInfo> candidates,
                                                                    String originalReference, 
                                                                    String command) {
        if (candidates == null || candidates.isEmpty()) {
            return new ArrayList<>();
        }
        
        if (candidates.size() == 1) {
            return new ArrayList<>(candidates);
        }
        
        DisambiguationDialog dialog = new DisambiguationDialog(parent, candidates, originalReference, command);
        return dialog.showDialog();
    }
}