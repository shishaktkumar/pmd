/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

import org.fxmisc.richtext.LineNumberFactory;

import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.util.fxdesigner.model.ASTManager;
import net.sourceforge.pmd.util.fxdesigner.model.ParseAbortedException;
import net.sourceforge.pmd.util.fxdesigner.util.codearea.AvailableSyntaxHighlighters;
import net.sourceforge.pmd.util.fxdesigner.util.codearea.CustomCodeArea;
import net.sourceforge.pmd.util.fxdesigner.util.codearea.SyntaxHighlighter;
import net.sourceforge.pmd.util.fxdesigner.util.controls.ASTTreeCell;
import net.sourceforge.pmd.util.fxdesigner.util.controls.ASTTreeItem;
import net.sourceforge.pmd.util.fxdesigner.util.settings.AppSetting;
import net.sourceforge.pmd.util.fxdesigner.util.settings.SettingsOwner;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionModel;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

/**
 * One editor, i.e. source editor and ast tree view.
 *
 * @author Clément Fournier
 * @since 6.0.0
 */
public class SourceEditorController implements Initializable, SettingsOwner {

    private final DesignerApp designerApp;
    @FXML
    private Label astTitleLabel;
    @FXML
    private TreeView<Node> astTreeView;
    @FXML
    private MenuItem toggleSyntaxHighlighting;
    @FXML
    private CustomCodeArea codeEditorArea;
    private ObjectProperty<Node> selectedNode = new SimpleObjectProperty<>();
    private BooleanProperty isSyntaxHighlightingEnabled = new SimpleBooleanProperty(true);
    private ASTManager astManager;


    public SourceEditorController(DesignerApp owner) {
        this.designerApp = owner;
        astManager = new ASTManager(designerApp);
    }


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeSyntaxHighlighting();
        initializeASTTreeView();

