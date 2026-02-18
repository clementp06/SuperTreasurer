package supertreasurer.tools.pdfeditor;

import javafx.scene.control.ScrollPane; // ✅ JavaFX

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
import java.util.regex.Matcher;

import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.scene.layout.Pane;
import javafx.scene.control.TextField;
import javafx.collections.FXCollections;
import javafx.scene.control.Separator;
import java.nio.file.Files;

import java.util.regex.Pattern;





public class PdfEditorTool implements ToolModule {
    private PDDocument pdfDocument;
    private PDFRenderer pdfRenderer;
    private BufferedImage bim;
    private ImageView imageView;
    private StackPane pdfStack;
    private VBox editor_box= new VBox();
    private ArrayList<Balise> balises = new ArrayList<>();
    private Pane overlay;
    private VBox TemplateEditionBox = new VBox();
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
        Label nameLabel;
        ComboBox<String> typeComboBox;
        Label typeLabel;
        TextField xField;
        Label xLabel;
        TextField yField;
        Label yLabel;
        TextField widthField;
        Label widthLabel;
        TextField heightField;
        Label heightLabel;
        Button saveButton;
        Button deleteButton;
        Separator separator;

        Balise_Modifier(Balise balise) {
            this.balise = balise;
            this.nameField = new TextField(balise.name);
            this.typeComboBox = new ComboBox<String>(FXCollections.observableArrayList("TEXT", "IMAGE"));
            this.xField = new TextField(String.valueOf(balise.x));
            this.yField = new TextField(String.valueOf(balise.y));
            this.widthField = new TextField(String.valueOf(balise.width));
            this.heightField = new TextField(String.valueOf(balise.height));
            this.nameLabel = new Label("Name:");
            this.typeLabel = new Label("Type:");
            this.xLabel = new Label("X:");
            this.yLabel = new Label("Y:");
            this.widthLabel = new Label("Width:");
            this.heightLabel = new Label("Height:");
            this.saveButton = new Button("Save");
            saveButton.setOnAction(e -> save());
            this.deleteButton = new Button("Delete");
            deleteButton.setOnAction(e -> delete());
            this.separator = new Separator();
        }
        void show() {
            editor_box.getChildren().addAll(
                nameLabel,
                nameField,
                typeLabel,
                typeComboBox,
                xLabel,
                xField,
                yLabel,
                yField,
                widthLabel,
                widthField,
                heightLabel,
                heightField,
                saveButton,
                deleteButton,
                separator
            );
        }
        void save() {
            balise.updateName(nameField.getText());
            balise.updateType(typeComboBox.getValue());
            balise.updatePosition(Integer.parseInt(xField.getText()), Integer.parseInt(yField.getText()));
            balise.updateSize(Integer.parseInt(widthField.getText()), Integer.parseInt(heightField.getText()));
            overlay.getChildren().clear();
            for (Balise b : balises) {
                drawBalisePreview(b);
            }
        };
        void delete() {
            balises.remove(balise);
            editor_box.getChildren().remove(nameLabel);
            editor_box.getChildren().remove(nameField);
            editor_box.getChildren().remove(typeLabel);
            editor_box.getChildren().remove(typeComboBox);
            editor_box.getChildren().remove(xLabel);
            editor_box.getChildren().remove(xField);
            editor_box.getChildren().remove(yLabel);
            editor_box.getChildren().remove(yField);
            editor_box.getChildren().remove(widthLabel);
            editor_box.getChildren().remove(widthField);
            editor_box.getChildren().remove(heightLabel);
            editor_box.getChildren().remove(heightField);
            editor_box.getChildren().remove(separator);
            editor_box.getChildren().remove(saveButton);
            editor_box.getChildren().remove(deleteButton);
            overlay.getChildren().clear();
            for (Balise b : balises) {
                drawBalisePreview(b);
            }
        }
        }
    private class Template {
        String name;
        Path folderPath;
        Path pdfPath;
        ArrayList<Balise> balises = new ArrayList<>();

        Template(String templateName) throws IOException {
            this.name = templateName;
            this.folderPath = Path.of("data", "pdfeditor", "templates", templateName);
            this.pdfPath = folderPath.resolve("template.pdf");

            loadBalisesFromJson();
        }

        private void loadBalisesFromJson() throws IOException {
            Path jsonPath = folderPath.resolve("template.json");
            if (!Files.exists(jsonPath)) return;

            String json = Files.readString(jsonPath, StandardCharsets.UTF_8);

            Pattern blockPattern = Pattern.compile("\\{[^\\{\\}]*\"type\"[^\\{\\}]*\\}");
            Pattern typePattern  = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]*)\"");
            Pattern namePattern  = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]*)\"");
            Pattern xPattern     = Pattern.compile("\"x\"\\s*:\\s*(-?\\d+)");
            Pattern yPattern     = Pattern.compile("\"y\"\\s*:\\s*(-?\\d+)");
            Pattern wPattern     = Pattern.compile("\"width\"\\s*:\\s*(-?\\d+)");
            Pattern hPattern     = Pattern.compile("\"height\"\\s*:\\s*(-?\\d+)");

            Matcher mBlock = blockPattern.matcher(json);

            while (mBlock.find()) {
                String block = mBlock.group();

                String type = matchString(typePattern, block);
                String name = matchString(namePattern, block);
                int x = matchInt(xPattern, block);
                int y = matchInt(yPattern, block);
                int w = matchInt(wPattern, block);
                int h = matchInt(hPattern, block);

                balises.add(new Balise(type, name, x, y, w, h));
            }
    }

        private String matchString(Pattern p, String text) {
            Matcher m = p.matcher(text);
            return m.find() ? m.group(1) : "";
        }

        private int matchInt(Pattern p, String text) {
            Matcher m = p.matcher(text);
            return m.find() ? Integer.parseInt(m.group(1)) : 0;
        }
    }


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
        Tab tab= new Tab("PDF Editor");
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
        Button save_pattern_btn= new Button("Save Pattern");
        TextField pattern_name_field = new TextField();
        pattern_name_field.setPromptText("Pattern Name");

        save_pattern_btn.setOnAction(e -> {
            try {
                if (selectedFile[0] != null){
                    save_pattern(selectedFile[0], pattern_name_field.getText());
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });


        editor_box.getChildren().add(new Label("Edit Template"));
        editor_box.getChildren().add(import_pdf_btn);
        editor_box.getChildren().add(pattern_name_field);
        editor_box.getChildren().add(save_pattern_btn);

        ScrollPane editor_scroll = new ScrollPane(editor_box);
        SplitPane visualisateur_et_editeur = new SplitPane();
        visualisateur_et_editeur.setDividerPositions(0.8);
        visualisateur_et_editeur.getItems().addAll(visualisateur,editor_scroll);

        pattern_creator.setContent(visualisateur_et_editeur);

        //Fin de la création du tab d'édition
        //Début du tab de remplissage de template
        Tab templates = new Tab("Templates");
        SplitPane template_filler = new SplitPane();
        ScrollPane template_scroll = new ScrollPane();
        template_scroll.setFitToWidth(true);
        template_filler.setDividerPositions(0.5);

        Button ImportTemplateBtn = new Button("Import Template");
        Button ExportTemplateBtn = new Button("Export Template");

        TemplateEditionBox.getChildren().addAll(new Label("Template Editor"),ImportTemplateBtn,ExportTemplateBtn);
        template_scroll.setContent(TemplateEditionBox);

        template_filler.getItems().add(template_scroll);
        templates.setContent(template_filler);
        //Fin du tab de remplissage de template
        pdf_tabs.getTabs().add(templates);
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
        Balise_Modifier modifier = new Balise_Modifier(b);
        modifier.show();
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
    private void save_pattern(Path selectedFile, String template_name) throws IOException {
        if (selectedFile == null) throw new IllegalArgumentException("selectedFile is null");
        if (template_name == null || template_name.isBlank()) throw new IllegalArgumentException("template_name is empty");

        String safeName = safeDirName(template_name);
        Path templateDir = Path.of("data", "pdfeditor", "templates", safeName);
        Files.createDirectories(templateDir);

        String pdfName = selectedFile.getFileName().toString();
        String ext = "";
        int dot = pdfName.lastIndexOf('.');
        if (dot >= 0) ext = pdfName.substring(dot).toLowerCase();
        if (!ext.equals(".pdf")) ext = ".pdf";

        Path targetPdf = templateDir.resolve("template" + ext);
        Files.copy(selectedFile, targetPdf, StandardCopyOption.REPLACE_EXISTING);

        String json = buildTemplateJson(template_name, "template" + ext, balises);
        Files.writeString(templateDir.resolve("template.json"), json, StandardCharsets.UTF_8);
    }

    private String buildTemplateJson(String templateName, String pdfFileName, ArrayList<Balise> balises) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"templateName\": \"").append(jsonEscape(templateName)).append("\",\n");
        sb.append("  \"pdfFile\": \"").append(jsonEscape(pdfFileName)).append("\",\n");
        sb.append("  \"balises\": [\n");

        for (int i = 0; i < balises.size(); i++) {
            Balise b = balises.get(i);
            sb.append("    {\n");
            sb.append("      \"type\": \"").append(jsonEscape(nullToEmpty(b.type))).append("\",\n");
            sb.append("      \"name\": \"").append(jsonEscape(nullToEmpty(b.name))).append("\",\n");
            sb.append("      \"x\": ").append(b.x).append(",\n");
            sb.append("      \"y\": ").append(b.y).append(",\n");
            sb.append("      \"width\": ").append(b.width).append(",\n");
            sb.append("      \"height\": ").append(b.height).append("\n");
            sb.append("    }");
            if (i < balises.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String jsonEscape(String s) {
        String out = s;
        out = out.replace("\\", "\\\\");
        out = out.replace("\"", "\\\"");
        out = out.replace("\r", "\\r");
        out = out.replace("\n", "\\n");
        out = out.replace("\t", "\\t");
        return out;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String safeDirName(String s) {
        String out = s.trim().toLowerCase();
        out = out.replaceAll("[^a-z0-9\\-_]+", "_");
        if (out.isBlank()) out = "template";
        return out;
    }


}

