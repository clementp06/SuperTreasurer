package supertreasurer.tools.pdfeditor;

import javafx.scene.control.ScrollPane; // ✅ JavaFX
import java.nio.file.Path;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import supertreasurer.tools.ToolModule;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import java.awt.image.BufferedImage;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.ImageView;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.VBox;
import java.io.IOException;


public class PdfEditorTool implements ToolModule {
    private PDDocument pdfDocument;
    private PDFRenderer pdfRenderer;
    private BufferedImage bim;
    private ImageView imageView;

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
        Button import_pdf_btn = new Button("Import PDF");
        Label attachedPathLabel = new Label("No file selected");
        final Path[] selectedFile = new Path[1];
        ScrollPane visualisateur = new ScrollPane();
        visualisateur.setFitToWidth(true);
        visualisateur.setFitToHeight(true);

    
        import_pdf_btn.setOnAction(e -> {
            Window w = import_pdf_btn.getScene().getWindow();
            FileChooser fc = new FileChooser();
            fc.setTitle("Select invoice");
            java.io.File f = fc.showOpenDialog(w);
            if (f != null) {
                selectedFile[0] = f.toPath();
                attachedPathLabel.setText(f.getAbsolutePath());
            }
            try {
            if (selectedFile[0] != null) {
            pdfDocument = PDDocument.load(selectedFile[0].toFile());
            pdfRenderer = new PDFRenderer(pdfDocument);
            bim = pdfRenderer.renderImageWithDPI(0, 300, ImageType.RGB);
            imageView = new ImageView(SwingFXUtils.toFXImage(bim, null));
            imageView.setPreserveRatio(true);
            imageView.setFitWidth(visualisateur.getWidth());
            visualisateur.setContent(imageView);
            }
            } catch (IOException g) {
            }
        });
        
        // Fin de la création du tab de pattern
        //Début de la création du tab d'édition
        VBox editor_box= new VBox();
        editor_box.getChildren().add(new Label("Edit Template"));
        editor_box.getChildren().add(import_pdf_btn);
        //Fin de la création du tab d'édition
        SplitPane visualisateur_et_editeur = new SplitPane();
        visualisateur_et_editeur.setDividerPositions(0.8);
        visualisateur_et_editeur.getItems().addAll(visualisateur,editor_box);

        pattern_creator.setContent(visualisateur_et_editeur);
        pdf_tabs.getTabs().add(pattern_creator);

        tab.setContent(pdf_tabs);
        return tab;
    }
    private class Balise {
        String type;
        String name;
        int x;
        int y;
        int width;
        int height;
        Balise(String type, String name, int x, int y, int width, int height) {
            this.type = type;
            this.name = name;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}

