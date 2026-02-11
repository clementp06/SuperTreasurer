package supertreasurer.tools.BudgetTool;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;


import javafx.stage.FileChooser;
import javafx.stage.Window;
import supertreasurer.tools.ToolModule;

public class BudgetTool implements ToolModule {

    @Override
    public String id() {
        return "budget";
    }

    @Override
    public String displayName() {
        return "Budget";
    }

    @Override
    public Tab createTab(Path toolDataDir) {
        // Partie relative à l'onglet de saisie
        ensureBudgetRoot(toolDataDir);

        Label title = new Label("Budget tool (V1)");
        Label hint = new Label("Data dir: " + toolDataDir);

        Button openDataDirBtn = new Button("Open data folder");
        openDataDirBtn.setOnAction(e -> openFolder(toolDataDir));

        TextField activityIdField = new TextField();
        activityIdField.setPromptText("activity id (ex: maraude_fev)");

        TextField activityNameField = new TextField();
        activityNameField.setPromptText("activity name (ex: Maraude février)");

        Button createActivityBtn = new Button("Create activity");
        Label activityStatus = new Label("");

        ComboBox<String> entryActivityIdField = createActivityComboBox(listActivityIds( toolDataDir.resolve("activities")));
        entryActivityIdField.setPromptText("activity id (ex: maraude_fev)");

        createActivityBtn.setOnAction(e -> {
            String activityId = activityIdField.getText().trim();
            String activityName = activityNameField.getText().trim();
            try {
                Path activityDir = ensureActivity(toolDataDir, activityId, activityName);
                activityStatus.setText("OK: " + activityDir);
                entryActivityIdField.getItems().clear();
                entryActivityIdField.getItems().addAll(listActivityIds( toolDataDir.resolve("activities")));
            } catch (Exception ex) {
                activityStatus.setText("Error: " + ex.getMessage());
            }
        });

        Separator sep1 = new Separator();

        DatePicker datePicker = new DatePicker(LocalDate.now());

        TextField amountField = new TextField();
        amountField.setPromptText("amount in cents (ex: -1250 for -12.50€)");

        TextArea descriptionArea = new TextArea();
        descriptionArea.setPromptText("description");
        descriptionArea.setPrefRowCount(3);

        Button attachBtn = new Button("Attach invoice (optional)");
        Label attachedPathLabel = new Label("No file selected");
        final Path[] selectedFile = new Path[1];

        attachBtn.setOnAction(e -> {
            Window w = attachBtn.getScene().getWindow();
            FileChooser fc = new FileChooser();
            fc.setTitle("Select invoice");
            java.io.File f = fc.showOpenDialog(w);
            if (f != null) {
                selectedFile[0] = f.toPath();
                attachedPathLabel.setText(f.getAbsolutePath());
            }
        });

        Button addEntryBtn = new Button("Add entry");
        Label entryStatus = new Label("");

        addEntryBtn.setOnAction(e -> {
            String activityId = entryActivityIdField.getValue().trim();
            String amountText = amountField.getText().trim();
            String desc = descriptionArea.getText().trim();
            LocalDate d = datePicker.getValue();

            try {
                long amountCents = Long.parseLong(amountText);
                String entryId = "entry_" + UUID.randomUUID();

                Path activityDir = ensureActivity(toolDataDir, activityId, activityId);
                Path ledgerFile = activityDir.resolve("ledger.json");

                appendLedgerEntry(ledgerFile, entryId, d, desc, amountCents);

                if (selectedFile[0] != null) {
                    Path attachmentsDir = activityDir.resolve("attachments").resolve(entryId);
                    Files.createDirectories(attachmentsDir);
                    Path target = attachmentsDir.resolve(selectedFile[0].getFileName().toString());
                    Files.copy(selectedFile[0], target);
                }

                entryStatus.setText("Entry added: " + entryId);
                selectedFile[0] = null;
                attachedPathLabel.setText("No file selected");
            } catch (Exception ex) {
                entryStatus.setText("Error: " + ex.getMessage());
            }
        });

        VBox root = new VBox(
            10,
            title,
            hint,
            openDataDirBtn,
            new Separator(),
            new Label("Create activity"),
            new HBox(10, activityIdField, activityNameField, createActivityBtn),
            activityStatus,
            sep1,
            new Label("Add entry"),
            entryActivityIdField,
            new HBox(10, new Label("Date:"), datePicker),
            amountField,
            descriptionArea,
            new HBox(10, attachBtn, attachedPathLabel),
            addEntryBtn,
            entryStatus
        );

        root.setPadding(new Insets(12));
        // Fin de l'onglet de Saisie
        // Départ de l'onglet de gestion

        Label TitreGestion = new Label("Gestion des activités");
        Label hintGestion = new Label("Ici on pourra éditer les détails du budget et des activités");

        //ChatGpt , travaille ici
                VBox gestionRoot = new VBox(10);
        gestionRoot.setPadding(new Insets(12));

        Button refreshBtn = new Button("Refresh (reload from disk)");
        Label refreshStatus = new Label("");

        javafx.scene.control.Accordion accordion = new javafx.scene.control.Accordion();

        java.util.function.Consumer<Void> reloadView = (v) -> {
            accordion.getPanes().clear();

            Path activitiesDir = toolDataDir.resolve("activities");
            List<String> activityIds = listActivityIds(activitiesDir);

            for (String activityId : activityIds) {
                Path activityDir = activitiesDir.resolve(activityId);

                String activityName = activityId;
                Path activityFile = activityDir.resolve("activity.json");
                if (Files.exists(activityFile)) {
                    try {
                        String json = Files.readString(activityFile, StandardCharsets.UTF_8);
                        java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("\"name\"\\s*:\\s*\"([^\"]*)\"")
                            .matcher(json);
                        if (m.find()) {
                            activityName = m.group(1);
                        }
                    } catch (IOException ignored) {
                    }
                }

                long totalCents = 0;
                java.util.ArrayList<String> incomes = new java.util.ArrayList<>();
                java.util.ArrayList<String> expenses = new java.util.ArrayList<>();

                Path ledgerFile = activityDir.resolve("ledger.json");
                if (Files.exists(ledgerFile)) {
                    try {
                        String ledger = Files.readString(ledgerFile, StandardCharsets.UTF_8);

                        java.util.regex.Pattern pEntry = java.util.regex.Pattern.compile("\\{[^\\}]*\\}");
                        java.util.regex.Matcher mEntry = pEntry.matcher(ledger);

                        while (mEntry.find()) {
                            String entry = mEntry.group();

                            String date = "";
                            String desc = "";
                            long amountCents = 0;

                            java.util.regex.Matcher mDate = java.util.regex.Pattern
                                .compile("\"date\"\\s*:\\s*\"([^\"]*)\"")
                                .matcher(entry);
                            if (mDate.find()) date = mDate.group(1);

                            java.util.regex.Matcher mDesc = java.util.regex.Pattern
                                .compile("\"description\"\\s*:\\s*\"([^\"]*)\"")
                                .matcher(entry);
                            if (mDesc.find()) desc = mDesc.group(1);

                            java.util.regex.Matcher mAmt = java.util.regex.Pattern
                                .compile("\"amountCents\"\\s*:\\s*(-?\\d+)")
                                .matcher(entry);
                            if (mAmt.find()) amountCents = Long.parseLong(mAmt.group(1));

                            totalCents += amountCents;

                            String line = date + " | " + desc + " | " + formatCents(amountCents);
                            if (amountCents >= 0) incomes.add(line);
                            else expenses.add(line);
                        }
                    } catch (IOException ignored) {
                    }
                }

                String header = activityName + "  —  balance: " + formatCents(totalCents);

                javafx.scene.control.ListView<String> incomeList = new javafx.scene.control.ListView<>();
                incomeList.getItems().addAll(incomes);
                incomeList.setPrefHeight(140);

                javafx.scene.control.ListView<String> expenseList = new javafx.scene.control.ListView<>();
                expenseList.getItems().addAll(expenses);
                expenseList.setPrefHeight(140);

                VBox paneContent = new VBox(10);
                paneContent.getChildren().addAll(
                    new Label("Incomes"),
                    incomeList,
                    new Label("Expenses"),
                    expenseList
                );

                javafx.scene.control.TitledPane pane = new javafx.scene.control.TitledPane(header, paneContent);
                accordion.getPanes().add(pane);
            }

            refreshStatus.setText("Loaded " + accordion.getPanes().size() + " activities");
        };

        refreshBtn.setOnAction(e -> {
            reloadView.accept(null);
        });

        gestionRoot.getChildren().addAll(
            TitreGestion,
            hintGestion,
            new HBox(10, refreshBtn, refreshStatus),
            new Separator(),
            accordion
        );

        reloadView.accept(null);

        //ChatGpt , fin de travaille ici

        TabPane budgetTabs = new TabPane();
        Tab tabSaisie = new Tab("Saisie");
        Tab tabGestion = new Tab("Gestion");
        tabSaisie.setContent(root);
        tabSaisie.setClosable(false);
        tabGestion.setContent(gestionRoot);
        tabGestion.setClosable(false);
        budgetTabs.getTabs().addAll(tabSaisie, tabGestion);
        Tab tab= new Tab("budget");
        tab.setContent(budgetTabs);
        return tab;
    }

