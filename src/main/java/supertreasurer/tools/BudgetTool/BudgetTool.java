package supertreasurer.tools.BudgetTool;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Comparator;
import java.io.File;

import javafx.geometry.Insets;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.util.LinkedHashMap;
import java.util.Map;
import java.awt.Color;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;


import supertreasurer.tools.ToolModule;


public class BudgetTool implements ToolModule {
    private long taille_legende = 20;
    private long taille_titre_chart = 25;

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

                appendLedgerEntry(ledgerFile, entryId, d, desc, amountCents, selectedFile, activityDir);

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

        VBox gestionRoot = new VBox(10);
        gestionRoot.setPadding(new Insets(12));

        Button refreshBtn = new Button("Refresh (reload from disk)");
        Label refreshStatus = new Label("");

        Accordion accordion = new Accordion();

        Pattern extractEntryId = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]*)\"");
        Pattern namePattern  = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]*)\"");
        Pattern entryPattern = Pattern.compile("\\{[^\\}]*\\}");
        Pattern datePattern  = Pattern.compile("\"date\"\\s*:\\s*\"([^\"]*)\"");
        Pattern descPattern  = Pattern.compile("\"description\"\\s*:\\s*\"([^\"]*)\"");
        Pattern amtPattern   = Pattern.compile("\"amountCents\"\\s*:\\s*(-?\\d+)");

        Consumer<Void> reloadView = (v) -> {

            accordion.getPanes().clear();
            Path activitiesDir = toolDataDir.resolve("activities");
            List<String> activityIds = listActivityIds(activitiesDir);

            long bilan_global = 0;

            for (String activityId : activityIds) {

                Path activityDir = activitiesDir.resolve(activityId);

                String activityName = activityId;
                Path activityFile = activityDir.resolve("activity.json");

                if (Files.exists(activityFile)) {
                    try {
                        String json = Files.readString(activityFile, StandardCharsets.UTF_8);
                        Matcher m = namePattern.matcher(json);
                        if (m.find()) activityName = m.group(1);
                    } catch (IOException ignored) {}
                }

                long totalCents = 0;
                VBox incomes  = new VBox();
                VBox expenses = new VBox();

                Path ledgerFile = activityDir.resolve("ledger.json");

                if (Files.exists(ledgerFile)) {
                    try {
                        String ledger = Files.readString(ledgerFile, StandardCharsets.UTF_8);
                        Matcher mEntry = entryPattern.matcher(ledger);

                        while (mEntry.find()) {
                            String entry = mEntry.group();

                            String date = "", desc = "" ; 
                            String entryId= "";
                            long amountCents = 0;
                            
                            Matcher mId = extractEntryId.matcher(entry);
                            if (mId.find()) entryId = mId.group(1);

                            Matcher mDate = datePattern.matcher(entry);
                            if (mDate.find()) date = mDate.group(1);

                            Matcher mDesc = descPattern.matcher(entry);
                            if (mDesc.find()) desc = mDesc.group(1);

                            Matcher mAmt = amtPattern.matcher(entry);
                            if (mAmt.find()) amountCents = Long.parseLong(mAmt.group(1));

                            totalCents += amountCents;

                            String line = date + " | " + desc + " | " + formatCents(amountCents);
                            Button deleteBtn = new Button("Delete");
                            
                            final String entryIdFinal = entryId;
                            deleteBtn.setOnAction(e -> {
                                try {
                                    deleteEntry(activityDir, entryIdFinal);
                                    refreshBtn.fire();
                                } catch (IOException ex) {
                                    throw new RuntimeException(ex);
                                }
                            });

                            Button ShowAttachmentsBtn = new Button("Show attachments");
                            ShowAttachmentsBtn.setOnAction(e -> {
                                Path attachmentsDir = activityDir.resolve("attachments").resolve(entryIdFinal);
                                if (Files.exists(attachmentsDir)) {
                                    openFolder(attachmentsDir);
                                }
                            });

                            HBox lineBox = new HBox(10, new Label(line), deleteBtn, ShowAttachmentsBtn);
                            if (amountCents >= 0) incomes.getChildren().add(lineBox);
                            else expenses.getChildren().add(lineBox);
                        }
                    } catch (IOException ignored) {}
                }

                String header = activityName + " — balance: " + formatCents(totalCents);
                
                bilan_global += totalCents;

                VBox paneContent = new VBox(10,
                    new Label("Incomes"), incomes,
                    new Label("Expenses"), expenses
                );

                accordion.getPanes().add(new TitledPane(header, paneContent));
            }
            Label bilanLabel = new Label("Bilan global: " + formatCents(bilan_global));
            accordion.getPanes().add(new TitledPane("Bilan global", bilanLabel));


            refreshStatus.setText("Loaded " + accordion.getPanes().size() + " activities");
        };

        refreshBtn.setOnAction(e -> reloadView.accept(null));

        Button genereBilan= new Button("Générer le bilan LaTeX");
        genereBilan.setOnAction(e -> {
            try {
                Path tex = createLatex(toolDataDir);
                compileLatexToPdf(tex);
                refreshStatus.setText("Bilan LaTeX généré avec succès");
            } catch (IOException | InterruptedException ex) {
                refreshStatus.setText("Erreur lors de la génération du bilan: " + ex.getMessage());
            }
        });

        Button voirBilans= new Button("Voir les bilans générés");
        voirBilans.setOnAction(e -> {
            try {
                Path bilansDir = toolDataDir.resolve("bilans");
                if (Files.exists(bilansDir)) {
                    openFolder(bilansDir);
                } else {
                    refreshStatus.setText("Aucun bilan trouvé. Générer un bilan avant de pouvoir les voir.");
                }
            } catch (Exception ex) {
                refreshStatus.setText("Erreur lors de l'ouverture du dossier des bilans: " + ex.getMessage());
            }
        });

        gestionRoot.getChildren().addAll(
            TitreGestion,
            hintGestion,
            new HBox(10, refreshBtn, refreshStatus),
            new Separator(),
            accordion,
            new Separator(),
            genereBilan,
            voirBilans
        );

        reloadView.accept(null);

        // Fin de l'onglet de gestion

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

    private void appendLedgerEntry(Path ledgerFile, String entryId, LocalDate date, String description, long amountCents, Path[] selectedFile, Path activityDir) throws IOException {
        //Pour chaque entrée, on crée un sous dossier dans attachments avec l'id de l'entrée, et on y copie le ou les fichiers attachés (s'il existe)
        String entryJson = "{\n" +
            "  \"id\": \"" + escape(entryId) + "\",\n" +
            "  \"date\": \"" + escape(date.toString()) + "\",\n" +
            "  \"description\": \"" + escape(description) + "\",\n" +
            "  \"amountCents\": " + amountCents + "\n" +
            "}";
        
        for (Path file : selectedFile) {
            if (file != null) {
                Path attachmentsDir = activityDir.resolve("attachments").resolve(entryId);
                Files.createDirectories(attachmentsDir);
                Path target = attachmentsDir.resolve(file.getFileName().toString());
                Files.copy(file, target);
            }
        }

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
    private void deleteEntry(Path activityDir, String entryId) throws IOException {
        //doit supprimer l'entrée d'id entryId du Ledger.json et les pièces jointes associées
        Path ledgerFile = activityDir.resolve("ledger.json");
        String content = Files.readString(ledgerFile, StandardCharsets.UTF_8);
        String entryPattern = "\\{[^\\}]*\"id\"\\s*:\\s*\"" + Pattern.quote(entryId) + "\"[^\\}]*\\}";
        String newContent = content.replaceAll(entryPattern, "").replaceAll(",\\s*,", ",").replaceAll("\\[\\s*,", "[").replaceAll(",\\s*\\]", "]");
        Files.writeString(ledgerFile, newContent, StandardCharsets.UTF_8);

        // Supprimer les pièces jointes associées
        Path attachmentsDir = activityDir.resolve("attachments").resolve(entryId);
        if (Files.exists(attachmentsDir)) {
            Files.walk(attachmentsDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }
    private Path createLatex(Path toolDataDir) throws IOException {
        Path budgetsDir = toolDataDir;
        Path activitiesDir = budgetsDir.resolve("activities");
        Path bilansDir = budgetsDir.resolve("bilans");
        Files.createDirectories(bilansDir);

        String mainTemplate = loadTemplate("budget_main.tex");
        String sectionTemplate = loadTemplate("budget_activity_section.tex");
        String rowTemplate = loadTemplate("budget_activity_row.tex");

        LocalDate today = LocalDate.now();
        String reportDate = String.format("%02d/%02d/%04d", today.getDayOfMonth(), today.getMonthValue(), today.getYear());

        List<ActivityData> activities = loadActivities(activitiesDir);
        createCharts(bilansDir, activities);

        long totalIncomeCents = 0;
        long totalExpenseCents = 0;

        for (ActivityData a : activities) {
            for (EntryData e : a.entries) {
                if (e.amountCents >= 0) totalIncomeCents += e.amountCents;
                else totalExpenseCents += -e.amountCents;
            }
        }

        long netCents = totalIncomeCents - totalExpenseCents;

        String globalRows = buildGlobalActivityRows(activities, rowTemplate);
        String perActivitySections = buildPerActivitySections(activities, sectionTemplate, rowTemplate);

        String tex = mainTemplate;
        tex = tex.replace("%%REPORT_DATE%%", latexEscape(reportDate));
        tex = tex.replace("%%ASSOCIATION_NAME%%", latexEscape("Telecom Espoir"));
        tex = tex.replace("%%TOTAL_INCOME%%", centsToEurString(totalIncomeCents));
        tex = tex.replace("%%TOTAL_EXPENSE%%", centsToEurString(totalExpenseCents));
        tex = tex.replace("%%NET_TOTAL%%", centsToEurString(netCents));
        tex = tex.replace("%%GLOBAL_ACTIVITY_ROWS%%", globalRows);
        tex = tex.replace("%%CHART_GLOBAL_INCOME%%", "charts/global_income_by_activity.pdf");
        tex = tex.replace("%%CHART_GLOBAL_EXPENSE%%", "charts/global_expense_by_activity.pdf");
        tex = tex.replace("%%PER_ACTIVITY_SECTIONS%%", perActivitySections);

        String fileName = String.format("bilan_%04d-%02d-%02d.tex", today.getYear(), today.getMonthValue(), today.getDayOfMonth());
        Path outFile = bilansDir.resolve(fileName);
        Files.writeString(outFile, tex, StandardCharsets.UTF_8);
        return outFile;
    }

    private static final class ActivityData {
        final String id;
        final String name;
        final List<EntryData> entries;

        ActivityData(String id, String name, List<EntryData> entries) {
            this.id = id;
            this.name = name;
            this.entries = entries;
        }
    }

    private static final class EntryData {
        final String date;
        final String description;
        final long amountCents;

        EntryData(String id, String date, String description, long amountCents) {
            this.date = date;
            this.description = description;
            this.amountCents = amountCents;
        }
    }

    private List<ActivityData> loadActivities(Path activitiesDir) throws IOException {
        if (!Files.exists(activitiesDir)) return List.of();

        List<String> ids;
        try (var s = Files.list(activitiesDir)) {
            ids = s.filter(Files::isDirectory)
                .map(p -> p.getFileName().toString())
                .sorted()
                .toList();
        }

        Pattern namePattern = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]*)\"");
        Pattern entryPattern = Pattern.compile("\\{[^\\}]*\\}");
        Pattern idPattern = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]*)\"");
        Pattern datePattern = Pattern.compile("\"date\"\\s*:\\s*\"([^\"]*)\"");
        Pattern descPattern = Pattern.compile("\"description\"\\s*:\\s*\"([^\"]*)\"");
        Pattern amtPattern = Pattern.compile("\"amountCents\"\\s*:\\s*(-?\\d+)");

        List<ActivityData> out = new ArrayList<>();

        for (String activityId : ids) {
            Path activityDir = activitiesDir.resolve(activityId);

            String activityName = activityId;
            Path activityFile = activityDir.resolve("activity.json");
            if (Files.exists(activityFile)) {
                String json = Files.readString(activityFile, StandardCharsets.UTF_8);
                Matcher m = namePattern.matcher(json);
                if (m.find()) activityName = m.group(1);
            }

            List<EntryData> entries = new ArrayList<>();
            Path ledgerFile = activityDir.resolve("ledger.json");
            if (Files.exists(ledgerFile)) {
                String ledger = Files.readString(ledgerFile, StandardCharsets.UTF_8);
                Matcher mEntry = entryPattern.matcher(ledger);
                while (mEntry.find()) {
                    String entry = mEntry.group();

                    String entryId = "";
                    String date = "";
                    String desc = "";
                    long amount = 0;

                    Matcher mId = idPattern.matcher(entry);
                    if (mId.find()) entryId = mId.group(1);

                    Matcher mDate = datePattern.matcher(entry);
                    if (mDate.find()) date = mDate.group(1);

                    Matcher mDesc = descPattern.matcher(entry);
                    if (mDesc.find()) desc = mDesc.group(1);

                    Matcher mAmt = amtPattern.matcher(entry);
                    if (mAmt.find()) amount = Long.parseLong(mAmt.group(1));

                    entries.add(new EntryData(entryId, date, desc, amount));
                }
            }

            entries.sort(Comparator.comparing(e -> e.date == null ? "" : e.date));
            out.add(new ActivityData(activityId, activityName, entries));
        }

        return out;
    }

    private String buildGlobalActivityRows(List<ActivityData> activities, String rowTemplate) {
        StringBuilder sb = new StringBuilder();

        for (ActivityData a : activities) {
            long income = 0;
            long expense = 0;
            for (EntryData e : a.entries) {
                if (e.amountCents >= 0) income += e.amountCents;
                else expense += -e.amountCents;
            }
            long net = income - expense;

            sb.append(latexEscape(a.name))
                .append(" & \\EUR{").append(centsToEurString(income)).append("}")
                .append(" & \\EUR{").append(centsToEurString(expense)).append("}")
                .append(" & \\EUR{").append(centsToEurString(net)).append("} \\\\\n");
        }

        return sb.toString();
    }

    private String buildPerActivitySections(List<ActivityData> activities, String sectionTemplate, String rowTemplate) {
        StringBuilder all = new StringBuilder();

        for (ActivityData a : activities) {
            long net = 0;

            StringBuilder rows = new StringBuilder();
            for (EntryData e : a.entries) {
                net += e.amountCents;

                String row = rowTemplate;
                row = row.replace("%%ROW_DATE%%", latexEscape(nullToEmpty(e.date)));
                row = row.replace("%%ROW_DESCRIPTION%%", latexEscape(nullToEmpty(e.description)));
                row = row.replace("%%ROW_AMOUNT%%", centsToEurSignedString(e.amountCents));
                rows.append(row);
            }

            String section = sectionTemplate;
            section = section.replace("%%ACTIVITY_NAME%%", latexEscape(a.name));
            section = section.replace("%%ACTIVITY_ROWS%%", rows.toString());
            section = section.replace("%%ACTIVITY_NET%%", centsToEurString(net));
            section = section.replace("%%ACTIVITY_CHART_INCOME%%", "charts/" + safeFileStem(a.id) + "_income.pdf");
            section = section.replace("%%ACTIVITY_CHART_EXPENSE%%", "charts/" + safeFileStem(a.id) + "_expense.pdf");

            all.append(section);
        }

        return all.toString();
    }

    private String loadTemplate(String fileName) throws IOException {
        String path = "/templates/" + fileName;
        try (var in = getClass().getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String latexEscape(String s) {
        if (s == null) return "";
        String out = s;
        out = out.replace("\\", "\\textbackslash{}");
        out = out.replace("{", "\\{");
        out = out.replace("}", "\\}");
        out = out.replace("#", "\\#");
        out = out.replace("$", "\\$");
        out = out.replace("%", "\\%");
        out = out.replace("&", "\\&");
        out = out.replace("_", "\\_");
        out = out.replace("^", "\\textasciicircum{}");
        out = out.replace("~", "\\textasciitilde{}");
        return out;
    }

    private String centsToEurString(long cents) {
        long a = Math.abs(cents);
        long euros = a / 100;
        long rem = a % 100;
        String s = euros + "." + String.format("%02d", rem);
        return cents < 0 ? "-" + s : s;
    }

    private String centsToEurSignedString(long cents) {
        return centsToEurString(cents);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String safeFileStem(String s) {
        if (s == null || s.isBlank()) return "activity";
        String out = s.toLowerCase();
        out = out.replaceAll("[^a-z0-9\\-_.]+", "_");
        return out;
    }
    private static final class PieItem {
    final String label;
    final long value;
    PieItem(String label, long value) { this.label = label; this.value = value; }
    }

    private void createCharts(Path bilansDir, List<ActivityData> activities) throws IOException {
        Path chartsDir = bilansDir.resolve("charts");
        Files.createDirectories(chartsDir);

        List<PieItem> globalIncome = new ArrayList<>();
        List<PieItem> globalExpense = new ArrayList<>();

        for (ActivityData a : activities) {
            long income = 0;
            long expense = 0;
            for (EntryData e : a.entries) {
                if (e.amountCents >= 0) income += e.amountCents;
                else expense += -e.amountCents;
            }
            globalIncome.add(new PieItem(a.name, income));
            globalExpense.add(new PieItem(a.name, expense));
        }

        writePiePdf(chartsDir.resolve("global_income_by_activity.pdf"), "Entrées par activité", globalIncome, taille_titre_chart);
        writePiePdf(chartsDir.resolve("global_expense_by_activity.pdf"), "Sorties par activité", globalExpense, taille_titre_chart);

        for (ActivityData a : activities) {
            Map<String, Long> incomeByDesc = new LinkedHashMap<>();
            Map<String, Long> expenseByDesc = new LinkedHashMap<>();

            for (EntryData e : a.entries) {
                String key = (e.description == null || e.description.isBlank()) ? "Sans description" : e.description;
                if (e.amountCents >= 0) incomeByDesc.merge(key, e.amountCents, Long::sum);
                else expenseByDesc.merge(key, -e.amountCents, Long::sum);
            }

            writePiePdf(chartsDir.resolve(safeFileStem(a.id) + "_income.pdf"), "Entrées — " + a.name, toPieItems(incomeByDesc), taille_titre_chart);
            writePiePdf(chartsDir.resolve(safeFileStem(a.id) + "_expense.pdf"), "Sorties — " + a.name, toPieItems(expenseByDesc), taille_titre_chart);
        }
    }

    private List<PieItem> toPieItems(Map<String, Long> m) {
        List<PieItem> out = new ArrayList<>();
        for (var e : m.entrySet()) out.add(new PieItem(e.getKey(), e.getValue()));
        return out;
    }

    private void writePiePdf(Path outFile, String title, List<PieItem> items,long taille_texte) throws IOException {
        long total = 0;
        for (PieItem it : items) total += Math.max(0, it.value);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float h = page.getMediaBox().getHeight();

                float margin = 40f;

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, taille_texte);
                cs.newLineAtOffset(margin, h - margin);
                cs.showText(safePdfText(title));
                cs.endText();

                if (total <= 0) {
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, taille_texte);
                    cs.newLineAtOffset(margin, h - margin - 60f);
                    cs.showText("No data");
                    cs.endText();
                } else {
                    float cx = margin + 210f;
                    float cy = h / 2f - 20f;
                    float r  = 140f;

                    drawPie(cs, cx, cy, r, items, total);
                    drawCircleOutline(cs, cx, cy, r);
                    
                    float legendX = cx - r;
                    float legendYTop = cy - r - 20f; // 20px sous le cercle
                    drawLegend(cs, legendX, legendYTop, items, total);

                }
            }

            Files.createDirectories(outFile.getParent());
            doc.save(outFile.toFile());
        }
    }

    private void drawLegend(PDPageContentStream cs, float x, float yTop, List<PieItem> items, long total) throws IOException {
        float y = yTop;

        float lineHeight = taille_legende + 6f; // dépend de la police
        float boxSize = Math.max(8f, taille_legende * 0.8f);

        int colorIdx = 0;

        for (PieItem it : items) {
            long v = Math.max(0, it.value);
            if (v == 0) continue;

            int[] rgb = palette(colorIdx++);
            cs.setNonStrokingColor(new Color(rgb[0], rgb[1], rgb[2]));
            cs.addRect(x, y - boxSize, boxSize, boxSize);
            cs.fill();

            cs.setNonStrokingColor(Color.BLACK);

            int pct = (int)Math.round((v * 100.0) / total);
            String label = safePdfText(it.label);
            if (label.length() > 45) label = label.substring(0, 45) + "...";
            String line = label + " — " + pct + "%";

            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, taille_legende);
            cs.newLineAtOffset(x + boxSize + 6f, y - boxSize + 1f);
            cs.showText(line);
            cs.endText();

            y -= lineHeight;
            if (y < 40f) break; // marge bas
        }
    }


    private void drawPie(PDPageContentStream cs, float cx, float cy, float r, List<PieItem> items, long total) throws IOException {
        double start = 0.0;
        int colorIdx = 0;

        for (PieItem it : items) {
            long v = Math.max(0, it.value);
            if (v == 0) continue;

            double sweep = (v * 2.0 * Math.PI) / total;
            double end = start + sweep;

            int[] rgb = palette(colorIdx++);
            cs.setNonStrokingColor(new Color(rgb[0], rgb[1], rgb[2]));

            List<float[]> arcPts = wedgePolygon(cx, cy, r, start, end, 80);

            cs.moveTo(cx, cy);
            for (float[] p : arcPts) cs.lineTo(p[0], p[1]);
            cs.closePath();
            cs.fill();

            start = end;
        }
    }

    private List<float[]> wedgePolygon(float cx, float cy, float r, double a0, double a1, int maxSteps) {
        double sweep = a1 - a0;
        int steps = Math.max(2, (int)Math.ceil(Math.abs(sweep) / (2.0 * Math.PI) * maxSteps));

        List<float[]> pts = new ArrayList<>();
        for (int i = 0; i <= steps; i++) {
            double a = a0 + (sweep * i / steps);
            float x = (float)(cx + r * Math.cos(a));
            float y = (float)(cy + r * Math.sin(a));
            pts.add(new float[]{x, y});
        }
        return pts;
    }

    private void drawCircleOutline(PDPageContentStream cs, float cx, float cy, float r) throws IOException {
        cs.setStrokingColor(new Color(0, 0, 0));

        int steps = 140;
        cs.moveTo(cx + r, cy);
        for (int i = 1; i <= steps; i++) {
            double a = (2.0 * Math.PI * i) / steps;
            cs.lineTo((float)(cx + r * Math.cos(a)), (float)(cy + r * Math.sin(a)));
        }
        cs.closePath();
        cs.stroke();
    }

    private int[] palette(int idx) {
        int[][] p = {
            {52, 152, 219},
            {46, 204, 113},
            {231, 76, 60},
            {155, 89, 182},
            {241, 196, 15},
            {26, 188, 156},
            {230, 126, 34},
            {127, 140, 141},
            {22, 160, 133},
            {41, 128, 185},
            {142, 68, 173},
            {192, 57, 43}
        };
        return p[idx % p.length];
    }

    private String safePdfText(String s) {
        if (s == null) return "";
        return s.replace("\r", " ").replace("\n", " ");
    }
    private void compileLatexToPdf(Path texFile) throws IOException, InterruptedException {

        Path workingDir = texFile.getParent();
        String fileName = texFile.getFileName().toString();

        ProcessBuilder pb = new ProcessBuilder(
            "pdflatex",
            "-interaction=nonstopmode",
            fileName
        );

        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);

        // 1ère compilation
        Process process1 = pb.start();
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process1.getInputStream()))) {
            while (reader.readLine() != null) {}
        }
        int exit1 = process1.waitFor();
        if (exit1 != 0) throw new RuntimeException("LaTeX compilation failed (pass 1)");

        // 2ème compilation (TOC / refs / figures)
        Process process2 = pb.start();
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process2.getInputStream()))) {
            while (reader.readLine() != null) {}
        }
        int exit2 = process2.waitFor();
        if (exit2 != 0) throw new RuntimeException("LaTeX compilation failed (pass 2)");
    }

}
