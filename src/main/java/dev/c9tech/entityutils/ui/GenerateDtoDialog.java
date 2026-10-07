package dev.c9tech.entityutils.ui;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import dev.c9tech.entityutils.generator.TypeConversionHelper;
import dev.c9tech.entityutils.model.DtoFieldItem;
import dev.c9tech.entityutils.model.DtoStyle;
import dev.c9tech.entityutils.util.PsiClassUtil;
import dev.c9tech.entityutils.util.PsiClassUtil.PropertyDescriptor;
import dev.c9tech.entityutils.util.StringUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class GenerateDtoDialog extends DialogWrapper {

    private final Project project;
    private final PsiClass entityClass;
    private final List<DtoFieldItem> fieldItems = new ArrayList<>();

    private JBTextField tfDtoName;
    private JBTextField tfPackage;
    private ComboBox<DtoStyle> comboStyle;
    private JBCheckBox cbSerializable;
    private JBCheckBox cbOpenMapperAfter;
    private JBTable fieldsTable;
    private FieldTableModel tableModel;

    public GenerateDtoDialog(Project project, PsiClass entityClass) {
        super(project, true);
        this.project = project;
        this.entityClass = entityClass;

        setTitle("EntityUtils - Generate DTO from Entity: " + entityClass.getName());
        initFields();
        init();
    }

    private void initFields() {
        List<PropertyDescriptor> props = PsiClassUtil.getAllProperties(entityClass);
        for (PropertyDescriptor prop : props) {
            fieldItems.add(new DtoFieldItem(prop.getName(), prop.getTypeText(), prop.getField()));
        }
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(0, 10));
        mainPanel.setPreferredSize(new Dimension(680, 540));

        // Top form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Entity Class Info
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        formPanel.add(new JBLabel("Source Entity:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        JBTextField tfEntity = new JBTextField(entityClass.getQualifiedName());
        tfEntity.setEditable(false);
        formPanel.add(tfEntity, gbc);

        // Row 1: DTO Class Name
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        formPanel.add(new JBLabel("DTO Class Name:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        tfDtoName = new JBTextField(entityClass.getName() + "Dto");
        formPanel.add(tfDtoName, gbc);

        // Row 2: Target Package
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        formPanel.add(new JBLabel("Target Package:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        String entityPkg = PsiClassUtil.getPackageName(entityClass);
        String defaultDtoPkg = PsiClassUtil.getSuggestedDtoPackage(entityPkg);
        tfPackage = new JBTextField(defaultDtoPkg);
        formPanel.add(tfPackage, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        JButton btnBrowsePkg = new JButton("Browse...");
        btnBrowsePkg.addActionListener(e -> {
            String chosen = ClassChooserUtil.choosePackage(project, "Choose Target Package", tfPackage.getText());
            if (chosen != null) {
                tfPackage.setText(chosen);
            }
        });
        formPanel.add(btnBrowsePkg, gbc);

        // Row 3: Generation Style
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        formPanel.add(new JBLabel("DTO Style:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        comboStyle = new ComboBox<>(DtoStyle.values());
        comboStyle.setSelectedItem(DtoStyle.JAVA_BEAN);
        formPanel.add(comboStyle, gbc);

        // Row 4: Options Checkboxes
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        cbSerializable = new JBCheckBox("implements Serializable", true);
        cbOpenMapperAfter = new JBCheckBox("Generate EntityMapper after DTO creation", true);
        optionsPanel.add(cbSerializable);
        optionsPanel.add(cbOpenMapperAfter);
        formPanel.add(optionsPanel, gbc);

        mainPanel.add(formPanel, BorderLayout.NORTH);

        // Center: Fields Table
        tableModel = new FieldTableModel(fieldItems);
        fieldsTable = new JBTable(tableModel);
        fieldsTable.getColumnModel().getColumn(0).setMaxWidth(40);
        fieldsTable.getColumnModel().getColumn(0).setPreferredWidth(40);

        // Type column editor with common types dropdown
        JComboBox<String> typeComboBox = new JComboBox<>(TypeConversionHelper.COMMON_TYPES);
        typeComboBox.setEditable(true);
        TableColumn typeColumn = fieldsTable.getColumnModel().getColumn(2);
        typeColumn.setCellEditor(new DefaultCellEditor(typeComboBox));

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Select & Customize Fields (Double-click to edit Name or Type)"));

        // Button toolbar for table
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JButton btnSelectAll = new JButton("Select All");
        btnSelectAll.addActionListener(e -> setAllFieldsSelected(true));

        JButton btnDeselectAll = new JButton("Deselect All");
        btnDeselectAll.addActionListener(e -> setAllFieldsSelected(false));

        JButton btnExcludeAudit = new JButton("Exclude Audit Fields");
        btnExcludeAudit.addActionListener(e -> excludeAuditFields());

        buttonBar.add(btnSelectAll);
        buttonBar.add(btnDeselectAll);
        buttonBar.add(btnExcludeAudit);

        tablePanel.add(buttonBar, BorderLayout.NORTH);
        tablePanel.add(new JScrollPane(fieldsTable), BorderLayout.CENTER);

        mainPanel.add(tablePanel, BorderLayout.CENTER);

        return mainPanel;
    }

    private void setAllFieldsSelected(boolean selected) {
        for (DtoFieldItem item : fieldItems) {
            item.setSelected(selected);
        }
        tableModel.fireTableDataChanged();
    }

    private void excludeAuditFields() {
        for (DtoFieldItem item : fieldItems) {
            if (PsiClassUtil.isAuditField(item.getName())) {
                item.setSelected(false);
            }
        }
        tableModel.fireTableDataChanged();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        String dtoName = tfDtoName.getText().trim();
        if (dtoName.isEmpty()) {
            return new ValidationInfo("DTO class name cannot be empty", tfDtoName);
        }
        if (!dtoName.matches("^[A-Za-z_$][A-Za-z0-9_$]*$")) {
            return new ValidationInfo("Invalid Java class name: " + dtoName, tfDtoName);
        }

        boolean hasSelectedField = fieldItems.stream().anyMatch(DtoFieldItem::isSelected);
        if (!hasSelectedField) {
            return new ValidationInfo("Please select at least one field for the DTO", fieldsTable);
        }

        return null;
    }

    public String getDtoClassName() {
        return tfDtoName.getText().trim();
    }

    public String getTargetPackageName() {
        return tfPackage.getText().trim();
    }

    public DtoStyle getDtoStyle() {
        return (DtoStyle) comboStyle.getSelectedItem();
    }

    public boolean isSerializable() {
        return cbSerializable.isSelected();
    }

    public boolean isOpenMapperAfter() {
        return cbOpenMapperAfter.isSelected();
    }

    public List<DtoFieldItem> getFieldItems() {
        return fieldItems;
    }

    public PsiDirectory getOrCreateTargetDirectory() {
        PsiFile containingFile = entityClass.getContainingFile();
        PsiDirectory currentDir = containingFile != null ? containingFile.getContainingDirectory() : null;
        if (currentDir == null) {
            return null;
        }

        String targetPkg = getTargetPackageName();
        String currentPkg = PsiClassUtil.getPackageName(entityClass);

        if (targetPkg.equals(currentPkg)) {
            return currentDir;
        }

        PsiDirectory sourceRootDir = findSourceRootDir(currentDir, currentPkg);
        final PsiDirectory finalRoot = sourceRootDir != null ? sourceRootDir : currentDir;

        return WriteCommandAction.writeCommandAction(project)
                .withName("Create Package Directory")
                .compute(() -> createPackageDirectories(finalRoot, targetPkg));
    }

    private PsiDirectory findSourceRootDir(PsiDirectory dir, String pkg) {
        if (StringUtil.isEmpty(pkg)) {
            return dir;
        }
        String[] parts = pkg.split("\\.");
        PsiDirectory current = dir;
        for (int i = parts.length - 1; i >= 0; i--) {
            if (current != null && current.getName().equals(parts[i])) {
                current = current.getParentDirectory();
            }
        }
        return current != null ? current : dir;
    }

    private PsiDirectory createPackageDirectories(PsiDirectory root, String pkg) {
        if (StringUtil.isEmpty(pkg)) {
            return root;
        }
        String[] parts = pkg.split("\\.");
        PsiDirectory current = root;
        for (String part : parts) {
            PsiDirectory sub = current.findSubdirectory(part);
            if (sub == null) {
                sub = current.createSubdirectory(part);
            }
            current = sub;
        }
        return current;
    }

    private static class FieldTableModel extends AbstractTableModel {
        private final String[] columns = {"", "Field Name", "Type"};
        private final List<DtoFieldItem> items;

        public FieldTableModel(List<DtoFieldItem> items) {
            this.items = items;
        }

        @Override
        public int getRowCount() {
            return items.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            // Checkbox (0), Field Name (1), and Type (2) are all editable!
            return true;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            DtoFieldItem item = items.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return item.isSelected();
                case 1:
                    return item.getName();
                case 2:
                    return item.getTypeText();
                default:
                    return null;
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            DtoFieldItem item = items.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    if (aValue instanceof Boolean) {
                        item.setSelected((Boolean) aValue);
                        fireTableCellUpdated(rowIndex, columnIndex);
                    }
                    break;
                case 1:
                    if (aValue != null) {
                        item.setName(aValue.toString().trim());
                        fireTableCellUpdated(rowIndex, columnIndex);
                    }
                    break;
                case 2:
                    if (aValue != null) {
                        item.setTypeText(aValue.toString().trim());
                        fireTableCellUpdated(rowIndex, columnIndex);
                    }
                    break;
            }
        }
    }
}
