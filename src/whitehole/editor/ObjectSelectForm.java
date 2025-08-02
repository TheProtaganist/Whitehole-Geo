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
package whitehole.editor;

import java.util.LinkedList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import whitehole.Whitehole;
import whitehole.db.ObjectDB;

public final class ObjectSelectForm extends javax.swing.JDialog {
    // -------------------------------------------------------------------------------------------------------------------------
    // Singleton pattern implementation
    
    private static final ObjectSelectForm SINGLETON = new ObjectSelectForm();
    
    public static boolean openChangeObjectDialog(String objectName) {
        SINGLETON.showChangeObject(objectName);
        return SINGLETON.validResult;
    }
    
    public static boolean openNewObjectDialog(String objectType, List<String> layerNames) {
        SINGLETON.showNewObject(objectType, layerNames);
        return SINGLETON.validResult;
    }
    
    public static void requestUpdateLAF() {
        SwingUtilities.updateComponentTreeUI(SINGLETON);
    }
    
    public static String getResultName() {
        return SINGLETON.resultName;
    }
    
    public static String getResultLayer() {
        return SINGLETON.resultLayer;
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    // Actual dialog implementation
    
    private final DefaultTreeModel normalModel, searchModel;
    private final DefaultMutableTreeNode normalRoot;
    private final SearchNode searchRoot = new SearchNode();
    private final LinkedList<ObjectCategoryNode> categoryNodes = new LinkedList();
    
    private ObjectDB.ObjectInfo selectedNode;
    private String resultName, resultLayer;
    private String tempObjectType;
    private boolean validResult;
    
    private ObjectSelectForm() {
        super((JFrame)null, true);
        initComponents();
        
        normalRoot = new DefaultMutableTreeNode(null, true);
        normalModel = (DefaultTreeModel)treeObjects.getModel();
        normalModel.setRoot(normalRoot);
        searchModel = new DefaultTreeModel(searchRoot);
        
        initNodes();
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    // Node initialization and declaration
    
    private void initNodes() {
        for (ObjectDB.CategoryInfo info : ObjectDB.getCategories()) {
            ObjectCategoryNode node = new ObjectCategoryNode(info.toString(), info.getDescription());
            categoryNodes.add(node);
            normalRoot.add(node);
        }
    }
    
    private static boolean isAllowedNode(ObjectDB.ObjectInfo info, String category, int game, String type) {
        if (info.games() < 4 && (game & info.games()) == 0) {
            return false;
        }
        
        if (!type.equals("") && !info.destFile(game).equalsIgnoreCase(type)) {
            return false;
        }
        
        return info.category().equalsIgnoreCase(category);
    }
    
    private class ObjectCategoryNode extends DefaultMutableTreeNode {
        final String key, description;
        
        ObjectCategoryNode(String identifier, String desc) {
            super(null, true);
            key = identifier;
            description = desc;
        }
        
        @Override
        public String toString() {
            return description;
        }
        
        void populate(int game, String type) {
            removeAllChildren();
            ObjectDB.getObjectInfos().values().stream().filter(i -> isAllowedNode(i, key, game, type)).forEach(i -> add(i));
            
            if (children != null) {
                children.sort((n1, n2) -> n1.toString().compareTo(n2.toString()));
            }
        }
    }
    
    private class SearchNode extends DefaultMutableTreeNode {
        SearchNode() {
            super(null, true);
        }
            
        void sort() {
            if (children != null) {
                children.sort((n1, n2) -> n1.toString().compareTo(n2.toString()));
            }
        }
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    // Dialog accessors
    
    private void populate() {
        for (ObjectCategoryNode node : categoryNodes) {
            node.populate(Whitehole.getCurrentGameType(), tempObjectType);
        }
        
        normalModel.reload();
        treeObjects.setModel(normalModel);
    }
    
    private void unpopulate() {
        for (ObjectCategoryNode node : categoryNodes) {
            node.removeAllChildren();
        }
        
        searchRoot.removeAllChildren();
        
        normalModel.reload();
        searchModel.reload();
    }
    
    public void showNewObject(String objectType, List<String> layerNames) {
        if (isVisible()) {
            return;
        }
        
        validResult = false;
        resultName = null;
        resultLayer = null;
        tempObjectType = objectType;
        txtSearchObj.setText("");
        txtObjId.setText("");
        txtSearchObj.requestFocus();
        
        populate();
        
        cmoLayer.removeAllItems();
        
        for (String layer : layerNames) {
            cmoLayer.addItem(layer);
        }
        
        lblLayer.setVisible(true);
        cmoLayer.setVisible(true);
        setVisible(true);
    }
    
    public void showChangeObject(String objectName) {
        if (isVisible()) {
            return;
        }
        
        validResult = false;
        resultName = null;
        resultLayer = null;
        tempObjectType = "";
        txtSearchObj.setText("");
        txtObjId.setText("");
        txtSearchObj.requestFocus();
        
        // Reload nodes and make selection from object name
        populate();
        txtObjId.setText(objectName);
        
        String objkey = objectName.toLowerCase();
        
        if (ObjectDB.getObjectInfos().containsKey(objkey)) {
            TreePath path = new TreePath(normalModel.getPathToRoot(ObjectDB.getObjectInfos().get(objkey)));
            treeObjects.setSelectionPath(path);
        }
        else {
            fetchInfoFromSelection();
        }
        
        lblLayer.setVisible(false);
        cmoLayer.setVisible(false);
        setVisible(true);
    }
    
    private void fetchInfoFromSelection() {
        if (selectedNode != null) {
            txtObjId.setText(selectedNode.internalName());
            lblInternalName.setText(String.format("%s (%s)", selectedNode.internalName(), selectedNode.className(Whitehole.getCurrentGameType())));
            
            String games = "";
            
            if ((selectedNode.games() & 1) == 1) {
                games += games.length() > 0 ? ", SMG1" : "SMG1";
            }
            if ((selectedNode.games() & 2) == 2) {
                games += games.length() > 0 ? ", SMG2" : "SMG2";
            }
            if ((selectedNode.games() & 4) == 4) {
                games += games.length() > 0 ? ", Custom" : "Custom";
            }
            
            lblDispGames.setText(games);
            lblDispArchive.setText(selectedNode.destArchive());
            lblDispPlacementList.setText(selectedNode.destFile(Whitehole.getCurrentGameType()));
            txtDispDescription.setText(selectedNode.description());
            txtDispClassNotes.setText(selectedNode.classDescription(Whitehole.getCurrentGameType()));
            lblDispIsUnused.setVisible(selectedNode.isUnused());
            lblDispIsLeftover.setVisible(selectedNode.isLeftover());
        }
        else {
            lblInternalName.setText("(Nothing selected)");
            lblDispGames.setText("?");
            lblDispArchive.setText("?");
            lblDispPlacementList.setText("?");
            txtDispDescription.setText("");
            txtDispClassNotes.setText("");
            lblDispIsUnused.setVisible(false);
            lblDispIsLeftover.setVisible(false);
        }
    }
    
    private void confirm() {
        if (cmoLayer.isVisible()) {
            resultLayer = (String)cmoLayer.getSelectedItem();
        }
        
        String maybeResult = txtObjId.getText().trim();
        
        if (maybeResult.isEmpty()) {
            validResult = false;
        }
        else {
            resultName = maybeResult;
            validResult = true;
        }
        
        setVisible(false);
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content
     * of this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        scrObjects = new javax.swing.JScrollPane();
        treeObjects = new javax.swing.JTree();
        pnlSelection = new javax.swing.JPanel();
        lblObjId = new javax.swing.JLabel();
        txtObjId = new javax.swing.JTextField();
        cmoLayer = new javax.swing.JComboBox<>();
        lblLayer = new javax.swing.JLabel();
        btnConfirm = new javax.swing.JButton();
        pnlSearchBar = new javax.swing.JPanel();
        lblSearchObj = new javax.swing.JLabel();
        txtSearchObj = new javax.swing.JTextField();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));
        jTabbedPane1 = new javax.swing.JTabbedPane();
        pnlObjInfo = new javax.swing.JPanel();
        pnlGeneralInformation = new javax.swing.JPanel();
        lblInternalName = new javax.swing.JLabel();
        lblGames = new javax.swing.JLabel();
        lblArchive = new javax.swing.JLabel();
        lblPlacementList = new javax.swing.JLabel();
        lblDispGames = new javax.swing.JLabel();
        lblDispArchive = new javax.swing.JLabel();
        lblDispPlacementList = new javax.swing.JLabel();
        sep1 = new javax.swing.JSeparator();
        lblDescription = new javax.swing.JLabel();
        scrDispDescription = new javax.swing.JScrollPane();
        txtDispDescription = new javax.swing.JTextPane();
        lblClassNotes = new javax.swing.JLabel();
        scrDispClassNotes = new javax.swing.JScrollPane();
        txtDispClassNotes = new javax.swing.JTextPane();
        lblDispIsUnused = new javax.swing.JLabel();
        lblDispIsLeftover = new javax.swing.JLabel();

        setTitle("Select Object");
        setIconImage(Whitehole.ICON);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        javax.swing.tree.DefaultMutableTreeNode treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("root");
        treeObjects.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        treeObjects.setRootVisible(false);
        treeObjects.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                treeObjectsMouseClicked(evt);
            }
        });
        treeObjects.addTreeSelectionListener(new javax.swing.event.TreeSelectionListener() {
            public void valueChanged(javax.swing.event.TreeSelectionEvent evt) {
                treeObjectsValueChanged(evt);
            }
        });
        scrObjects.setViewportView(treeObjects);

        pnlSelection.setLayout(new java.awt.GridBagLayout());

        lblObjId.setText("New Object: ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlSelection.add(lblObjId, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlSelection.add(txtObjId, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlSelection.add(cmoLayer, gridBagConstraints);

        lblLayer.setText("Destination Layer: ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlSelection.add(lblLayer, gridBagConstraints);

        btnConfirm.setText("Confirm");
        btnConfirm.setToolTipText("");
        btnConfirm.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnConfirmActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlSelection.add(btnConfirm, gridBagConstraints);

        pnlSearchBar.setLayout(new java.awt.GridBagLayout());

        lblSearchObj.setText("Search Object...");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlSearchBar.add(lblSearchObj, gridBagConstraints);

        txtSearchObj.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                txtSearchObjKeyReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlSearchBar.add(txtSearchObj, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlSearchBar.add(filler1, gridBagConstraints);

        pnlGeneralInformation.setLayout(new java.awt.GridBagLayout());

        lblInternalName.setFont(new java.awt.Font("Dialog", 0, 18)); // NOI18N
        lblInternalName.setText("<InternalName> (<ClassName>)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        pnlGeneralInformation.add(lblInternalName, gridBagConstraints);

        lblGames.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        lblGames.setText("Games");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        pnlGeneralInformation.add(lblGames, gridBagConstraints);

        lblArchive.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        lblArchive.setText("Archive");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        pnlGeneralInformation.add(lblArchive, gridBagConstraints);

        lblPlacementList.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        lblPlacementList.setText("Placement List");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        pnlGeneralInformation.add(lblPlacementList, gridBagConstraints);

        lblDispGames.setText("<Games>");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        pnlGeneralInformation.add(lblDispGames, gridBagConstraints);

        lblDispArchive.setText("<Archive>");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        pnlGeneralInformation.add(lblDispArchive, gridBagConstraints);

        lblDispPlacementList.setText("<Placement List>");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        pnlGeneralInformation.add(lblDispPlacementList, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipady = 8;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        pnlGeneralInformation.add(sep1, gridBagConstraints);

        lblDescription.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        lblDescription.setText("Brief Description");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        pnlGeneralInformation.add(lblDescription, gridBagConstraints);

        txtDispDescription.setEditable(false);
        scrDispDescription.setViewportView(txtDispDescription);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        pnlGeneralInformation.add(scrDispDescription, gridBagConstraints);

        lblClassNotes.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        lblClassNotes.setText("Class Description");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        pnlGeneralInformation.add(lblClassNotes, gridBagConstraints);

        txtDispClassNotes.setEditable(false);
        scrDispClassNotes.setViewportView(txtDispClassNotes);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        pnlGeneralInformation.add(scrDispClassNotes, gridBagConstraints);

        lblDispIsUnused.setFont(new java.awt.Font("Dialog", 1, 12)); // NOI18N
        lblDispIsUnused.setText("This object is not used in any of the official games.");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        pnlGeneralInformation.add(lblDispIsUnused, gridBagConstraints);

        lblDispIsLeftover.setFont(new java.awt.Font("Dialog", 1, 12)); // NOI18N
        lblDispIsLeftover.setText("This object was left over in the code of Super Mario Galaxy 2.");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        pnlGeneralInformation.add(lblDispIsLeftover, gridBagConstraints);

        javax.swing.GroupLayout pnlObjInfoLayout = new javax.swing.GroupLayout(pnlObjInfo);
        pnlObjInfo.setLayout(pnlObjInfoLayout);
        pnlObjInfoLayout.setHorizontalGroup(
            pnlObjInfoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlObjInfoLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(pnlGeneralInformation, javax.swing.GroupLayout.DEFAULT_SIZE, 517, Short.MAX_VALUE)
                .addContainerGap())
        );
        pnlObjInfoLayout.setVerticalGroup(
            pnlObjInfoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlObjInfoLayout.createSequentialGroup()
                .addComponent(pnlGeneralInformation, javax.swing.GroupLayout.DEFAULT_SIZE, 518, Short.MAX_VALUE)
                .addContainerGap())
        );

        jTabbedPane1.addTab("General Information", pnlObjInfo);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(pnlSelection, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 350, Short.MAX_VALUE)
                    .addComponent(scrObjects, javax.swing.GroupLayout.Alignment.LEADING))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTabbedPane1)
                .addContainerGap())
            .addComponent(pnlSearchBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(pnlSearchBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(scrObjects)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pnlSelection, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jTabbedPane1))
                .addContainerGap())
        );

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void btnConfirmActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnConfirmActionPerformed
        confirm();
    }//GEN-LAST:event_btnConfirmActionPerformed

    private void treeObjectsValueChanged(javax.swing.event.TreeSelectionEvent evt) {//GEN-FIRST:event_treeObjectsValueChanged
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)treeObjects.getLastSelectedPathComponent();
        
        if (node != null && node instanceof ObjectDB.ObjectInfo) {
            selectedNode = (ObjectDB.ObjectInfo)node;
        }
        else {
            selectedNode = null;
        }
        
        fetchInfoFromSelection();
    }//GEN-LAST:event_treeObjectsValueChanged

    private void treeObjectsMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_treeObjectsMouseClicked
        if (evt.getClickCount() > 1 && selectedNode != null) {
            confirm();
        }
    }//GEN-LAST:event_treeObjectsMouseClicked

    private void txtSearchObjKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_txtSearchObjKeyReleased
        String searchcase = txtSearchObj.getText().toLowerCase().trim();
        
        // Ignore strings that are less than 3 characters. This improves performance and sorts out obsolete cases.
        if (searchcase.length() < 3) {
            populate();
        }
        else {
            searchRoot.removeAllChildren();
            int game = Whitehole.getCurrentGameType();
            
            for (ObjectDB.ObjectInfo info : ObjectDB.getObjectInfos().values()) {
                if (info.games() < 4 && (game & info.games()) == 0) {
                    continue;
                }
                if (!tempObjectType.equals("") && !tempObjectType.equalsIgnoreCase(info.destFile(game))) {
                    continue;
                }
                
                if (info.toString().toLowerCase().contains(searchcase) || info.internalName().toLowerCase().contains(searchcase)) {
                    searchRoot.add(info);
                }
            }
            
            searchRoot.sort();
            searchModel.reload();
            treeObjects.setModel(searchModel);
        }
    }//GEN-LAST:event_txtSearchObjKeyReleased

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        unpopulate();
    }//GEN-LAST:event_formWindowClosing
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnConfirm;
    private javax.swing.JComboBox<String> cmoLayer;
    private javax.swing.Box.Filler filler1;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JLabel lblArchive;
    private javax.swing.JLabel lblClassNotes;
    private javax.swing.JLabel lblDescription;
    private javax.swing.JLabel lblDispArchive;
    private javax.swing.JLabel lblDispGames;
    private javax.swing.JLabel lblDispIsLeftover;
    private javax.swing.JLabel lblDispIsUnused;
    private javax.swing.JLabel lblDispPlacementList;
    private javax.swing.JLabel lblGames;
    private javax.swing.JLabel lblInternalName;
    private javax.swing.JLabel lblLayer;
    private javax.swing.JLabel lblObjId;
    private javax.swing.JLabel lblPlacementList;
    private javax.swing.JLabel lblSearchObj;
    private javax.swing.JPanel pnlGeneralInformation;
    private javax.swing.JPanel pnlObjInfo;
    private javax.swing.JPanel pnlSearchBar;
    private javax.swing.JPanel pnlSelection;
    private javax.swing.JScrollPane scrDispClassNotes;
    private javax.swing.JScrollPane scrDispDescription;
    private javax.swing.JScrollPane scrObjects;
    private javax.swing.JSeparator sep1;
    private javax.swing.JTree treeObjects;
    private javax.swing.JTextPane txtDispClassNotes;
    private javax.swing.JTextPane txtDispDescription;
    private javax.swing.JTextField txtObjId;
    private javax.swing.JTextField txtSearchObj;
    // End of variables declaration//GEN-END:variables
}