        codeEditorArea.setParagraphGraphicFactory(LineNumberFactory.get(codeEditorArea));
    }


    private void initializeSyntaxHighlighting() {

        isSyntaxHighlightingEnabled.bind(codeEditorArea.syntaxHighlightingEnabledProperty());
        toggleSyntaxHighlighting.setOnAction(e -> {
            isSyntaxHighlightingEnabled.set(!isSyntaxHighlightingEnabled.get());
            toggleSyntaxHighlighting.setText((isSyntaxHighlightingEnabled.get() ? "Disable" : "Enable")
                                                 + " syntax highlighting");
        });

        isSyntaxHighlightingEnabled.addListener(((observable, wasEnabled, isEnabled) -> {
            if (!wasEnabled && isEnabled) {
                updateSyntaxHighlighter();
            } else if (!isEnabled) {
                codeEditorArea.disableSyntaxHighlighting();
            }
        }));

        astManager.languageVersionProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                updateSyntaxHighlighter();
            }
        });

    }


    private void initializeASTTreeView() {
        astTreeView.setCellFactory(param -> new ASTTreeCell());

        ReadOnlyObjectProperty<TreeItem<Node>> selectedItemProperty
            = astTreeView.getSelectionModel().selectedItemProperty();


        //        astTreeView.rootProperty().addListener((obs, oldRoot, newRoot) -> {
        //            if (newRoot == null) {
        //                nodeInfoPanelController.invalidateInfo();
        //            }
        //        });

        ObjectBinding<Node> selectedBinding
            = Bindings.createObjectBinding(() -> {
                                               TreeItem<Node> selected = astTreeView.getSelectionModel().getSelectedItem();
                                               return selected == null ? null : selected.getValue();
                                           },
                                           selectedItemProperty);
        selectedNode.bind(selectedBinding);

//
//        selectedItemProperty.addListener(observable -> {
//            nodeInfoPanelController.invalidateInfo();
//        });
//
//        selectedItemProperty.addListener((observable, oldValue, newValue) -> {
//            if (newValue != null && newValue != oldValue) {
//                onNodeItemSelected(newValue.getValue());
//            } else if (newValue == null) {
//                nodeInfoPanelController.invalidateInfo();
//            }
//        });
    }


    /**
     * Refreshes the AST.
     */
    public void refreshAST() {
        String source = codeEditorArea.getText();
        Node current;
        try {
            current = astManager.updateCompilationUnit(source);
        } catch (ParseAbortedException e) {
            invalidateAST(true);
            return;
        }

        setUpToDateCompilationUnit(current);
        codeEditorArea.clearPrimaryStyleLayer();

    }


    private void setUpToDateCompilationUnit(Node node) {
        astTitleLabel.setText("Abstract Syntax Tree");
        ASTTreeItem root = ASTTreeItem.getRoot(node);
        astTreeView.setRoot(root);
    }


    public void shutdown() {
        codeEditorArea.disableSyntaxHighlighting();
    }


    private void updateSyntaxHighlighter() {
        SyntaxHighlighter computer = AvailableSyntaxHighlighters.getComputerForLanguage(astManager.getLanguageVersion().getLanguage());
        if (computer != null) {
            codeEditorArea.setSyntaxHighlightingEnabled(computer);
        } else {
            codeEditorArea.disableSyntaxHighlighting();
        }
    }


    public void highlightNode(Node node) {
        if (codeEditorArea.isInRange(node)) {
            codeEditorArea.restylePrimaryStyleLayer(node, Collections.singleton("primary-highlight"));
            codeEditorArea.paintCss();
            codeEditorArea.positionCaret(node.getBeginLine(), node.getBeginColumn());
        } else {
            codeEditorArea.clearPrimaryStyleLayer();
        }
    }


    public void focusNodeInTreeView(Node node) {
        ASTTreeItem found = ((ASTTreeItem) astTreeView.getRoot()).findItem(node);
        if (found != null) {
            SelectionModel<TreeItem<Node>> selectionModel = astTreeView.getSelectionModel();
            selectionModel.select(found);
            astTreeView.getFocusModel().focus(selectionModel.getSelectedIndex());
            // astTreeView.scrollTo(selectionModel.getSelectedIndex());
        }
    }


    private void invalidateAST(boolean error) {
        astTitleLabel.setText("Abstract syntax tree (" + (error ? "error" : "outdated") + ")");
    }


    public boolean isSyntaxHighlightingEnabled() {
        return isSyntaxHighlightingEnabled.get();
    }


    public ReadOnlyBooleanProperty syntaxHighlightingEnabledProperty() {
        return isSyntaxHighlightingEnabled;
    }


    public ObservableValue<String> sourceCodeProperty() {
        return codeEditorArea.textProperty();
    }


    public LanguageVersion getLanguageVersion() {
        return astManager.getLanguageVersion();
    }


    public ObjectProperty<LanguageVersion> languageVersionProperty() {
        return astManager.languageVersionProperty();
    }


    public Node getCompilationUnit() {
        return astManager.updateCompilationUnit();
    }


    public ObjectProperty<Node> commpilationUnitProperty() {
        return astManager.compilationUnitProperty();
    }


    public void replaceText(String source) {
        codeEditorArea.replaceText(source);
    }


    public void clearStyleLayers() {
        codeEditorArea.clearStyleLayers();
    }


    @Override
    public List<AppSetting> getSettings() {
        List<AppSetting> settings = new ArrayList<>();
        settings.add(new AppSetting("langVersion", () -> getLanguageVersion().getTerseName(),
                                    this::restoreLanguageVersion));

        settings.add(new AppSetting("code", () -> codeEditorArea.getText(),
                                    (e) -> codeEditorArea.replaceText(e)));

        return settings;
    }


    private void restoreLanguageVersion(String name) {
        LanguageVersion version = LanguageRegistry.findLanguageVersionByTerseName(name);
        if (version != null) {
            astManager.languageVersionProperty().setValue(version);
        }
    }


    public ObjectProperty<Node> selectedNodeProperty() {
        return selectedNode;
    }
}
