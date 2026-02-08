package supertreasurer.tools.pdfeditor;

import java.nio.file.Path;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
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
        Tab tab = new Tab(displayName());
        tab.setContent(new Label("Coming soon (data dir: " + toolDataDir + ")"));
        tab.setClosable(false);
        return tab;
    }
}