    private void ensureBudgetRoot(Path toolDataDir) {
        try {
            Files.createDirectories(toolDataDir.resolve("activities"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Path ensureActivity(Path toolDataDir, String activityId, String activityName) throws IOException {
        if (activityId.isEmpty()) {
            throw new IllegalArgumentException("activity id is empty");
        }

        Path activityDir = toolDataDir.resolve("activities").resolve(activityId);
        Files.createDirectories(activityDir);
        Files.createDirectories(activityDir.resolve("attachments"));

        Path activityFile = activityDir.resolve("activity.json");
        if (!Files.exists(activityFile)) {
            String json = "{\n" +
                "  \"id\": \"" + escape(activityId) + "\",\n" +
                "  \"name\": \"" + escape(activityName.isEmpty() ? activityId : activityName) + "\"\n" +
                "}\n";
            Files.writeString(activityFile, json, StandardCharsets.UTF_8);
        }

        Path ledgerFile = activityDir.resolve("ledger.json");
        if (!Files.exists(ledgerFile)) {
            Files.writeString(ledgerFile, "[]\n", StandardCharsets.UTF_8);
        }

        return activityDir;
    }

    private List<String> listActivityIds(Path activitiesDir){
        try (var stream = Files.list(activitiesDir)) {
            return stream
                .filter(Files::isDirectory)
                .map(p -> p.getFileName().toString())
                .toList();
        } catch (IOException e) {
            return List.of("il y a un bug: impossible de lister les activités");
        }
    }

    private ComboBox<String> createActivityComboBox(List<String> activityIds) {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.getItems().addAll(activityIds);
        comboBox.setEditable(true);
        return comboBox;
    }

    private void appendLedgerEntry(Path ledgerFile, String entryId, LocalDate date, String description, long amountCents) throws IOException {
        String entryJson = "{\n" +
            "  \"id\": \"" + escape(entryId) + "\",\n" +
            "  \"date\": \"" + escape(date.toString()) + "\",\n" +
            "  \"description\": \"" + escape(description) + "\",\n" +
            "  \"amountCents\": " + amountCents + "\n" +
            "}";

        String content = Files.readString(ledgerFile, StandardCharsets.UTF_8).trim();
        if (content.equals("[]")) {
            Files.writeString(ledgerFile, "[\n" + entryJson + "\n]\n", StandardCharsets.UTF_8);
            return;
        }

        if (!content.endsWith("]")) {
            throw new IllegalStateException("ledger.json is not a JSON array");
        }

        String withoutEnd = content.substring(0, content.length() - 1).trim();
        String newContent = withoutEnd + ",\n" + entryJson + "\n]\n";
        Files.writeString(ledgerFile, newContent, StandardCharsets.UTF_8);
    }

    private void openFolder(Path dir) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir.toFile());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    private String formatCents(long cents) {
        boolean negative = cents < 0;
        long abs = Math.abs(cents);
        long euros = abs / 100;
        long remCents = abs % 100;
        return (negative ? "-" : "") + euros + "€" + (remCents > 0 ? String.format("%02d", remCents) : "");
    }
}
