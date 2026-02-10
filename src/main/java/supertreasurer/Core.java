package supertreasurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javafx.scene.Parent;
import javafx.scene.control.TabPane;

import supertreasurer.tools.ToolModule;
import supertreasurer.tools.pdfeditor.PdfEditorTool;
import supertreasurer.tools.BudgetTool.BudgetTool;

public class Core {

    public List<ToolModule> tools() {
        return List.of(
            new PdfEditorTool(),
            new BudgetTool()
        );
    }

    public Parent buildMainView(Path appDataDir) throws IOException {
        TabPane tabs = new TabPane();

        for (ToolModule tool : tools()) {
            Path toolDir = appDataDir.resolve(tool.id());
            Files.createDirectories(toolDir);
            tabs.getTabs().add(tool.createTab(toolDir));
        }

        return tabs;
    }
}
