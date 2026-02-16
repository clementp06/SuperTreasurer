package supertreasurer.tools.pdfeditor;

import java.nio.file.Path;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import supertreasurer.tools.ToolModule;

public class PdfEditorTool implements ToolModule {

    @Override
    public String id() {
        return "pdfeditor";
    }

    @Override
    public String displayName() {
        return "PDF Editor";
    }

    @Override
    public Tab createTab(Path toolDataDir) {
        Tab tab= new Tab();
        TabPane pdf_tabs = new TabPane();

        Tab pattern_creator= new Tab();
        // Début de la création du tab de pattern
        pattern_creator.setText("Pattern Creator");


        return tab;
    }
}

