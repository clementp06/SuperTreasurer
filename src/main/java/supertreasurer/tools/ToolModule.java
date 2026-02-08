package supertreasurer.tools;

import java.nio.file.Path;
import javafx.scene.control.Tab;


public interface ToolModule {
    String id();
    String displayName();
    Tab createTab(Path toolDataDir);
}
