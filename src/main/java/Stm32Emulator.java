import java.io.*;
import java.net.*;

public class Stm32Emulator {
    public static void main(String[] args) {
        int port = 5000;
        System.out.println("Iniciando emulador STM32 en el puerto " + port + "...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Interfaz conectada.");
                
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("Recibido del software: " + inputLine);
                    
                    if (inputLine.startsWith("SET_ANGLES:")) {
                        Thread.sleep(500); 
                        
                        String[] angles = inputLine.split(":")[1].split(",");
                        double a1 = Double.parseDouble(angles[0]) + 0.1;
                        double a2 = Double.parseDouble(angles[1]) - 0.05;
                        double a3 = Double.parseDouble(angles[2]) + 0.02;
                        
                        String feedback = String.format("FEEDBACK:%.2f,%.2f,%.2f", a1, a2, a3);
                        out.println(feedback);
                        System.out.println("Enviando retroalimentación: " + feedback);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
