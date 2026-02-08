package supertreasurer;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javafx.util.Duration;

public class MainApp extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        Stage splashStage = new Stage(StageStyle.UNDECORATED);
        Scene splashScene = createSplashScene();
        splashStage.setTitle("SuperTreasurer");
        splashStage.setScene(splashScene);
        splashStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/logo.png")));
        splashStage.show();

        PauseTransition wait = new PauseTransition(Duration.seconds(4));
        wait.setOnFinished(event -> {
            try {
                Scene mainScene = createMainScene();
                primaryStage.setScene(mainScene);
                primaryStage.setTitle("SuperTreasurer");
                primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/logo.png")));
                splashStage.close();
                primaryStage.show();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        wait.play();
    }

    private Scene createSplashScene() {
        Image image = new Image(getClass().getResourceAsStream("/images/splash.png"));
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setFitWidth(600);

        StackPane root = new StackPane(view);
        return new Scene(root, 600, 600);
    }

    private Scene createMainScene() throws IOException {
        Path appDataDir = resolveAppDataDir();
        Core core = new Core();
        return new Scene(core.buildMainView(appDataDir), 1000, 700);
    }

    private Path resolveAppDataDir() {
        return Paths.get("data");
    }
}

