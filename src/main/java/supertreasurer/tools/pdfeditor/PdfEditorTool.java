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
import javafx.scene.control.TitledPane;

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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

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
            this(Path.of("data", "pdfeditor", "templates", templateName), templateName);
        }

        Template(Path folderPath, String templateName) throws IOException {
            this.name = templateName;
            this.folderPath = folderPath;
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

        Button open_templates_folder_btn= new Button("Open templates folder");
        open_templates_folder_btn.setOnAction(e -> {
            try {
                Path templates = Path.of("data", "pdfeditor", "templates");
                Files.createDirectories(templates);
                openFolder(templates);
            } catch (IOException ex) {
                // ignore
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
        editor_box.getChildren().add(open_templates_folder_btn);
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
        pdf_tabs.getTabs().add(createGestionNdfTab());
        

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

        float label_offset = 2;

        double[] xy = pdfToViewCoords(doc, iv, b.x - label_offset, b.y+b.height/2);
        double fontPx = Math.max(0.1, scalePdfHToView(iv, doc, b.height));

        Label l = new Label(value);
        l.setMouseTransparent(true);
        l.setStyle("-fx-background-color: rgba(255,255,255,0.55); -fx-border-color: red; -fx-border-width: 1; -fx-padding: 2 4 2 4;");
        l.setFont(javafx.scene.text.Font.font(fontPx));

        l.setLayoutX(xy[0]);
        l.setLayoutY(Math.max(0, xy[1]));

        return l;
    }

    private Node makeImagePreviewNode(PDDocument doc, BufferedImage img, ImageView iv, Balise b, Path imagePath) {
        try {
            Image fxImg = new Image(imagePath.toUri().toString());
            if (fxImg.isError()) return null;

            double[] xy = pdfToViewCoords(doc, iv, b.x, b.y-b.height);

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
    private void applyTemplateOnDoc(
        PDDocument doc,
        Template template,
        Map<Balise, TextField> textInputs,
        Map<Balise, Path> imageInputs
    ) throws IOException {

        PDPage page = doc.getPage(0);

        try (PDPageContentStream cs = new PDPageContentStream(doc, page, AppendMode.APPEND, true, true)) {
            for (Balise b : template.balises) {

                if ("TEXT".equalsIgnoreCase(b.type)) {
                    TextField tf = textInputs.get(b);
                    String v = tf == null ? "" : tf.getText();

                    if (v != null && !v.isBlank()) {
                            cs.beginText();
                            cs.setFont(PDType1Font.HELVETICA, Math.max(1, b.height));
                            cs.newLineAtOffset(b.x, b.y-(3*b.height/4));
                            cs.showText(safePdfText(v));
                            cs.endText();
                    }

                } else if ("IMAGE".equalsIgnoreCase(b.type)) {
                    Path p = imageInputs.get(b);
                    if (p != null && Files.exists(p)) {
                            PDImageXObject img = PDImageXObject.createFromFileByContent(p.toFile(), doc);
                            float y = b.y - Math.max(1, b.height);
                            cs.drawImage(img, b.x, y, Math.max(1, b.width), Math.max(1, b.height));
                    }
                }
            }
        }
    }
    private Path exportFilledTemplate(Template template, Map<Balise, TextField> textInputs, Map<Balise, Path> imageInputs) throws IOException {
        Path resultsDir = Path.of("data", "pdfeditor", "results");
        Files.createDirectories(resultsDir);

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path out = resultsDir.resolve(template.name + "_" + stamp + ".pdf");

        try (PDDocument doc = PDDocument.load(template.pdfPath.toFile())) {
            applyTemplateOnDoc(doc, template, textInputs, imageInputs);
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
            if (c== 'é') sb.append('e');
            else if (c == 'è') sb.append('e');
            else if (c == 'ê') sb.append('e');
            else if (c == 'ë') sb.append('e');
            else if (c == 'à') sb.append('a');
            else if (c == 'á') sb.append('a');
            else if (c == 'â') sb.append('a');
            else if (c == 'ä') sb.append('a');
            else if (c == 'ù') sb.append('u');
            else if (c == 'ú') sb.append('u');
            else if (c == 'û') sb.append('u');
            else if (c == 'ü') sb.append('u');
            else if (c =='€') sb.append('€'); //ça peut tout casser mais y'en a besoin
            else if (c >= 32 && c <= 126) sb.append(c);
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
    record NDFText(
            String last_name,
            String first_name,
            String date,
            String purpose,
            String designation,
            double amount
        ){}
    record NDFEntry(String id, NDFText text){}
    public class NdfFilesService {
        private final HttpClient client = HttpClient.newHttpClient();
        private void downloadToFile(HttpRequest request, Path dest)
                throws IOException, InterruptedException {

            HttpResponse<Path> resp = client.send(request, HttpResponse.BodyHandlers.ofFile(dest));
            if (resp.statusCode() != 200) {
                Files.deleteIfExists(dest);
                throw new IOException("Download failed: HTTP " + resp.statusCode());
            }
        }
        private HttpRequest CreateNDFListRequest(String password, URI uri){
            URI fullUri = URI.create(uri.toString() + "/expenses");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(fullUri)
                    .header("X-Api-Key", password)
                    .GET()
                    .build();
            return request;
        }
        private HttpRequest CreateNDFInvoiceRequest(String password, URI uri, String NDFid){
            String fullUri= uri.toString() + "/expenses"+ "/" + NDFid + "/invoice";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUri))
                    .header("X-Api-Key", password)
                    .GET()
                    .build();
            return request;
        }
        private HttpRequest CreateNDFSignatureRequest(String password, URI uri, String NDFid){
            String fullUri= uri.toString() + "/expenses"+ "/" + NDFid + "/signature";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUri))
                    .header("X-Api-Key", password)
                    .GET()
                    .build();
            return request;
        }
        private HttpRequest CreateDeleteNDFRequest(String password, URI uri, String NDFid){
            String fullUri= uri.toString() + "/expenses"+ "/" + NDFid;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUri))
                    .header("X-Api-Key", password)
                    .DELETE()
                    .build();
            return request;
        }
        private HttpRequest CreateRestoreBinRequest(String password, URI uri){
            String fullUri= uri.toString() + "/trash" + "/restore";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUri))
                    .header("X-Api-Key", password)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            return request;
        }
        List<NDFEntry> fetchNDFEntries(String password, URI uri, ObjectMapper objectMapper) throws IOException, InterruptedException {
            HttpRequest request = CreateNDFListRequest(password, uri);
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("Failed to fetch NDF entries: " + response.statusCode());
            }

            String responseBody = response.body();
            List<NDFEntry> entries = objectMapper.readValue(responseBody, new TypeReference<List<NDFEntry>>(){});
            return entries;
        }
        public void downloadNdfFiles(String password, URI baseUri, String ndfId)
                throws IOException, InterruptedException {

            Path folder = Path.of("data", "pdfeditor", "NDF", ndfId);
            Files.createDirectories(folder);

            Path sigPath = folder.resolve("signature.png");
            Path invPath = folder.resolve("invoice"); 

            HttpRequest sigReq = CreateNDFSignatureRequest(password, baseUri, ndfId);
            downloadToFile(sigReq, sigPath);

            HttpRequest invReq = CreateNDFInvoiceRequest(password, baseUri, ndfId);
            downloadToFile(invReq, invPath);
        }
        public void deleteNdf(String password, URI baseUri, String ndfId) throws IOException, InterruptedException {
            HttpRequest delReq = CreateDeleteNDFRequest(password, baseUri, ndfId);
            HttpResponse<String> response = client.send(delReq, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Failed to delete NDF: " + response.statusCode());
            }
        }
        public void restoreBin(String password, URI baseUri) throws IOException, InterruptedException {
            HttpRequest restoreReq = CreateRestoreBinRequest(password, baseUri);
            HttpResponse<String> response = client.send(restoreReq, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Failed to restore bin: " + response.statusCode());
            }
        }
    }
    
    private Tab createGestionNdfTab() {
        Tab gestionTab = new Tab("GestionNDF");
        gestionTab.setClosable(false);

        ObjectMapper objectMapper = new ObjectMapper();
        NdfFilesService ndfService = new NdfFilesService();

        SplitPane root = new SplitPane();
        root.setDividerPositions(0.60);

        ScrollPane previewScroll = new ScrollPane();
        previewScroll.setFitToWidth(true);
        previewScroll.setFitToHeight(true);

        StackPane previewStack = new StackPane();
        previewStack.setAlignment(Pos.TOP_LEFT);

        ImageView previewImageView = new ImageView();
        previewImageView.setPreserveRatio(true);

        Pane previewOverlay = new Pane();
        previewOverlay.setPickOnBounds(false);
        previewOverlay.setMouseTransparent(true);

        previewStack.getChildren().addAll(previewImageView, previewOverlay);
        previewScroll.setContent(previewStack);

        previewScroll.viewportBoundsProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                double w = newV.getWidth();
                if (w > 0) previewImageView.setFitWidth(w);
            }
        });

        VBox rightBox = new VBox(10);
        rightBox.setPadding(new Insets(10));

        TextField baseUriField = new TextField();
        baseUriField.setPromptText("Base URI");
        baseUriField.setText("http://127.0.0.1:5000/treasurer");

        TextField apiKeyField = new TextField();
        apiKeyField.setPromptText("X-Api-Key");

        Button fetchBtn = new Button("Fetch NDF");
        Button restoreBinBtn = new Button("RestoreBin");
        Button seeResultsBtn = new Button("See Results");

        Label statusLabel = new Label("");

        VBox ndfListBox = new VBox(8);
        ndfListBox.setPadding(new Insets(8));
        ScrollPane ndfListScroll = new ScrollPane(ndfListBox);
        ndfListScroll.setFitToWidth(true);
        ndfListScroll.setPrefViewportHeight(280);

        Label selectedNdfLabel = new Label("No accepted NDF selected");

        TextField ndfNumberField = new TextField();
        ndfNumberField.setPromptText("NDF number on 3 digits");

        Button importTreasurerSignatureBtn = new Button("Import treasurer signature");
        Label treasurerSignatureLabel = new Label("No treasurer signature selected");

        Button seeChangesBtn = new Button("See Changes");
        Button exportFinalBtn = new Button("Export final NDF");

        VBox finalizeBox = new VBox(8,
            selectedNdfLabel,
            new Label("NDF number"),
            ndfNumberField,
            importTreasurerSignatureBtn,
            treasurerSignatureLabel,
            seeChangesBtn,
            exportFinalBtn
        );
        finalizeBox.setPadding(new Insets(8));

        TitledPane finalizePane = new TitledPane("Finalize selected NDF", finalizeBox);
        finalizePane.setExpanded(false);

        rightBox.getChildren().addAll(
            new Label("Server"),
            baseUriField,
            apiKeyField,
            new HBox(10, fetchBtn, restoreBinBtn, seeResultsBtn),
            new Separator(),
            new Label("Remote NDF list"),
            ndfListScroll,
            finalizePane,
            statusLabel
        );

        root.getItems().addAll(previewScroll, rightBox);
        gestionTab.setContent(root);

        final NDFEntry[] currentAcceptedEntry = new NDFEntry[1];
        final Path[] currentAcceptedDir = new Path[1];
        final Path[] currentTreasurerSignature = new Path[1];

        final Template[] currentNdfTemplate = new Template[1];
        final PDDocument[] currentPreviewDoc = new PDDocument[1];
        final BufferedImage[] currentPreviewImg = new BufferedImage[1];

        Runnable clearPreview = () -> {
            previewOverlay.getChildren().clear();
            previewImageView.setImage(null);
            currentAcceptedEntry[0] = null;
            currentAcceptedDir[0] = null;
            currentTreasurerSignature[0] = null;
            currentNdfTemplate[0] = null;
            currentPreviewImg[0] = null;
            if (currentPreviewDoc[0] != null) {
                try { currentPreviewDoc[0].close(); } catch (Exception ignored) {}
                currentPreviewDoc[0] = null;
            }
        };

        fetchBtn.setOnAction(e -> {
            String baseUriText = baseUriField.getText();
            String apiKey = apiKeyField.getText();

            if (baseUriText == null || baseUriText.isBlank()) {
                statusLabel.setText("Base URI is empty");
                return;
            }
            if (apiKey == null || apiKey.isBlank()) {
                statusLabel.setText("API key is empty");
                return;
            }

            try {
                List<NDFEntry> entries = ndfService.fetchNDFEntries(apiKey, URI.create(baseUriText), objectMapper);

                ndfListBox.getChildren().clear();

                if (entries.isEmpty()) {
                    ndfListBox.getChildren().add(new Label("No remote NDF found"));
                }

                for (NDFEntry entry : entries) {
                    VBox card = new VBox(5);
                    card.setPadding(new Insets(8));
                    card.setStyle("-fx-border-color: lightgray; -fx-border-width: 1; -fx-background-color: rgba(240,240,240,0.35);");

                    Label idLabel = new Label("ID: " + entry.id());
                    Label whoLabel = new Label("Beneficiary: " + entry.text().first_name() + " " + entry.text().last_name());
                    Label dateLabel = new Label("Date: " + entry.text().date());
                    Label purposeLabel = new Label("Purpose: " + entry.text().purpose());
                    Label designationLabel = new Label("Designation: " + entry.text().designation());
                    Label amountLabel = new Label("Amount: " + formatNdfAmount(entry.text().amount()));

                    Button rejectBtn = new Button("Reject");
                    Button acceptBtn = new Button("Accept");

                    rejectBtn.setOnAction(ev -> {
                        try {
                            ndfService.deleteNdf(apiKey, URI.create(baseUriText), entry.id());
                            statusLabel.setText("Rejected NDF " + entry.id());
                            fetchBtn.fire();
                        } catch (IOException | InterruptedException ex) {
                            statusLabel.setText("Reject error: " + ex.getMessage());
                        }
                    });

                    acceptBtn.setOnAction(ev -> {
                        try {
                            ndfService.downloadNdfFiles(apiKey, URI.create(baseUriText), entry.id());

                            currentAcceptedEntry[0] = entry;
                            currentAcceptedDir[0] = Path.of("data", "pdfeditor", "NDF", entry.id());
                            currentTreasurerSignature[0] = null;

                            selectedNdfLabel.setText(
                                "Selected NDF: " + entry.id() +
                                "\nBeneficiary: " + entry.text().first_name() + " " + entry.text().last_name() +
                                "\nAmount: " + formatNdfAmount(entry.text().amount())
                            );
                            treasurerSignatureLabel.setText("No treasurer signature selected");
                            finalizePane.setExpanded(true);

                            Template ndfTemplate = ensureNdfTemplateCache();
                            currentNdfTemplate[0] = ndfTemplate;

                            if (currentPreviewDoc[0] != null) {
                                try { currentPreviewDoc[0].close(); } catch (Exception ignored) {}
                            }

                            currentPreviewDoc[0] = PDDocument.load(ndfTemplate.pdfPath.toFile());
                            PDFRenderer renderer = new PDFRenderer(currentPreviewDoc[0]);
                            currentPreviewImg[0] = renderer.renderImageWithDPI(0, 220, ImageType.RGB);

                            previewImageView.setImage(SwingFXUtils.toFXImage(currentPreviewImg[0], null));
                            previewOverlay.getChildren().clear();

                            statusLabel.setText("Accepted NDF " + entry.id() + " and downloaded files");
                        } catch (IOException | InterruptedException ex) {
                            statusLabel.setText("Accept error: " + ex.getMessage());
                        }
                    });

                    HBox buttons = new HBox(10, rejectBtn, acceptBtn);

                    card.getChildren().addAll(
                        idLabel,
                        whoLabel,
                        dateLabel,
                        purposeLabel,
                        designationLabel,
                        amountLabel,
                        buttons
                    );

                    ndfListBox.getChildren().add(card);
                }

                statusLabel.setText("Fetched " + entries.size() + " NDF");
            } catch (IOException | InterruptedException ex) {
                statusLabel.setText("Fetch error: " + ex.getMessage());
            }
        });

        restoreBinBtn.setOnAction(e -> {
            String baseUriText = baseUriField.getText();
            String apiKey = apiKeyField.getText();

            if (baseUriText == null || baseUriText.isBlank()) {
                statusLabel.setText("Base URI is empty");
                return;
            }
            if (apiKey == null || apiKey.isBlank()) {
                statusLabel.setText("API key is empty");
                return;
            }

            try {
                ndfService.restoreBin(apiKey, URI.create(baseUriText));
                statusLabel.setText("Bin restored");
                fetchBtn.fire();
            } catch (IOException | InterruptedException ex) {
                statusLabel.setText("RestoreBin error: " + ex.getMessage());
            }
        });

        seeResultsBtn.setOnAction(e -> {
            try {
                Path results = Path.of("data", "pdfeditor", "NDF");
                Files.createDirectories(results);
                openFolder(results);
            } catch (IOException ex) {
                statusLabel.setText("Error: " + ex.getMessage());
            }
        });

        importTreasurerSignatureBtn.setOnAction(e -> {
            if (currentAcceptedEntry[0] == null) {
                statusLabel.setText("Accept an NDF first");
                return;
            }

            Window w = importTreasurerSignatureBtn.getScene().getWindow();
            FileChooser fc = new FileChooser();
            fc.setTitle("Select treasurer signature");
            fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg")
            );
            File f = fc.showOpenDialog(w);
            if (f != null) {
                currentTreasurerSignature[0] = f.toPath();
                treasurerSignatureLabel.setText(f.getName());
                statusLabel.setText("Treasurer signature selected");
            }
        });

        seeChangesBtn.setOnAction(e -> {
            if (currentAcceptedEntry[0] == null || currentAcceptedDir[0] == null || currentNdfTemplate[0] == null || currentPreviewDoc[0] == null || currentPreviewImg[0] == null) {
                statusLabel.setText("Accept an NDF first");
                return;
            }

            String number3 = pad3(ndfNumberField.getText());
            if (number3 == null) {
                statusLabel.setText("Enter a valid 3-digit NDF number");
                return;
            }

            Map<String, String> textValues = buildNdfTextValues(currentAcceptedEntry[0], number3);
            Map<String, Path> imageValues = buildNdfImageValues(currentAcceptedDir[0], currentTreasurerSignature[0]);

            previewOverlay.getChildren().clear();

            for (Balise b : currentNdfTemplate[0].balises) {
                if ("TEXT".equalsIgnoreCase(b.type)) {
                    String value = textValues.get(b.name);
                    if (value != null && !value.isBlank()) {
                        Node node = makeTextPreviewNode(currentPreviewDoc[0], currentPreviewImg[0], previewImageView, b, value);
                        if (node != null) previewOverlay.getChildren().add(node);
                    }
                } else if ("IMAGE".equalsIgnoreCase(b.type)) {
                    Path p = imageValues.get(b.name);
                    if (p != null && Files.exists(p)) {
                        Node node = makeImagePreviewNode(currentPreviewDoc[0], currentPreviewImg[0], previewImageView, b, p);
                        if (node != null) previewOverlay.getChildren().add(node);
                    }
                }
            }

            statusLabel.setText("Preview updated");
        });

        exportFinalBtn.setOnAction(e -> {
            if (currentAcceptedEntry[0] == null || currentAcceptedDir[0] == null || currentNdfTemplate[0] == null) {
                statusLabel.setText("Accept an NDF first");
                return;
            }

            String number3 = pad3(ndfNumberField.getText());
            if (number3 == null) {
                statusLabel.setText("Enter a valid 3-digit NDF number");
                return;
            }

            try {
                Path sourceInvoice = currentAcceptedDir[0].resolve("invoice");
                if (!Files.exists(sourceInvoice)) {
                    statusLabel.setText("Downloaded invoice not found");
                    return;
                }

                Path renamedInvoice = currentAcceptedDir[0].resolve("FAC-R-2026_" + number3 + ".pdf");
                Files.copy(sourceInvoice, renamedInvoice, StandardCopyOption.REPLACE_EXISTING);

                Map<String, String> textValues = buildNdfTextValues(currentAcceptedEntry[0], number3);
                Map<String, Path> imageValues = buildNdfImageValues(currentAcceptedDir[0], currentTreasurerSignature[0]);

                Path finalPdf = currentAcceptedDir[0].resolve("NDF-2026_" + number3 + ".pdf");
                Map<Balise, TextField> textInputs = new HashMap<>();
                Map<Balise, Path> imageInputs = new HashMap<>();

                for (Balise b : currentNdfTemplate[0].balises) {
                    if ("TEXT".equalsIgnoreCase(b.type)) {
                        String v = textValues.get(b.name);           // ton Map<String,String>
                        TextField fake = new TextField(v == null ? "" : v);
                        textInputs.put(b, fake);
                    } else if ("IMAGE".equalsIgnoreCase(b.type)) {
                        Path p = imageValues.get(b.name);            // ton Map<String,Path>
                        if (p != null) imageInputs.put(b, p);
                    }
                }

                exportFilledTemplateToPath(currentNdfTemplate[0], textInputs, imageInputs, finalPdf);

                statusLabel.setText("Final NDF exported: " + finalPdf.getFileName());
            } catch (IOException ex) {
                statusLabel.setText("Export error: " + ex.getMessage());
            }
        });

        return gestionTab;
    }

    private Template ensureNdfTemplateCache() throws IOException {
        Path cacheDir = Path.of("data", "pdfeditor", "NDF", "_template_cache");
        Files.createDirectories(cacheDir);

        copyResourceToPath("/ndf_template/template.pdf", cacheDir.resolve("template.pdf"));
        copyResourceToPath("/ndf_template/template.json", cacheDir.resolve("template.json"));

        return new Template(cacheDir, "ndf_template");
    }

    private void copyResourceToPath(String resourcePath, Path dest) throws IOException {
        try (var in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Missing resource: " + resourcePath);
            }
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Map<String, String> buildNdfTextValues(NDFEntry entry, String number3) {
        Map<String, String> map = new HashMap<>();

        String noteNumber = "2026_" + number3;
        String invoiceRef = "FAC-R-2026_" + number3;
        String amount = formatNdfAmount(entry.text().amount());

        map.put("numero_note_frais", "1");
        map.put("description", nullToEmpty(entry.text().designation()));
        map.put("ref_facture", invoiceRef);
        map.put("Montant_haut", amount);
        map.put("montant_bas", amount);
        map.put("prenom", nullToEmpty(entry.text().first_name()));
        map.put("nom", nullToEmpty(entry.text().last_name()));
        map.put("date", nullToEmpty(entry.text().date()));
        map.put("motif", nullToEmpty(entry.text().purpose()));
        map.put("numero_note_haut", noteNumber);

        return map;
    }

    private Map<String, Path> buildNdfImageValues(Path ndfDir, Path treasurerSignaturePath) {
        Map<String, Path> map = new HashMap<>();

        Path beneficiarySignature = ndfDir.resolve("signature.png");
        if (Files.exists(beneficiarySignature)) {
            map.put("signature_benef", beneficiarySignature);
        }

        if (treasurerSignaturePath != null && Files.exists(treasurerSignaturePath)) {
            map.put("signature_trez", treasurerSignaturePath);
        }

        return map;
    }

    private Path exportFilledTemplateToPath(
        Template template,
        Map<Balise, TextField> textInputs,
        Map<Balise, Path> imageInputs,
        Path out
    ) throws IOException {

        Files.createDirectories(out.getParent());

        try (PDDocument doc = PDDocument.load(template.pdfPath.toFile())) {
            applyTemplateOnDoc(doc, template, textInputs, imageInputs);
            doc.save(out.toFile());
        }

        return out;
    }

    private String pad3(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.isBlank()) return null;
        if (digits.length() > 3) return null;
        return String.format("%03d", Integer.parseInt(digits));
    }

    private String formatNdfAmount(double amount) {
        String s = String.format("%.2f", amount).replace('.', ',');
        return s + " €";
    }
}