package supertreasurer.tools.pdfeditor;

import java.awt.ScrollPane;
import java.nio.file.Path;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;
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
        Button import_pdf_btn = new Button("Import PDF");
        Label attachedPathLabel = new Label("No file selected");
        final Path[] selectedFile = new Path[1];
    
        import_pdf_btn.setOnAction(e -> {
            Window w = import_pdf_btn.getScene().getWindow();
            FileChooser fc = new FileChooser();
            fc.setTitle("Select invoice");
            java.io.File f = fc.showOpenDialog(w);
            if (f != null) {
                selectedFile[0] = f.toPath();
                attachedPathLabel.setText(f.getAbsolutePath());
            }
        });

        PDDocument pdfDocument = PDDocument.load(selectedFile[0].toFile());
        PDFRenderer pdfRenderer = new PDFRenderer(pdfDocument);
        bufferedImage bim = pdfRenderer.renderImageWithDPI(0, 300, ImageType.RGB);
        SplitPane visualisateur_et_editeur = new SplitPane();

        ScrollPane visualisateur = new ScrollPane();
        ImageView imageView = new ImageView(SwingFXUtils.toFXImage(bim, null));
        visualisateur.setContent(imageView);

        visualisateur_et_editeur.getItems().add(visualisateur);

        pattern_creator.setContent(visualisateur_et_editeur);
        pdf_tabs.getTabs().add(pattern_creator);

        tab.setContent(pdf_tabs);
        return tab;
    }
}

