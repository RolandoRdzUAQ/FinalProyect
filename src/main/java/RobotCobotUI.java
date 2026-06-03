import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.*;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RobotCobotUI extends Application {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Connection dbConn;

    private Rotate joint1Sim, joint2Sim, joint3Sim;
    private Rotate joint1Real, joint2Real, joint3Real;

    private TextArea logArea;
    private Slider sliderVelocidad, sliderTolerancia;
    private TextField targetX, targetY, targetZ;
    private ComboBox<String> comboSecuencias;

    private List<double[]> puntosSecuenciaTemp = new ArrayList<>();

    private final String BG_COLOR = "#2B2B2B";
    private final String PANEL_COLOR = "#3C3F41";
    private final String TEXT_COLOR = "#A9B7C6";
    private final String ACCENT_COLOR = "#4C708C";
    private final String SUCCESS_COLOR = "#2E7D32"; 
    private final String BORDER_COLOR = "#555555";

    @Override
    public void start(Stage primaryStage) {
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setStyle("-fx-control-inner-background: #1E1E1E; -fx-text-fill: #00FF00; -fx-font-family: 'Monospaced';");
        logArea.setPrefHeight(120);

        initDatabase();
        conectarEmulador();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG_COLOR + ";");

        Label titleLabel = new Label("HMI CONTROL DE COBOT - 3 GDL");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        titleLabel.setTextFill(Color.web(TEXT_COLOR));
        HBox header = new HBox(titleLabel);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(15));
        header.setStyle("-fx-background-color: #232527; -fx-border-width: 0 0 2 0; -fx-border-color: " + ACCENT_COLOR + ";");
        root.setTop(header);

        VBox view3DBox = new VBox(10);
        view3DBox.setAlignment(Pos.CENTER);
        view3DBox.setPadding(new Insets(15));

        Label simLabel = new Label("SIMULACIÓN (IDEAL) vs HARDWARE (REAL)");
        simLabel.setStyle("-fx-text-fill: " + TEXT_COLOR + "; -fx-font-weight: bold;");
        
        HBox renders = new HBox(15);
        renders.setAlignment(Pos.CENTER);
        
        StackPane viewSim = create3DScene(true, Color.web("#4A90E2"));
        StackPane viewReal = create3DScene(false, Color.web("#50E3C2"));
        
        HBox.setHgrow(viewSim, Priority.ALWAYS);
        HBox.setHgrow(viewReal, Priority.ALWAYS);
        renders.getChildren().addAll(viewSim, viewReal);
        VBox.setVgrow(renders, Priority.ALWAYS);
        
        view3DBox.getChildren().addAll(simLabel, renders);

        VBox controlPanel = new VBox(15);
        controlPanel.setPadding(new Insets(20));
        controlPanel.setStyle("-fx-background-color: " + PANEL_COLOR + ";");

        VBox targetBox = createStyledSection("OBJETIVO (MODO TOOL)");
        targetX = createStyledTextField("X (ej. 100)"); targetX.setText("100");
        targetY = createStyledTextField("Y (ej. 150)"); targetY.setText("150");
        targetZ = createStyledTextField("Z (ej. 50)");  targetZ.setText("50");
        targetBox.getChildren().addAll(new Label("Coordenada X:"), targetX, new Label("Coordenada Y:"), targetY, new Label("Coordenada Z:"), targetZ);

        Button btnMover = new Button("EJECUTAR PUNTO");
        btnMover.setMaxWidth(Double.MAX_VALUE);
        btnMover.setStyle("-fx-background-color: " + ACCENT_COLOR + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8; -fx-cursor: hand;");
        btnMover.setOnAction(e -> ejecutarPuntoManual());

        targetBox.getChildren().add(btnMover);

        VBox secuenciaBox = createStyledSection("PROGRAMACIÓN DE SECUENCIAS");
        
        Button btnGuardarPunto = new Button("GRABAR PUNTO ACTUAL (+)");
        btnGuardarPunto.setMaxWidth(Double.MAX_VALUE);
        btnGuardarPunto.setStyle("-fx-background-color: " + SUCCESS_COLOR + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        btnGuardarPunto.setOnAction(e -> grabarPuntoTemporal());

        TextField nombreSecuencia = createStyledTextField("Nombre de nueva secuencia...");
        Button btnGuardarSecuencia = new Button("GUARDAR SECUENCIA EN BD");
        btnGuardarSecuencia.setMaxWidth(Double.MAX_VALUE);
        btnGuardarSecuencia.setStyle("-fx-background-color: #D35400; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        btnGuardarSecuencia.setOnAction(e -> guardarSecuenciaSQL(nombreSecuencia.getText()));

        comboSecuencias = new ComboBox<>();
        comboSecuencias.setMaxWidth(Double.MAX_VALUE);
        comboSecuencias.setStyle("-fx-background-color: #45494A;");
        actualizarListaSecuencias(); 

        Button btnReproducir = new Button("▶ REPRODUCIR SECUENCIA");
        btnReproducir.setMaxWidth(Double.MAX_VALUE);
        btnReproducir.setStyle("-fx-background-color: #8E44AD; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10; -fx-cursor: hand;");
        btnReproducir.setOnAction(e -> reproducirSecuencia(comboSecuencias.getValue()));

        secuenciaBox.getChildren().addAll(
            btnGuardarPunto, 
            new Separator(), 
            nombreSecuencia, 
            btnGuardarSecuencia, 
            new Separator(), 
            new Label("Secuencias Guardadas:"), 
            comboSecuencias, 
            btnReproducir
        );

        VBox settingsBox = createStyledSection("PARÁMETROS DE EJECUCIÓN");
        sliderVelocidad = new Slider(0.1, 5.0, 2.0); styleSlider(sliderVelocidad);
        sliderTolerancia = new Slider(0.1, 3.0, 0.5); styleSlider(sliderTolerancia);
        settingsBox.getChildren().addAll(new Label("Tiempo por punto (s):"), sliderVelocidad, new Label("Tolerancia Jacobiana (mm):"), sliderTolerancia);

        controlPanel.getChildren().addAll(targetBox, secuenciaBox, settingsBox);

        ScrollPane rightScroll = new ScrollPane(controlPanel);
        rightScroll.setFitToWidth(true);
        rightScroll.setMinWidth(300);
        rightScroll.setStyle("-fx-background: " + PANEL_COLOR + ";");

        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(view3DBox, rightScroll);
        splitPane.setDividerPositions(0.70);
        splitPane.setStyle("-fx-background-color: " + BG_COLOR + "; -fx-box-border: transparent;");
        root.setCenter(splitPane);

        VBox bottomPanel = new VBox(5);
        bottomPanel.setPadding(new Insets(10));
        bottomPanel.setStyle("-fx-background-color: " + BG_COLOR + ";");
        Label consoleLabel = new Label("CONSOLA DEL SISTEMA / SQL LOGS");
        consoleLabel.setStyle("-fx-text-fill: " + TEXT_COLOR + "; -fx-font-weight: bold;");
        bottomPanel.getChildren().addAll(consoleLabel, logArea);
        root.setBottom(bottomPanel);

        Scene scene = new Scene(root, 1200, 750);
        primaryStage.setTitle("HMI Cobot - Sistema de Control y Visión");
        primaryStage.setMinWidth(800); 
        primaryStage.setMinHeight(500);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void ejecutarPuntoManual() {
        try {
            double x = Double.parseDouble(targetX.getText());
            double y = Double.parseDouble(targetY.getText());
            double z = Double.parseDouble(targetZ.getText());
            calcularYEnviar(x, y, z);
        } catch (NumberFormatException ex) {
            log("ERROR: Coordenadas inválidas.");
        }
    }

    private void grabarPuntoTemporal() {
        try {
            double x = Double.parseDouble(targetX.getText());
            double y = Double.parseDouble(targetY.getText());
            double z = Double.parseDouble(targetZ.getText());
            puntosSecuenciaTemp.add(new double[]{x, y, z});
            log(String.format(">> Punto grabado en memoria temporal: [X:%.1f, Y:%.1f, Z:%.1f] (Puntos totales: %d)", x, y, z, puntosSecuenciaTemp.size()));
        } catch (NumberFormatException ex) {
            log("ERROR: Asegúrate de tener coordenadas válidas antes de grabar.");
        }
    }

    private void guardarSecuenciaSQL(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            log("ERROR: Debes darle un nombre a la secuencia.");
            return;
        }
        if (puntosSecuenciaTemp.isEmpty()) {
            log("ERROR: No hay puntos grabados para guardar.");
            return;
        }

        try {
            PreparedStatement pstmt = dbConn.prepareStatement("INSERT INTO secuencias (nombre_secuencia, paso_orden, target_x, target_y, target_z) VALUES (?, ?, ?, ?, ?)");
            for (int i = 0; i < puntosSecuenciaTemp.size(); i++) {
                double[] p = puntosSecuenciaTemp.get(i);
                pstmt.setString(1, nombre);
                pstmt.setInt(2, i + 1);
                pstmt.setDouble(3, p[0]);
                pstmt.setDouble(4, p[1]);
                pstmt.setDouble(5, p[2]);
                pstmt.executeUpdate();
            }
            log(">> Secuencia '" + nombre + "' guardada en SQL con " + puntosSecuenciaTemp.size() + " puntos.");
            puntosSecuenciaTemp.clear(); 
            actualizarListaSecuencias(); 
        } catch (SQLException e) {
            log("ERROR SQL al guardar secuencia: " + e.getMessage());
        }
    }

    private void actualizarListaSecuencias() {
        if (dbConn == null) return;
        try {
            ObservableList<String> nombres = FXCollections.observableArrayList();
            Statement stmt = dbConn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT DISTINCT nombre_secuencia FROM secuencias");
            while (rs.next()) {
                nombres.add(rs.getString("nombre_secuencia"));
            }
            comboSecuencias.setItems(nombres);
            if (!nombres.isEmpty()) comboSecuencias.setValue(nombres.get(0));
        } catch (SQLException e) {
            log("ERROR SQL al leer secuencias.");
        }
    }

    private void reproducirSecuencia(String nombre) {
        if (nombre == null || nombre.isEmpty()) return;
        
        new Thread(() -> {
            try {
                PreparedStatement pstmt = dbConn.prepareStatement("SELECT target_x, target_y, target_z FROM secuencias WHERE nombre_secuencia = ? ORDER BY paso_orden ASC");
                pstmt.setString(1, nombre);
                ResultSet rs = pstmt.executeQuery();

                Platform.runLater(() -> log("============== INICIANDO SECUENCIA: " + nombre + " =============="));

                int paso = 1;
                while (rs.next()) {
                    double x = rs.getDouble("target_x");
                    double y = rs.getDouble("target_y");
                    double z = rs.getDouble("target_z");
                    
                    int currentPaso = paso;
                    Platform.runLater(() -> {
                        log("--> Ejecutando Paso " + currentPaso);
                        calcularYEnviar(x, y, z);
                    });

                    long esperaMs = (long) (sliderVelocidad.getValue() * 1000) + 500;
                    Thread.sleep(esperaMs);
                    paso++;
                }
                Platform.runLater(() -> log("============== SECUENCIA FINALIZADA =============="));

            } catch (SQLException | InterruptedException e) {
                Platform.runLater(() -> log("ERROR al reproducir secuencia: " + e.getMessage()));
            }
        }).start();
    }

    private void initDatabase() {
        try { 
            dbConn = DriverManager.getConnection("jdbc:sqlite:robot_logs.db"); 
            Statement stmt = dbConn.createStatement();
            
            stmt.execute("CREATE TABLE IF NOT EXISTS system_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, message TEXT, timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)"); 
            
            stmt.execute("CREATE TABLE IF NOT EXISTS secuencias (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre_secuencia TEXT, paso_orden INTEGER, target_x REAL, target_y REAL, target_z REAL)"); 

            log(">> Módulos SQLite Inicializados Correctamente."); 
        } catch (SQLException e) { 
            System.err.println("Error DB: " + e.getMessage());
        }
    }

    private VBox createStyledSection(String title) {
        VBox box = new VBox(8); box.setPadding(new Insets(15));
        box.setStyle("-fx-border-color: " + BORDER_COLOR + "; -fx-border-radius: 5; -fx-background-color: #323436; -fx-background-radius: 5;");
        Label titleLabel = new Label(title); titleLabel.setStyle("-fx-text-fill: " + ACCENT_COLOR + "; -fx-font-weight: bold; -fx-font-size: 13px;");
        box.getChildren().add(titleLabel);
        for (javafx.scene.Node node : box.getChildren()) { if (node instanceof Label && node != titleLabel) node.setStyle("-fx-text-fill: " + TEXT_COLOR + ";"); }
        return box;
    }

    private TextField createStyledTextField(String prompt) {
        TextField tf = new TextField(); tf.setPromptText(prompt);
        tf.setStyle("-fx-background-color: #45494A; -fx-text-fill: #FFFFFF; -fx-prompt-text-fill: #888888; -fx-border-color: " + BORDER_COLOR + "; -fx-border-radius: 3; -fx-padding: 8;");
        return tf;
    }

    private void styleSlider(Slider slider) {
        slider.setShowTickLabels(true); slider.setShowTickMarks(true); slider.setMajorTickUnit(1.0); slider.setBlockIncrement(0.1);
        slider.setStyle("-fx-control-inner-background: " + ACCENT_COLOR + ";");
    }

    private StackPane create3DScene(boolean isSimulated, Color color) {
        Group root3D = new Group();
        
        Box suelo = new Box(1000, 2, 1000); suelo.setTranslateY(0); suelo.setMaterial(new PhongMaterial(Color.web("#1A1A1A")));
        Group ejes = createAxes(150);
        Group robot = buildRobot(isSimulated, color);
        root3D.getChildren().addAll(suelo, ejes, robot);

        AmbientLight luzAmbiente = new AmbientLight(Color.rgb(180, 180, 180));
        PointLight luzPuntual = new PointLight(Color.WHITE);
        luzPuntual.setTranslateX(200); luzPuntual.setTranslateY(-300); luzPuntual.setTranslateZ(-600);
        root3D.getChildren().addAll(luzAmbiente, luzPuntual);

        Rotate rotateX = new Rotate(-20, Rotate.X_AXIS);
        Rotate rotateY = new Rotate(35, Rotate.Y_AXIS);
        root3D.getTransforms().addAll(rotateX, rotateY);

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setTranslateZ(-800); camera.setTranslateY(-150); camera.setNearClip(0.1); camera.setFarClip(3000.0);

        SubScene subScene = new SubScene(root3D, 300, 300, true, SceneAntialiasing.DISABLED);
        subScene.setFill(Color.web("#232527")); 
        subScene.setCamera(camera);

        StackPane container = new StackPane(subScene);
        container.setMinSize(200, 200); 
        container.setStyle("-fx-background-color: #232527; -fx-border-color: " + BORDER_COLOR + "; -fx-border-width: 1; -fx-border-radius: 5;");
        
        container.widthProperty().addListener((obs, oldVal, newVal) -> subScene.setWidth(Math.max(100, newVal.doubleValue()) - 2));
        container.heightProperty().addListener((obs, oldVal, newVal) -> subScene.setHeight(Math.max(100, newVal.doubleValue()) - 2));
        
        double[] mouseOldX = {0}; double[] mouseOldY = {0};
        container.setOnMousePressed((MouseEvent event) -> { mouseOldX[0] = event.getSceneX(); mouseOldY[0] = event.getSceneY(); });
        container.setOnMouseDragged((MouseEvent event) -> {
            double deltaX = event.getSceneX() - mouseOldX[0], deltaY = event.getSceneY() - mouseOldY[0];
            rotateY.setAngle(rotateY.getAngle() - deltaX * 0.4); rotateX.setAngle(rotateX.getAngle() + deltaY * 0.4);
            mouseOldX[0] = event.getSceneX(); mouseOldY[0] = event.getSceneY();
        });

        return container;
    }

    private Group createAxes(double longitud) {
        Group ejes = new Group(); double grosor = 2.0;
        Box xAxis = new Box(longitud, grosor, grosor); xAxis.setTranslateX(longitud / 2.0); xAxis.setMaterial(new PhongMaterial(Color.RED));
        Box yAxis = new Box(grosor, longitud, grosor); yAxis.setTranslateY(-longitud / 2.0); yAxis.setMaterial(new PhongMaterial(Color.GREEN));
        Box zAxis = new Box(grosor, grosor, longitud); zAxis.setTranslateZ(longitud / 2.0); zAxis.setMaterial(new PhongMaterial(Color.BLUE));
        Sphere origen = new Sphere(5); origen.setMaterial(new PhongMaterial(Color.WHITE));
        ejes.getChildren().addAll(origen, xAxis, yAxis, zAxis);
        return ejes;
    }

    private Group buildRobot(boolean isSimulated, Color color) {
        Group rootRobot = new Group();
        PhongMaterial matArts = new PhongMaterial(Color.web("#808080"));
        PhongMaterial matEslabones = new PhongMaterial(color);
        PhongMaterial matHerramienta = new PhongMaterial(Color.web("#E74C3C"));

        double h1 = 120, rad1 = 12; double h2 = 100, rad2 = 10; double h3 = 80, rad3 = 8;

        Group j1Group = new Group(); Rotate rot1 = new Rotate(0, Rotate.Y_AXIS); j1Group.getTransforms().add(rot1); rootRobot.getChildren().add(j1Group);
        Sphere nodoBase = new Sphere(18); nodoBase.setMaterial(matArts);
        Cylinder eslabon1 = new Cylinder(rad1, h1); eslabon1.setMaterial(matEslabones); eslabon1.setTranslateY(-h1 / 2.0);
        j1Group.getChildren().addAll(nodoBase, eslabon1);

        Group j2Group = new Group(); j2Group.setTranslateY(-h1); Rotate rot2 = new Rotate(0, Rotate.Z_AXIS); j2Group.getTransforms().add(rot2); j1Group.getChildren().add(j2Group); 
        Sphere nodoHombro = new Sphere(14); nodoHombro.setMaterial(matArts);
        Cylinder eslabon2 = new Cylinder(rad2, h2); eslabon2.setMaterial(matEslabones); eslabon2.setTranslateY(-h2 / 2.0);
        j2Group.getChildren().addAll(nodoHombro, eslabon2);

        Group j3Group = new Group(); j3Group.setTranslateY(-h2); Rotate rot3 = new Rotate(0, Rotate.Z_AXIS); j3Group.getTransforms().add(rot3); j2Group.getChildren().add(j3Group); 
        Sphere nodoCodo = new Sphere(12); nodoCodo.setMaterial(matArts);
        Cylinder eslabon3 = new Cylinder(rad3, h3); eslabon3.setMaterial(matEslabones); eslabon3.setTranslateY(-h3 / 2.0);
        j3Group.getChildren().addAll(nodoCodo, eslabon3);

        Sphere efectorFinal = new Sphere(10); efectorFinal.setMaterial(matHerramienta); efectorFinal.setTranslateY(-h3);
        j3Group.getChildren().add(efectorFinal);

        if (isSimulated) { joint1Sim = rot1; joint2Sim = rot2; joint3Sim = rot3; } else { joint1Real = rot1; joint2Real = rot2; joint3Real = rot3; }
        return rootRobot;
    }

    private double[] cinematicaDirecta(double t1, double t2, double t3) {
        double rad1 = Math.toRadians(t1), rad2 = Math.toRadians(t2), rad3 = Math.toRadians(t3);
        double r = 100.0 * Math.sin(rad2) + 80.0 * Math.sin(rad2 + rad3);
        return new double[]{ Math.cos(rad1) * r, 120.0 + 100.0 * Math.cos(rad2) + 80.0 * Math.cos(rad2 + rad3), -Math.sin(rad1) * r };
    }

    private double[] calcularCinematicaInversa(double targetX, double targetY, double targetZ) {
        double q1 = joint1Sim.getAngle(), q2 = joint2Sim.getAngle(), q3 = joint3Sim.getAngle();
        double tolerancia = sliderTolerancia.getValue(); 
        for (int i = 0; i < 2500; i++) {
            double[] currentPos = cinematicaDirecta(q1, q2, q3);
            double errX = targetX - currentPos[0], errY = targetY - currentPos[1], errZ = targetZ - currentPos[2];
            double errorDistancia = Math.sqrt(errX*errX + errY*errY + errZ*errZ);
            
            if (errorDistancia < tolerancia) {
                log(String.format(">> IK Convergió | Iteraciones: %d | Error: %.2f mm", i, errorDistancia)); break;
            }

            double delta = 0.1;
            double[] pdq1 = cinematicaDirecta(q1 + delta, q2, q3), pdq2 = cinematicaDirecta(q1, q2 + delta, q3), pdq3 = cinematicaDirecta(q1, q2, q3 + delta);

            double J11 = (pdq1[0]-currentPos[0])/delta, J21 = (pdq1[1]-currentPos[1])/delta, J31 = (pdq1[2]-currentPos[2])/delta;
            double J12 = (pdq2[0]-currentPos[0])/delta, J22 = (pdq2[1]-currentPos[1])/delta, J32 = (pdq2[2]-currentPos[2])/delta;
            double J13 = (pdq3[0]-currentPos[0])/delta, J23 = (pdq3[1]-currentPos[1])/delta, J33 = (pdq3[2]-currentPos[2])/delta;

            double dq1 = J11*errX + J21*errY + J31*errZ, dq2 = J12*errX + J22*errY + J32*errZ, dq3 = J13*errX + J23*errY + J33*errZ;
            double norm = Math.sqrt(dq1*dq1 + dq2*dq2 + dq3*dq3);
            if (norm > 2.0) { dq1 = (dq1/norm)*2.0; dq2 = (dq2/norm)*2.0; dq3 = (dq3/norm)*2.0; }
            q1 += dq1; q2 += dq2; q3 += dq3;
        }
        return new double[]{q1, q2, q3};
    }

    private void calcularYEnviar(double x, double y, double z) {
        double[] t = calcularCinematicaInversa(x, y, z);
        
        new Timeline(new KeyFrame(Duration.seconds(sliderVelocidad.getValue()),
            new KeyValue(joint1Sim.angleProperty(), t[0]), new KeyValue(joint2Sim.angleProperty(), t[1]), new KeyValue(joint3Sim.angleProperty(), t[2]))
        ).play();

        String comando = String.format("SET_ANGLES:%.2f,%.2f,%.2f", t[0], t[1], t[2]);
        logDatabase("MOVE_CMD", comando);
        if (out != null) out.println(comando);
    }

    private void conectarEmulador() {
        new Thread(() -> {
            try {
                socket = new Socket("127.0.0.1", 5000); out = new PrintWriter(socket.getOutputStream(), true); in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                Platform.runLater(() -> log(">> Socket TCP Conectado (Emulador STM32)"));
                String response; while ((response = in.readLine()) != null) { String fr = response; Platform.runLater(() -> procesarFeedback(fr)); }
            } catch (IOException e) { Platform.runLater(() -> log(">> Advertencia: No se encontró emulador STM32.")); }
        }).start();
    }

    private void procesarFeedback(String data) {
        if(data.startsWith("FEEDBACK:")) {
            String[] p = data.split(":")[1].split(",");
            new Timeline(new KeyFrame(Duration.seconds(0.5),
                new KeyValue(joint1Real.angleProperty(), Double.parseDouble(p[0])), new KeyValue(joint2Real.angleProperty(), Double.parseDouble(p[1])), new KeyValue(joint3Real.angleProperty(), Double.parseDouble(p[2])))
            ).play();
        }
    }

    private void logDatabase(String type, String message) {
        if (dbConn == null) return;
        try { PreparedStatement p = dbConn.prepareStatement("INSERT INTO system_logs (type, message) VALUES (?, ?)"); p.setString(1, type); p.setString(2, message); p.executeUpdate(); } catch (SQLException e) { }
    }

    private void log(String msg) { logArea.appendText(msg + "\n"); }
    public static void main(String[] args) { launch(args); }
}
