package dev.c9tech.entityutils.ui;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import dev.c9tech.entityutils.generator.MapperGenerator.MapperOptions;
import dev.c9tech.entityutils.generator.TypeConversionHelper;
import dev.c9tech.entityutils.model.DtoStyle;
import dev.c9tech.entityutils.model.FieldMappingItem;
import dev.c9tech.entityutils.model.FieldMappingItem.ConversionKind;
import dev.c9tech.entityutils.model.MethodNamingStyle;
import dev.c9tech.entityutils.util.PsiClassUtil;
import dev.c9tech.entityutils.util.PsiClassUtil.PropertyDescriptor;
import dev.c9tech.entityutils.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class GenerateMapperDialog extends DialogWrapper {

    private final Project project;
    private PsiClass sourceClass;
    private PsiClass targetClass;
    private PsiClass existingMapperClass;

    private JBTextField tfSourceClass;
    private JBTextField tfTargetClass;

    private JBCheckBox cbCreateDto;
    private ComboBox<DtoStyle> comboDtoStyle;

    private JBRadioButton rbNewMapper;
    private JBRadioButton rbExistingMapper;
    private JBTextField tfMapperClassName;
    private JBTextField tfMapperPackage;
    private JBTextField tfExistingMapperClass;

    private JBCheckBox cbForwardSingle;
    private JBCheckBox cbForwardList;
    private JBCheckBox cbReverseSingle;
    private JBCheckBox cbReverseList;
    private JBCheckBox cbMerge;

    private ComboBox<MethodNamingStyle> comboNamingStyle;

    private final List<FieldMappingItem> mappingItems = new ArrayList<>();
    private MappingTableModel tableModel;
    private JBTable mappingTable;

    public GenerateMapperDialog(Project project, PsiClass sourceClass, PsiClass targetClass) {
        super(project, true);
        this.project = project;
        this.sourceClass = sourceClass;
        this.targetClass = targetClass;

        // Auto-detect target class in package ....dto if not specified
        if (this.sourceClass != null && this.targetClass == null) {
            this.targetClass = PsiClassUtil.findRelatedClass(project, this.sourceClass);
        }

        setTitle("EntityUtils - Generate EntityMapper");
        initMappingList();
        init();
    }

    private void initMappingList() {
        mappingItems.clear();
        if (sourceClass == null) {
            return;
        }

        List<PropertyDescriptor> sourceProps = PsiClassUtil.getAllProperties(sourceClass);
        for (PropertyDescriptor sProp : sourceProps) {
            PropertyDescriptor tProp = targetClass != null
                    ? PsiClassUtil.findMatchingProperty(targetClass, sProp.getName())
                    : null;

            String targetName = tProp != null ? tProp.getName() : sProp.getName();
            String targetType = tProp != null ? tProp.getTypeText() : sProp.getTypeText();
            PsiField tField = tProp != null ? tProp.getField() : null;

            FieldMappingItem item = new FieldMappingItem(
                    sProp.getName(),
                    sProp.getTypeText(),
                    targetName,
                    targetType,
                    sProp.getField(),
                    tField
            );
            mappingItems.add(item);
        }
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(0, 8));
        mainPanel.setPreferredSize(new Dimension(860, 640));

        // Top: Classes configuration
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(BorderFactory.createTitledBorder("Source Entity & Target DTO"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(3);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Source Class
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        topPanel.add(new JBLabel("Source Class (Entity):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        tfSourceClass = new JBTextField(sourceClass != null ? sourceClass.getQualifiedName() : "");
        tfSourceClass.setEditable(false);
        topPanel.add(tfSourceClass, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        JButton btnBrowseSource = new JButton("Browse...");
        btnBrowseSource.addActionListener(e -> {
            PsiClass chosen = ClassChooserUtil.chooseClass(project, "Choose Source Class", sourceClass);
            if (chosen != null) {
                sourceClass = chosen;
                tfSourceClass.setText(chosen.getQualifiedName());
                if (targetClass == null) {
                    targetClass = PsiClassUtil.findRelatedClass(project, sourceClass);
                    if (targetClass != null) {
                        tfTargetClass.setText(targetClass.getQualifiedName());
                    } else {
                        tfTargetClass.setText(PsiClassUtil.getSuggestedTargetClassQualifiedName(sourceClass));
                    }
                }
                refreshMappings();
            }
        });
        topPanel.add(btnBrowseSource, gbc);

        // Target Class (Auto-suggested with package ....dto)
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        topPanel.add(new JBLabel("Target Class (DTO):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        String initialTargetName = targetClass != null
                ? targetClass.getQualifiedName()
                : PsiClassUtil.getSuggestedTargetClassQualifiedName(sourceClass);
        tfTargetClass = new JBTextField(initialTargetName);
        tfTargetClass.setToolTipText("Auto-suggested with ....dto package convention.");

        tfTargetClass.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                String text = tfTargetClass.getText().trim();
                if (!text.isEmpty()) {
                    PsiClass found = JavaPsiFacade.getInstance(project).findClass(text, GlobalSearchScope.projectScope(project));
                    if (found != null && !found.equals(targetClass)) {
                        targetClass = found;
                        cbCreateDto.setSelected(false);
                        refreshMappings();
                    } else if (found == null) {
                        targetClass = null;
                        cbCreateDto.setSelected(true);
                    }
                }
            }
        });
        topPanel.add(tfTargetClass, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        JButton btnBrowseTarget = new JButton("Browse...");
        btnBrowseTarget.addActionListener(e -> {
            PsiClass chosen = ClassChooserUtil.chooseClass(project, "Choose Target Class", targetClass);
            if (chosen != null) {
                targetClass = chosen;
                tfTargetClass.setText(chosen.getQualifiedName());
                cbCreateDto.setSelected(false);
                refreshMappings();
            }
        });
        topPanel.add(btnBrowseTarget, gbc);

        // Row 2: Checkbox Create DTO from Entity
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        JPanel dtoOptionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        cbCreateDto = new JBCheckBox("Auto-create/update DTO class from this mapping", targetClass == null);
        comboDtoStyle = new ComboBox<>(DtoStyle.values());
        comboDtoStyle.setSelectedItem(DtoStyle.JAVA_BEAN);
        comboDtoStyle.setEnabled(cbCreateDto.isSelected());

        cbCreateDto.addActionListener(e -> comboDtoStyle.setEnabled(cbCreateDto.isSelected()));

        dtoOptionPanel.add(cbCreateDto);
        dtoOptionPanel.add(new JBLabel("DTO Style:"));
        dtoOptionPanel.add(comboDtoStyle);
        topPanel.add(dtoOptionPanel, gbc);

        // Destination Mapper Class Panel
        JPanel destPanel = new JPanel(new GridBagLayout());
        destPanel.setBorder(BorderFactory.createTitledBorder("Destination Mapper Class"));
        GridBagConstraints dgbc = new GridBagConstraints();
        dgbc.insets = JBUI.insets(3);
        dgbc.fill = GridBagConstraints.HORIZONTAL;

        rbNewMapper = new JBRadioButton("Create New Mapper Class", true);
        rbExistingMapper = new JBRadioButton("Insert into Existing Class", false);
        ButtonGroup bg = new ButtonGroup();
        bg.add(rbNewMapper);
        bg.add(rbExistingMapper);

        dgbc.gridx = 0;
        dgbc.gridy = 0;
        dgbc.gridwidth = 3;
        destPanel.add(rbNewMapper, dgbc);

        dgbc.gridy = 1;
        dgbc.gridwidth = 1;
        dgbc.weightx = 0;
        destPanel.add(new JBLabel("Mapper Name:"), dgbc);

        dgbc.gridx = 1;
        dgbc.weightx = 1.0;
        String defaultMapperName = sourceClass != null ? sourceClass.getName() + "Mapper" : "EntityMapper";
        tfMapperClassName = new JBTextField(defaultMapperName);
        destPanel.add(tfMapperClassName, dgbc);

        dgbc.gridx = 0;
        dgbc.gridy = 2;
        dgbc.weightx = 0;
        destPanel.add(new JBLabel("Package:"), dgbc);

        dgbc.gridx = 1;
        dgbc.weightx = 1.0;
        String defaultPkg = sourceClass != null ? PsiClassUtil.getPackageName(sourceClass) : "";
        if (StringUtil.isNotEmpty(defaultPkg)) {
            if (defaultPkg.endsWith(".entity") || defaultPkg.endsWith(".entities") || defaultPkg.endsWith(".model")) {
                int lastDot = defaultPkg.lastIndexOf('.');
                defaultPkg = defaultPkg.substring(0, lastDot) + ".mapper";
            } else {
                defaultPkg += ".mapper";
            }
        }
        tfMapperPackage = new JBTextField(defaultPkg);
        destPanel.add(tfMapperPackage, dgbc);

        dgbc.gridx = 2;
        dgbc.weightx = 0;
        JButton btnBrowseMapperPkg = new JButton("Browse...");
        btnBrowseMapperPkg.addActionListener(e -> {
            String pkg = ClassChooserUtil.choosePackage(project, "Choose Mapper Package", tfMapperPackage.getText());
            if (pkg != null) tfMapperPackage.setText(pkg);
        });
        destPanel.add(btnBrowseMapperPkg, dgbc);

        // Existing Mapper options
        dgbc.gridx = 0;
        dgbc.gridy = 3;
        dgbc.gridwidth = 3;
        destPanel.add(rbExistingMapper, dgbc);

        dgbc.gridy = 4;
        dgbc.gridwidth = 1;
        dgbc.weightx = 0;
        destPanel.add(new JBLabel("Target Class:"), dgbc);

        dgbc.gridx = 1;
        dgbc.weightx = 1.0;
        tfExistingMapperClass = new JBTextField();
        tfExistingMapperClass.setEnabled(false);
        destPanel.add(tfExistingMapperClass, dgbc);

        dgbc.gridx = 2;
        dgbc.weightx = 0;
        JButton btnBrowseExisting = new JButton("Browse...");
        btnBrowseExisting.setEnabled(false);
        btnBrowseExisting.addActionListener(e -> {
            PsiClass chosen = ClassChooserUtil.chooseClass(project, "Choose Existing Mapper Class", existingMapperClass);
            if (chosen != null) {
                existingMapperClass = chosen;
                tfExistingMapperClass.setText(chosen.getQualifiedName());
            }
        });
        destPanel.add(btnBrowseExisting, dgbc);

        rbNewMapper.addActionListener(e -> {
            tfMapperClassName.setEnabled(true);
            tfMapperPackage.setEnabled(true);
            btnBrowseMapperPkg.setEnabled(true);
            tfExistingMapperClass.setEnabled(false);
            btnBrowseExisting.setEnabled(false);
        });

        rbExistingMapper.addActionListener(e -> {
            tfMapperClassName.setEnabled(false);
            tfMapperPackage.setEnabled(false);
            btnBrowseMapperPkg.setEnabled(false);
            tfExistingMapperClass.setEnabled(true);
            btnBrowseExisting.setEnabled(true);
        });

        // Method options panel
        JPanel methodsPanel = new JPanel(new GridLayout(2, 3, 5, 2));
        methodsPanel.setBorder(BorderFactory.createTitledBorder("Methods to Generate"));

        cbForwardSingle = new JBCheckBox("Source -> Target", true);
        cbForwardList = new JBCheckBox("List<Source> -> List<Target>", true);
        cbReverseSingle = new JBCheckBox("Target -> Source", true);
        cbReverseList = new JBCheckBox("List<Target> -> List<Source>", true);
        cbMerge = new JBCheckBox("Merge Method (mergeEntity)", true);

        methodsPanel.add(cbForwardSingle);
        methodsPanel.add(cbForwardList);
        methodsPanel.add(cbMerge);
        methodsPanel.add(cbReverseSingle);
        methodsPanel.add(cbReverseList);

        // Naming style
        JPanel stylePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        stylePanel.add(new JBLabel("Method Naming Style:"));
        comboNamingStyle = new ComboBox<>(MethodNamingStyle.values());
        comboNamingStyle.setSelectedItem(MethodNamingStyle.CONVERT_X_TO_Y);
        stylePanel.add(comboNamingStyle);

        JPanel configHeaderPanel = new JPanel(new BorderLayout());
        configHeaderPanel.add(topPanel, BorderLayout.NORTH);
        configHeaderPanel.add(destPanel, BorderLayout.CENTER);

        JPanel optionsCombined = new JPanel(new BorderLayout());
        optionsCombined.add(methodsPanel, BorderLayout.CENTER);
        optionsCombined.add(stylePanel, BorderLayout.SOUTH);
        configHeaderPanel.add(optionsCombined, BorderLayout.SOUTH);

        mainPanel.add(configHeaderPanel, BorderLayout.NORTH);

        // Center: Field Mapping Table
        tableModel = new MappingTableModel(mappingItems);
        mappingTable = new JBTable(tableModel);
        setupTableEditors();

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Field Mappings (Click to edit Target Field, Target Type, or Conversion)"));

        JPanel tableToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JButton btnSelectAll = new JButton("Select All");
        btnSelectAll.addActionListener(e -> setAllMappingSelected(true));

        JButton btnDeselectAll = new JButton("Deselect All");
        btnDeselectAll.addActionListener(e -> setAllMappingSelected(false));

        tableToolbar.add(btnSelectAll);
        tableToolbar.add(btnDeselectAll);

        tablePanel.add(tableToolbar, BorderLayout.NORTH);
        tablePanel.add(new JScrollPane(mappingTable), BorderLayout.CENTER);

        mainPanel.add(tablePanel, BorderLayout.CENTER);

        return mainPanel;
    }

    private void setupTableEditors() {
        mappingTable.getColumnModel().getColumn(0).setMaxWidth(40);
        mappingTable.getColumnModel().getColumn(0).setPreferredWidth(40);

        // Column 4: Target Type ComboBox Editor
        JComboBox<String> typeComboBox = new JComboBox<>(TypeConversionHelper.COMMON_TYPES);
        typeComboBox.setEditable(true);
        TableColumn typeColumn = mappingTable.getColumnModel().getColumn(4);
        typeColumn.setCellEditor(new DefaultCellEditor(typeComboBox));

        // Column 5: Conversion ComboBox Editor
        JComboBox<ConversionKind> conversionComboBox = new JComboBox<>(ConversionKind.values());
        TableColumn convColumn = mappingTable.getColumnModel().getColumn(5);
        convColumn.setCellEditor(new DefaultCellEditor(conversionComboBox));
    }

    private void refreshMappings() {
        initMappingList();
        if (tableModel != null) {
            tableModel.fireTableDataChanged();
        }
        if (sourceClass != null && rbNewMapper.isSelected()) {
            tfMapperClassName.setText(sourceClass.getName() + "Mapper");
        }
    }

    private void setAllMappingSelected(boolean selected) {
        for (FieldMappingItem item : mappingItems) {
            item.setSelected(selected);
        }
        tableModel.fireTableDataChanged();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (sourceClass == null) {
            return new ValidationInfo("Source class is required", tfSourceClass);
        }

        String targetName = tfTargetClass.getText().trim();
        if (targetName.isEmpty()) {
            return new ValidationInfo("Target class (DTO) is required", tfTargetClass);
        }

        if (rbNewMapper.isSelected()) {
            String name = tfMapperClassName.getText().trim();
            if (name.isEmpty() || !name.matches("^[A-Za-z_$][A-Za-z0-9_$]*$")) {
                return new ValidationInfo("Invalid Mapper class name: " + name, tfMapperClassName);
            }
        } else {
            if (existingMapperClass == null) {
                return new ValidationInfo("Please select an existing Mapper class", tfExistingMapperClass);
            }
        }

        boolean hasSelectedMethod = cbForwardSingle.isSelected() || cbForwardList.isSelected()
                || cbReverseSingle.isSelected() || cbReverseList.isSelected() || cbMerge.isSelected();
        if (!hasSelectedMethod) {
            return new ValidationInfo("Please select at least one method to generate");
        }

        boolean hasSelectedField = mappingItems.stream().anyMatch(FieldMappingItem::isSelected);
        if (!hasSelectedField) {
            return new ValidationInfo("Please select at least one field mapping", mappingTable);
        }

        return null;
    }

    public PsiClass getSourceClass() {
        return sourceClass;
    }

    public PsiClass getTargetClass() {
        return targetClass;
    }

    public String getTargetClassQualifiedName() {
        return tfTargetClass.getText().trim();
    }

    public boolean isCreateDto() {
        return cbCreateDto.isSelected();
    }

    public DtoStyle getDtoStyle() {
        return (DtoStyle) comboDtoStyle.getSelectedItem();
    }

    public String getTargetPackageName() {
        String qName = getTargetClassQualifiedName();
        int dot = qName.lastIndexOf('.');
        return dot >= 0 ? qName.substring(0, dot) : "";
    }

    public String getTargetSimpleClassName() {
        String qName = getTargetClassQualifiedName();
        int dot = qName.lastIndexOf('.');
        return dot >= 0 ? qName.substring(dot + 1) : qName;
    }

    public boolean isNewMapper() {
        return rbNewMapper.isSelected();
    }

    public String getMapperClassName() {
        return tfMapperClassName.getText().trim();
    }

    public String getMapperPackageName() {
        return tfMapperPackage.getText().trim();
    }

    public PsiClass getExistingMapperClass() {
        return existingMapperClass;
    }

    public List<FieldMappingItem> getMappingItems() {
        return mappingItems;
    }

    public MapperOptions getMapperOptions() {
        MapperOptions options = new MapperOptions();
        options.generateForwardSingle = cbForwardSingle.isSelected();
        options.generateForwardList = cbForwardList.isSelected();
        options.generateReverseSingle = cbReverseSingle.isSelected();
        options.generateReverseList = cbReverseList.isSelected();
        options.generateMerge = cbMerge.isSelected();
        options.namingStyle = (MethodNamingStyle) comboNamingStyle.getSelectedItem();
        options.isStaticMethods = true;
        return options;
    }

    public PsiDirectory getOrCreateDtoTargetDirectory() {
        PsiFile containingFile = sourceClass.getContainingFile();
        PsiDirectory currentDir = containingFile != null ? containingFile.getContainingDirectory() : null;
        if (currentDir == null) {
            return null;
        }

        String targetPkg = getTargetPackageName();
        String currentPkg = PsiClassUtil.getPackageName(sourceClass);

        if (targetPkg.equals(currentPkg)) {
            return currentDir;
        }

        PsiDirectory sourceRootDir = findSourceRootDir(currentDir, currentPkg);
        final PsiDirectory finalRoot = sourceRootDir != null ? sourceRootDir : currentDir;

        return WriteCommandAction.writeCommandAction(project)
                .withName("Create DTO Package Directory")
                .compute(() -> createPackageDirectories(finalRoot, targetPkg));
    }

    public PsiDirectory getOrCreateTargetDirectory() {
        if (!isNewMapper()) {
            return null;
        }

        PsiFile containingFile = sourceClass.getContainingFile();
        PsiDirectory currentDir = containingFile != null ? containingFile.getContainingDirectory() : null;
        if (currentDir == null) {
            return null;
        }

        String targetPkg = getMapperPackageName();
        String currentPkg = PsiClassUtil.getPackageName(sourceClass);

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

    private static class MappingTableModel extends AbstractTableModel {
        private final String[] columns = {"", "Source Field", "Source Type", "Target Field", "Target Type", "Conversion"};
        private final List<FieldMappingItem> items;

        public MappingTableModel(List<FieldMappingItem> items) {
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
            switch (columnIndex) {
                case 0:
                    return Boolean.class;
                case 5:
                    return ConversionKind.class;
                default:
                    return String.class;
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0 || columnIndex == 3 || columnIndex == 4 || columnIndex == 5;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            FieldMappingItem item = items.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return item.isSelected();
                case 1:
                    return item.getSourceFieldName();
                case 2:
                    return item.getSourceFieldTypeText();
                case 3:
                    return item.getTargetFieldName();
                case 4:
                    return item.getTargetFieldTypeText();
                case 5:
                    return item.getForwardConversion();
                default:
                    return null;
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            FieldMappingItem item = items.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    if (aValue instanceof Boolean) {
                        item.setSelected((Boolean) aValue);
                        fireTableCellUpdated(rowIndex, columnIndex);
                    }
                    break;
                case 3:
                    if (aValue != null) {
                        item.setTargetFieldName(aValue.toString().trim());
                        fireTableCellUpdated(rowIndex, columnIndex);
                    }
                    break;
                case 4:
                    if (aValue != null) {
                        item.setTargetFieldTypeText(aValue.toString().trim());
                        fireTableCellUpdated(rowIndex, 4);
                        fireTableCellUpdated(rowIndex, 5);
                    }
                    break;
                case 5:
                    if (aValue instanceof ConversionKind) {
                        item.setForwardConversion((ConversionKind) aValue);
                        fireTableCellUpdated(rowIndex, columnIndex);
                    }
                    break;
            }
        }
    }
}
