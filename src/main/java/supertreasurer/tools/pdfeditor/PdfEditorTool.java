package supertreasurer.tools.pdfeditor;

import javafx.scene.control.ScrollPane;

import java.awt.Desktop;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import supertreasurer.tools.ToolModule;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;

import java.awt.image.BufferedImage;
import javafx.embed.swing.SwingFXUtils;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PdfEditorTool implements ToolModule {
    private PDDocument pdfDocument;
    private PDFRenderer pdfRenderer;
    private BufferedImage bim;
    private ImageView imageView;
    private StackPane pdfStack;
    private VBox editor_box = new VBox();
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
        void updatePosition(int x, int y) { this.x = x; this.y = y; }
        void updateSize(int width, int height) { this.width = width; this.height = height; }
        void updateType(String type) { this.type = type; }
        void updateName(String name) { this.name = name; }
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
            this.typeComboBox = new ComboBox<>(FXCollections.observableArrayList("TEXT", "IMAGE"));
            this.typeComboBox.setValue(balise.type == null || balise.type.isBlank() ? "TEXT" : balise.type);
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
                nameLabel, nameField,
                typeLabel, typeComboBox,
                xLabel, xField,
                yLabel, yField,
                widthLabel, widthField,
                heightLabel, heightField,
                saveButton, deleteButton,
                separator
            );
        }

        void save() {
            balise.updateName(nameField.getText());
            balise.updateType(typeComboBox.getValue());
            balise.updatePosition(Integer.parseInt(xField.getText()), Integer.parseInt(yField.getText()));
            balise.updateSize(Integer.parseInt(widthField.getText()), Integer.parseInt(heightField.getText()));
            if (overlay != null) {
                overlay.getChildren().clear();
                for (Balise b : balises) drawBalisePreview(b);
            }
        }

        void delete() {
            balises.remove(balise);
            editor_box.getChildren().removeAll(
                nameLabel, nameField,
                typeLabel, typeComboBox,
                xLabel, xField,
                yLabel, yField,
                widthLabel, widthField,
                heightLabel, heightField,
                saveButton, deleteButton,
                separator
            );
            if (overlay != null) {
                overlay.getChildren().clear();
                for (Balise b : balises) drawBalisePreview(b);
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
        Tab tab = new Tab("PDF Editor");
        TabPane pdf_tabs = new TabPane();

        Tab pattern_creator = new Tab();
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
                attachedPathLabel.setText("Error: " + g.getMessage());
            }
        });

        Button save_pattern_btn = new Button("Save Pattern");
        TextField pattern_name_field = new TextField();
        pattern_name_field.setPromptText("Pattern Name");

        save_pattern_btn.setOnAction(e -> {
            try {
                if (selectedFile[0] != null) {
                    save_pattern(selectedFile[0], pattern_name_field.getText());
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });

        editor_box.getChildren().clear();
        editor_box.setPadding(new Insets(10));
        editor_box.setSpacing(8);
        editor_box.getChildren().add(new Label("Edit Template"));
        editor_box.getChildren().add(import_pdf_btn);
        editor_box.getChildren().add(attachedPathLabel);
        editor_box.getChildren().add(pattern_name_field);
        editor_box.getChildren().add(save_pattern_btn);

        ScrollPane editor_scroll = new ScrollPane(editor_box);
        editor_scroll.setFitToWidth(true);

        SplitPane visualisateur_et_editeur = new SplitPane();
        visualisateur_et_editeur.setDividerPositions(0.8);
        visualisateur_et_editeur.getItems().addAll(visualisateur, editor_scroll);

        pattern_creator.setContent(visualisateur_et_editeur);
        pattern_creator.setClosable(false);

        Tab templates = new Tab("Templates");
        templates.setClosable(false);

        SplitPane template_filler = new SplitPane();
        template_filler.setDividerPositions(0.65);

        ScrollPane template_view_scroll = new ScrollPane();
        template_view_scroll.setFitToWidth(true);
        template_view_scroll.setFitToHeight(true);

        StackPane templateStack = new StackPane();
        templateStack.setAlignment(Pos.TOP_LEFT);

        ImageView templateImageView = new ImageView();
        templateImageView.setPreserveRatio(true);

        Pane templateOverlay = new Pane();
        templateOverlay.setPickOnBounds(false);
        templateOverlay.setMouseTransparent(true);

        templateStack.getChildren().addAll(templateImageView, templateOverlay);
        template_view_scroll.setContent(templateStack);

        template_view_scroll.viewportBoundsProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                double w = newV.getWidth();
                if (w > 0) templateImageView.setFitWidth(w);
            }
        });

        ScrollPane template_scroll = new ScrollPane();
        template_scroll.setFitToWidth(true);

        ComboBox<String> templateSelector = new ComboBox<>();
        templateSelector.setItems(FXCollections.observableArrayList(listTemplateNames()));
        templateSelector.setPromptText("Select template");

        Button refreshTemplatesBtn = new Button("Refresh list");
        Button ImportTemplateBtn = new Button("Load Template");
        Button SeeChangesBtn = new Button("See Changes");
        Button ExportTemplateBtn = new Button("Export PDF");
        Button OpenResultsBtn = new Button("Open results folder");

        Label templateStatus = new Label("");

        VBox fieldsBox = new VBox();
        fieldsBox.setSpacing(8);
        fieldsBox.setPadding(new Insets(8));

        ScrollPane fieldsScroll = new ScrollPane(fieldsBox);
        fieldsScroll.setFitToWidth(true);
        fieldsScroll.setPrefViewportHeight(450);

        TemplateEditionBox.getChildren().clear();
        TemplateEditionBox.setPadding(new Insets(10));
        TemplateEditionBox.setSpacing(10);

        TemplateEditionBox.getChildren().addAll(
            new Label("Template Filler"),
            new HBox(10, templateSelector, refreshTemplatesBtn),
            ImportTemplateBtn,
            new Separator(),
            new Label("Fields"),
            fieldsScroll,
            new Separator(),
            SeeChangesBtn,
            ExportTemplateBtn,
            OpenResultsBtn,
            templateStatus
        );

        template_scroll.setContent(TemplateEditionBox);

        final Template[] currentTemplate = new Template[1];
        final PDDocument[] currentTemplateDoc = new PDDocument[1];
        final BufferedImage[] currentTemplateImg = new BufferedImage[1];

        Map<Balise, TextField> textInputs = new HashMap<>();
        Map<Balise, Path> imageInputs = new HashMap<>();
        Map<Balise, Label> imageLabels = new HashMap<>();

        refreshTemplatesBtn.setOnAction(e -> {
            templateSelector.setItems(FXCollections.observableArrayList(listTemplateNames()));
            templateStatus.setText("List refreshed");
        });

        ImportTemplateBtn.setOnAction(e -> {
            String selected = templateSelector.getValue();
            if (selected == null || selected.isBlank()) {
                templateStatus.setText("Select a template first");
                return;
            }

            try {
                if (currentTemplateDoc[0] != null) {
                    try { currentTemplateDoc[0].close(); } catch (Exception ignored) {}
                    currentTemplateDoc[0] = null;
                }

                currentTemplate[0] = new Template(selected);

                if (!Files.exists(currentTemplate[0].pdfPath)) {
                    templateStatus.setText("Missing: " + currentTemplate[0].pdfPath);
                    return;
                }

                currentTemplateDoc[0] = PDDocument.load(currentTemplate[0].pdfPath.toFile());
                PDFRenderer renderer = new PDFRenderer(currentTemplateDoc[0]);
                currentTemplateImg[0] = renderer.renderImageWithDPI(0, 220, ImageType.RGB);

                templateImageView.setImage(SwingFXUtils.toFXImage(currentTemplateImg[0], null));
                templateOverlay.getChildren().clear();

                fieldsBox.getChildren().clear();
                textInputs.clear();
                imageInputs.clear();
                imageLabels.clear();

                for (Balise b : currentTemplate[0].balises) {
                    Label title = new Label(b.name + " (" + b.type + ")");
                    fieldsBox.getChildren().add(title);

                    if ("TEXT".equalsIgnoreCase(b.type)) {
                        TextField tf = new TextField();
                        tf.setPromptText("Text...");
                        textInputs.put(b, tf);
                        fieldsBox.getChildren().add(tf);
                    } else if ("IMAGE".equalsIgnoreCase(b.type)) {
                        HBox row = new HBox(10);
                        Button choose = new Button("Import image");
                        Label chosen = new Label("No image selected");
                        row.getChildren().addAll(choose, chosen);

                        imageLabels.put(b, chosen);

                        choose.setOnAction(ev -> {
                            Window w = choose.getScene().getWindow();
                            FileChooser fc = new FileChooser();
                            fc.setTitle("Select image");
                            fc.getExtensionFilters().addAll(
                                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg")
                            );
                            File f = fc.showOpenDialog(w);
                            if (f != null) {
                                imageInputs.put(b, f.toPath());
                                chosen.setText(f.getName());
                                templateStatus.setText("Image set for " + b.name);
                            }
                        });

                        fieldsBox.getChildren().add(row);
                    } else {
                        fieldsBox.getChildren().add(new Label("Unknown type"));
                    }

                    fieldsBox.getChildren().add(new Separator());
                }

                templateStatus.setText("Loaded template: " + selected);

            } catch (IOException ex) {
                templateStatus.setText("Error: " + ex.getMessage());
            }
        });

        SeeChangesBtn.setOnAction(e -> {
            if (currentTemplate[0] == null || currentTemplateDoc[0] == null || currentTemplateImg[0] == null) {
                templateStatus.setText("Load a template first");
                return;
            }

            templateOverlay.getChildren().clear();

            for (Balise b : currentTemplate[0].balises) {
                if ("TEXT".equalsIgnoreCase(b.type)) {
                    TextField tf = textInputs.get(b);
                    if (tf != null) {
                        String v = tf.getText();
                        if (v != null && !v.isBlank()) {
                            Node n = makeTextPreviewNode(currentTemplateDoc[0], currentTemplateImg[0], templateImageView, b, v);
                            if (n != null) templateOverlay.getChildren().add(n);
                        }
                    }
                } else if ("IMAGE".equalsIgnoreCase(b.type)) {
                    Path p = imageInputs.get(b);
                    if (p != null && Files.exists(p)) {
                        Node n = makeImagePreviewNode(currentTemplateDoc[0], currentTemplateImg[0], templateImageView, b, p);
                        if (n != null) templateOverlay.getChildren().add(n);
                    }
                }
            }

            templateStatus.setText("Preview updated");
        });

        ExportTemplateBtn.setOnAction(e -> {
            if (currentTemplate[0] == null) {
                templateStatus.setText("Load a template first");
                return;
            }

            try {
                Path out = exportFilledTemplate(currentTemplate[0], textInputs, imageInputs);
                templateStatus.setText("Exported: " + out.getFileName());
            } catch (IOException ex) {
                templateStatus.setText("Export error: " + ex.getMessage());
            }
        });

        OpenResultsBtn.setOnAction(e -> {
            try {
                Path results = Path.of("data", "pdfeditor", "results");
                Files.createDirectories(results);
                openFolder(results);
            } catch (IOException ex) {
                templateStatus.setText("Error: " + ex.getMessage());
            }
        });

        template_filler.getItems().addAll(template_view_scroll, template_scroll);
        templates.setContent(template_filler);

        pdf_tabs.getTabs().add(pattern_creator);
        pdf_tabs.getTabs().add(templates);

        tab.setContent(pdf_tabs);
        tab.setClosable(false);
        return tab;
    }

    private void onPdfClicked(MouseEvent event) {
        if (imageView == null) return;

        double clickX = event.getX();
        double clickY = event.getY();

        double[] pdfCoords = imageToPdfCoords(clickX, clickY);

        Balise b = new Balise("TEXT", "balise_test",
            (int) pdfCoords[0],
            (int) pdfCoords[1],
            100,
            20
        );

        balises.add(b);

        Balise_Modifier modifier = new Balise_Modifier(b);
        modifier.show();

        drawBalisePreview(b);
    }

    private void drawBalisePreview(Balise b) {
        if (overlay == null || imageView == null || pdfDocument == null) return;

        double[] imgCoords = pdfToImageCoords(b.x, b.y);

        Rectangle r = new Rectangle(scalePdfWToView(imageView, pdfDocument, b.width), scalePdfHToView(imageView, pdfDocument, b.height));
        r.setStroke(Color.RED);
        r.setFill(Color.TRANSPARENT);
        r.setStrokeWidth(2);

        r.setLayoutX(imgCoords[0]);
        r.setLayoutY(imgCoords[1]);

        overlay.getChildren().add(r);
    }

    private double[] imageToPdfCoords(double xImg, double yImg) {
        PDPage page = pdfDocument.getPage(0);

        double pdfWidth = page.getMediaBox().getWidth();
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

        double pdfWidth = page.getMediaBox().getWidth();
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

        Path targetPdf = templateDir.resolve("template.pdf");
        Files.copy(selectedFile, targetPdf, StandardCopyOption.REPLACE_EXISTING);

        String json = buildTemplateJson(template_name, "template.pdf", balises);
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

    private Node makeTextPreviewNode(PDDocument doc, BufferedImage img, ImageView iv, Balise b, String value) {
        if (doc == null || img == null || iv == null) return null;

        double[] xy = pdfToViewCoords(doc, iv, b.x, b.y);
        double fontPx = Math.max(8, scalePdfHToView(iv, doc, b.height));

        Label l = new Label(value);
        l.setMouseTransparent(true);
        l.setStyle("-fx-background-color: rgba(255,255,255,0.55); -fx-border-color: red; -fx-border-width: 1; -fx-padding: 2 4 2 4;");
        l.setFont(javafx.scene.text.Font.font(fontPx));

        l.setLayoutX(xy[0]);
        l.setLayoutY(Math.max(0, xy[1] - fontPx));

        return l;
    }

    private Node makeImagePreviewNode(PDDocument doc, BufferedImage img, ImageView iv, Balise b, Path imagePath) {
        try {
            Image fxImg = new Image(imagePath.toUri().toString());
            if (fxImg.isError()) return null;

            double[] xy = pdfToViewCoords(doc, iv, b.x, b.y);

            double w = Math.max(1, scalePdfWToView(iv, doc, b.width));
            double h = Math.max(1, scalePdfHToView(iv, doc, b.height));

            ImageView v = new ImageView(fxImg);
            v.setMouseTransparent(true);
            v.setPreserveRatio(false);
            v.setFitWidth(w);
            v.setFitHeight(h);

            v.setLayoutX(xy[0]);
            v.setLayoutY(xy[1] - h);

            v.setStyle("-fx-border-color: red; -fx-border-width: 1;");
            return v;

        } catch (Exception ex) {
            return null;
        }
    }

    private double[] pdfToViewCoords(PDDocument doc, ImageView iv, double xPdf, double yPdf) {
        PDPage page = doc.getPage(0);

        double pdfWidth = page.getMediaBox().getWidth();
        double pdfHeight = page.getMediaBox().getHeight();

        double viewW = iv.getBoundsInLocal().getWidth();
        double viewH = iv.getBoundsInLocal().getHeight();

        if (viewW <= 0 || viewH <= 0) return new double[]{0, 0};

        double x = xPdf * viewW / pdfWidth;
        double y = (pdfHeight - yPdf) * viewH / pdfHeight;

        return new double[]{x, y};
    }

    private double scalePdfWToView(ImageView iv, PDDocument doc, double wPdf) {
        PDPage page = doc.getPage(0);
        double pdfW = page.getMediaBox().getWidth();
        double viewW = iv.getBoundsInLocal().getWidth();
        if (pdfW <= 0 || viewW <= 0) return wPdf;
        return wPdf * viewW / pdfW;
    }

    private double scalePdfHToView(ImageView iv, PDDocument doc, double hPdf) {
        PDPage page = doc.getPage(0);
        double pdfH = page.getMediaBox().getHeight();
        double viewH = iv.getBoundsInLocal().getHeight();
        if (pdfH <= 0 || viewH <= 0) return hPdf;
        return hPdf * viewH / pdfH;
    }

    private Path exportFilledTemplate(Template template, Map<Balise, TextField> textInputs, Map<Balise, Path> imageInputs) throws IOException {
        Path resultsDir = Path.of("data", "pdfeditor", "results");
        Files.createDirectories(resultsDir);

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path out = resultsDir.resolve(template.name + "_" + stamp + ".pdf");

        try (PDDocument doc = PDDocument.load(template.pdfPath.toFile())) {
            PDPage page = doc.getPage(0);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page, AppendMode.APPEND, true, true)) {
                for (Balise b : template.balises) {
                    if ("TEXT".equalsIgnoreCase(b.type)) {
                        TextField tf = textInputs.get(b);
                        String v = tf == null ? "" : tf.getText();
                        if (v != null && !v.isBlank()) {
                            cs.beginText();
                            cs.setFont(PDType1Font.HELVETICA, Math.max(6, b.height));
                            cs.newLineAtOffset(b.x, b.y);
                            cs.showText(safePdfText(v));
                            cs.endText();
                        }
                    } else if ("IMAGE".equalsIgnoreCase(b.type)) {
                        Path p = imageInputs.get(b);
                        if (p != null && Files.exists(p)) {
                            PDImageXObject img = PDImageXObject.createFromFileByContent(p.toFile(), doc);
                            cs.drawImage(img, b.x, b.y, Math.max(1, b.width), Math.max(1, b.height));
                        }
                    }
                }
            }

            doc.save(out.toFile());
        }

        return out;
    }

    private List<String> listTemplateNames() {
        Path templatesDir = Path.of("data", "pdfeditor", "templates");
        try {
            Files.createDirectories(templatesDir);
        } catch (IOException ignored) {}

        try (var s = Files.list(templatesDir)) {
            return s.filter(Files::isDirectory)
                .map(p -> p.getFileName().toString())
                .sorted()
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private void openFolder(Path dir) {
        try {
            if (!Files.exists(dir)) return;
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir.toFile());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String safePdfText(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 32 && c <= 126) sb.append(c);
            else sb.append('?');
        }
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