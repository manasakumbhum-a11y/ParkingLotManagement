
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Main {

    static class Vehicle {
        String number;
        String category;

        Vehicle(String number, String category) {
            this.number = number;
            this.category = category;
        }
    }

    static class Slot {
        String number;
        String category;
        boolean occupied = false;
        String vehicleNumber = "";

        Slot(String number, String category) {
            this.number = number;
            this.category = category;
        }
    }

    static Map<String, Vehicle> vehicles = new LinkedHashMap<>();
    static Map<String, Slot> slots = new LinkedHashMap<>();
    static Map<String, String> assignments = new LinkedHashMap<>();

    static int slotCounter = 1;

    public static void main(String[] args) throws Exception {

        HttpServer server = HttpServer.create(
            new InetSocketAddress(8080), 0
        );

        server.createContext("/", Main::handleRequest);

        server.setExecutor(null);
        server.start();

        System.out.println("Parking Lot Server Started!");
        System.out.println("Open http://localhost:8080");
    }

    static void handleRequest(HttpExchange exchange) throws IOException {

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {

            if (path.startsWith("/api/")) {

                Map<String, String> data = new HashMap<>();

                if (method.equalsIgnoreCase("POST")) {
                    data = readFormData(exchange);
                }

                String response;

                synchronized (Main.class) {

                    switch (path) {

                        case "/api/vehicles":
                            if (method.equals("POST")) {
                                response = registerVehicle(data);
                            } else {
                                response = getVehicles();
                            }
                            break;

                        case "/api/slots":
                            if (method.equals("POST")) {
                                response = addSlot(data);
                            } else {
                                response = getSlots();
                            }
                            break;

                        case "/api/entry":
                            response = enterVehicle(data);
                            break;

                        case "/api/exit":
                            response = exitVehicle(data);
                            break;

                        case "/api/dashboard":
                            response = getDashboard();
                            break;

                        case "/api/conflicts":
                            response = getConflicts();
                            break;

                        default:
                            send(exchange, 404,
                                "{\"message\":\"API endpoint not found\"}");
                            return;
                    }
                }

                send(exchange, 200, response);

            } else {
                serveFrontend(exchange, path);
            }

        } catch (Exception e) {
            send(exchange, 500,
                "{\"message\":\"Server error: " +
                escape(e.getMessage()) + "\"}");
        }
    }

    // Read HTML form data sent by JavaScript
    static Map<String, String> readFormData(
        HttpExchange exchange
    ) throws IOException {

        String body = new String(
            exchange.getRequestBody().readAllBytes(),
            StandardCharsets.UTF_8
        );

        Map<String, String> data = new HashMap<>();

        for (String pair : body.split("&")) {

            String[] parts = pair.split("=", 2);

            String key = URLDecoder.decode(
                parts[0], StandardCharsets.UTF_8
            );

            String value = parts.length > 1
                ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                : "";

            data.put(key, value);
        }

        return data;
    }

    // 1. Register vehicles
    static String registerVehicle(Map<String, String> data) {

        String number = data.getOrDefault("number", "")
            .trim().toUpperCase();

        String category = data.getOrDefault("category", "")
            .trim().toUpperCase();

        if (number.isEmpty()) {
            return message("Vehicle number is required.");
        }

        if (!category.equals("2W") && !category.equals("4W")) {
            return message("Category must be 2W or 4W.");
        }

        if (vehicles.containsKey(number)) {
            return message("Vehicle is already registered.");
        }

        vehicles.put(number, new Vehicle(number, category));

        return message("Vehicle registered successfully!");
    }

    static String getVehicles() {

        StringBuilder json = new StringBuilder("[");

        for (Vehicle v : vehicles.values()) {

            if (json.length() > 1) json.append(",");

            json.append("{")
                .append("\"number\":\"").append(escape(v.number)).append("\",")
                .append("\"category\":\"").append(v.category).append("\"")
                .append("}");
        }

        return json.append("]").toString();
    }

    // 2. Add parking slots
    static String addSlot(Map<String, String> data) {

        String category = data.getOrDefault("category", "")
            .trim().toUpperCase();

        if (!category.equals("2W") && !category.equals("4W")) {
            return message("Category must be 2W or 4W.");
        }

        String slotNumber = "S" + slotCounter++;

        slots.put(slotNumber, new Slot(slotNumber, category));

        return message("Parking slot " + slotNumber +
            " added successfully!");
    }

    static String getSlots() {

        StringBuilder json = new StringBuilder("[");

        for (Slot s : slots.values()) {

            if (json.length() > 1) json.append(",");

            json.append("{")
                .append("\"number\":\"").append(s.number).append("\",")
                .append("\"category\":\"").append(s.category).append("\",")
                .append("\"occupied\":").append(s.occupied).append(",")
                .append("\"vehicleNumber\":\"")
                .append(escape(s.vehicleNumber)).append("\"")
                .append("}");
        }

        return json.append("]").toString();
    }

    // 3. Vehicle entry and automatic slot assignment
    static String enterVehicle(Map<String, String> data) {

        String number = data.getOrDefault("number", "")
            .trim().toUpperCase();

        Vehicle vehicle = vehicles.get(number);

        if (vehicle == null) {
            return message("Please register the vehicle first.");
        }

        if (assignments.containsKey(number)) {
            return message("Vehicle is already parked.");
        }

        for (Slot slot : slots.values()) {

            if (!slot.occupied &&
                slot.category.equals(vehicle.category)) {

                slot.occupied = true;
                slot.vehicleNumber = number;

                assignments.put(number, slot.number);

                return message(
                    "Vehicle " + number +
                    " parked successfully in slot " + slot.number
                );
            }
        }

        return message("No available matching parking slot.");
    }

    // 4. Vehicle exit and slot release
    static String exitVehicle(Map<String, String> data) {

        String number = data.getOrDefault("number", "")
            .trim().toUpperCase();

        String slotNumber = assignments.get(number);

        if (slotNumber == null) {
            return message("This vehicle is not currently parked.");
        }

        Slot slot = slots.get(slotNumber);

        if (slot != null) {
            slot.occupied = false;
            slot.vehicleNumber = "";
        }

        assignments.remove(number);

        return message(
            "Vehicle " + number +
            " exited successfully. Slot " + slotNumber + " released."
        );
    }

    // 5. Detect category conflicts in existing assignments
    static String getConflicts() {

        StringBuilder json = new StringBuilder("[");

        for (Map.Entry<String, String> entry :
            assignments.entrySet()) {

            String vehicleNumber = entry.getKey();
            String slotNumber = entry.getValue();

            Vehicle vehicle = vehicles.get(vehicleNumber);
            Slot slot = slots.get(slotNumber);

            if (vehicle != null && slot != null &&
                !vehicle.category.equals(slot.category)) {

                if (json.length() > 1) json.append(",");

                json.append("{")
                    .append("\"vehicle\":\"")
                    .append(escape(vehicleNumber)).append("\",")
                    .append("\"vehicleCategory\":\"")
                    .append(vehicle.category).append("\",")
                    .append("\"slot\":\"")
                    .append(slotNumber).append("\",")
                    .append("\"slotCategory\":\"")
                    .append(slot.category).append("\"")
                    .append("}");
            }
        }

        return json.append("]").toString();
    }

    // 6. Dashboard counts
    static String getDashboard() {

        int available2W = 0;
        int occupied2W = 0;
        int available4W = 0;
        int occupied4W = 0;

        for (Slot s : slots.values()) {

            if (s.category.equals("2W")) {
                if (s.occupied) occupied2W++;
                else available2W++;
            }

            if (s.category.equals("4W")) {
                if (s.occupied) occupied4W++;
                else available4W++;
            }
        }

        return "{"
            + "\"available2W\":" + available2W + ","
            + "\"occupied2W\":" + occupied2W + ","
            + "\"available4W\":" + available4W + ","
            + "\"occupied4W\":" + occupied4W + ","
            + "\"totalVehicles\":" + vehicles.size() + ","
            + "\"parkedVehicles\":" + assignments.size()
            + "}";
    }

    // Serve frontend HTML, CSS and JavaScript files
    static void serveFrontend(
        HttpExchange exchange, String path
    ) throws IOException {

        if (path.equals("/")) path = "/index.html";

        Path root = Paths.get("frontend")
            .toAbsolutePath().normalize();

        Path file = root.resolve(path.substring(1))
            .normalize();

        if (!file.startsWith(root) || !Files.exists(file)
            || Files.isDirectory(file)) {

            send(exchange, 404, "File not found");
            return;
        }

        String contentType = "text/plain";

        if (path.endsWith(".html")) {
            contentType = "text/html; charset=UTF-8";
        } else if (path.endsWith(".css")) {
            contentType = "text/css; charset=UTF-8";
        } else if (path.endsWith(".js")) {
            contentType = "application/javascript; charset=UTF-8";
        }

        byte[] bytes = Files.readAllBytes(file);

        exchange.getResponseHeaders().set(
            "Content-Type", contentType
        );

        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    static String message(String text) {
        return "{\"message\":\"" + escape(text) + "\"}";
    }

    static String escape(String text) {

        if (text == null) return "";

        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r");
    }

    static void send(
        HttpExchange exchange, int status, String response
    ) throws IOException {

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
            "Content-Type", "application/json; charset=UTF-8"
        );

        exchange.sendResponseHeaders(status, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
