package supertreasurer.tools.pdfeditor;

import javafx.scene.control.ScrollPane; // ✅ JavaFX
import java.nio.file.Path;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import supertreasurer.tools.ToolModule;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import java.awt.image.BufferedImage;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.ImageView;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.VBox;
import java.io.IOException;
import java.util.ArrayList;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.scene.layout.Pane;
import javafx.scene.control.TextField;
import javafx.collections.FXCollections;





public class PdfEditorTool implements ToolModule {
    private PDDocument pdfDocument;
    private PDFRenderer pdfRenderer;
    private BufferedImage bim;
    private ImageView imageView;
    private StackPane pdfStack;
    private VBox editor_box= new VBox();
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
        void updatePosition(int x, int y) {
            this.x = x;
            this.y = y;
        }
        void updateSize(int width, int height) {
            this.width = width;
            this.height = height;
        }
        void updateType(String type) {
            this.type = type;
        }
        void updateName(String name) {
            this.name = name;
        }
    }
    private class Balise_Modifier {
        Balise balise;
        TextField nameField;
        ComboBox<String> typeComboBox;
        TextField xField;
        TextField yField;
        TextField widthField;
        TextField heightField;

        Balise_Modifier(Balise balise) {
            this.balise = balise;
            this.nameField = new TextField(balise.name);
            this.typeComboBox = new ComboBox<String>(FXCollections.observableArrayList("TEXT", "IMAGE"));
            this.xField = new TextField(String.valueOf(balise.x));
            this.yField = new TextField(String.valueOf(balise.y));
            this.widthField = new TextField(String.valueOf(balise.width));
            this.heightField = new TextField(String.valueOf(balise.height));
            void show() {
                editor_box.getChildren().clear();
                editor_box.getChildren().addAll(
                    nameField,
                    typeComboBox,
                    xField,
                    yField,
                    widthField,
                    heightField
                );
            }
             void save() {
                balise.updateName(nameField.getText());
                balise.updateType(typeComboBox.getValue());
                balise.updatePosition(Integer.parseInt(xField.getText()), Integer.parseInt(yField.getText()));
                balise.updateSize(Integer.parseInt(widthField.getText()), Integer.parseInt(heightField.getText()));
            }
        }
    private ArrayList<Balise> balises = new ArrayList<>();
    private Pane overlay;

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
        pdfStack = new StackPane();
        visualisateur.setContent(pdfStack);

    
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
            imageView.setOnMouseClicked(this::onPdfClicked);
            imageView.setPreserveRatio(true);
            imageView.setFitWidth(visualisateur.getWidth());
            pdfStack.getChildren().clear();

            overlay = new Pane();
            overlay.setPickOnBounds(false);
            overlay.setMouseTransparent(true);

            pdfStack.getChildren().addAll(imageView, overlay);

            }
            } catch (IOException g) {
            }
        });
        
        // Fin de la création du tab de pattern
        //Début de la création du tab d'édition

        editor_box.getChildren().add(new Label("Edit Template"));
        editor_box.getChildren().add(import_pdf_btn);
        Button Modify_balise_btn = new Button("Modify Balise");
        Modify_balise_btn.setOnAction(e -> {
            // Logique de modification de balise
            for (Balise b : balises) {
                Balise_Modifier bm = new Balise_Modifier(b);
                bm.show();
                bm.save();
                overlay.getChildren().clear();
                drawBalisePreview(b);
            }
        });
        //Fin de la création du tab d'édition
        SplitPane visualisateur_et_editeur = new SplitPane();
        visualisateur_et_editeur.setDividerPositions(0.8);
        visualisateur_et_editeur.getItems().addAll(visualisateur,editor_box);

        pattern_creator.setContent(visualisateur_et_editeur);
        pdf_tabs.getTabs().add(pattern_creator);

        tab.setContent(pdf_tabs);
        return tab;
    }
    private void onPdfClicked(MouseEvent event) {
        if (imageView == null) return;

        double clickX = event.getX();
        double clickY = event.getY();

        System.out.println("Click at image coords: " + clickX + ", " + clickY);

        // Création d'une balise temporaire (test)
        double[] pdfCoords = imageToPdfCoords(clickX, clickY);
        Balise b = new Balise("TEXT", "balise_test",
                (int) pdfCoords[0],
                (int) pdfCoords[1],
                100,
                20);

        balises.add(b);

        drawBalisePreview(b);
    }
    private void drawBalisePreview(Balise b) {
        if (overlay == null) return;

        double[] imgCoords = pdfToImageCoords(b.x, b.y);

        Rectangle r = new Rectangle(b.width, b.height);
        r.setStroke(Color.RED);
        r.setFill(Color.TRANSPARENT);
        r.setStrokeWidth(2);

        r.setLayoutX(imgCoords[0]);
        r.setLayoutY(imgCoords[1]);

        overlay.getChildren().add(r);
    }

    private double[] imageToPdfCoords(double xImg, double yImg) {
        PDPage page = pdfDocument.getPage(0);

        double pdfWidth  = page.getMediaBox().getWidth();
        double pdfHeight = page.getMediaBox().getHeight();

        double viewW = imageView.getBoundsInLocal().getWidth();
        double viewH = imageView.getBoundsInLocal().getHeight();

        if (viewW <= 0 || viewH <= 0) return new double[]{0, 0};

        double pdfX = xImg * pdfWidth / viewW;
        double pdfY = pdfHeight - (yImg * pdfHeight / viewH);

        return new double[]{pdfX, pdfY};
    }

    private double[] pdfToImageCoords(double xPdf, double yPdf) {
        PDPage page = pdfDocument.getPage(0);

        double pdfWidth  = page.getMediaBox().getWidth();
        double pdfHeight = page.getMediaBox().getHeight();

        double viewW = imageView.getBoundsInLocal().getWidth();
        double viewH = imageView.getBoundsInLocal().getHeight();

        if (viewW <= 0 || viewH <= 0) return new double[]{0, 0};

        double imgX = xPdf * viewW / pdfWidth;
        double imgY = (pdfHeight - yPdf) * viewH / pdfHeight;

        return new double[]{imgX, imgY};
    }
}

