package supertreasurer.tools.pdfeditor;

import java.nio.file.Path;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import supertreasurer.tools.ToolModule;

public class DummyTool implements ToolModule {
    @Override
    public String id() {
        return "dummytool";
    }
    @Override
    public String displayName() {
        return "Dummy Tool";
    }
    @Override
    public Tab createTab(Path toolDataDir) {
        Tab tab = new Tab(displayName());
        tab.setContent(new Label("This is a dummy tool (data dir: " + toolDataDir + ")"));
        tab.setClosable(false);
        return tab;
    }
}