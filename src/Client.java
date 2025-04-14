import java.io.*;
import java.net.Socket;
import java.util.Random;
import com.google.gson.Gson;

public class Client {
    public static void main(String[] args) {
        String serverAddress = "localhost";
        int port = 5000;
        Random random = new Random();
        Gson gson = new Gson();

        while (true) {
            try (Socket socket = new Socket(serverAddress, port);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

                InputData data = new InputData();
                int rows = 2 + random.nextInt(5);
                int cols = 2 + random.nextInt(5);
                data.matrix = new int[rows][cols];

                System.out.println("Згенерована матриця (" + rows + "x" + cols + "):");
                for (int i = 0; i < rows; i++) {
                    for (int j = 0; j < cols; j++) {
                        data.matrix[i][j] = random.nextInt(100);
                        System.out.printf("%3d ", data.matrix[i][j]);
                    }
                    System.out.println();
                }

                data.threadCount = 1 + random.nextInt(rows);
                System.out.println("Кількість потоків: " + data.threadCount);

                sendCommand(writer, gson, "SEND_DATA", data);
                System.out.println("Сервер відповів: " + reader.readLine());

                sendCommand(writer, gson, "GET_STATUS", null);
                System.out.println("Попередній статус: " + reader.readLine());

                sendCommand(writer, gson, "GET_RESULT", null);
                System.out.println("Попередній результат: " + reader.readLine());

                sendCommand(writer, gson, "START_COMPUTE", null);
                System.out.println("Сервер відповів: " + reader.readLine());

                sendCommand(writer, gson, "START_COMPUTE", null);
                System.out.println("Повторна спроба START_COMPUTE: " + reader.readLine());

                String status = "";
                while (true) {
                    Thread.sleep(500);
                    sendCommand(writer, gson, "GET_STATUS", null);
                    status = reader.readLine();
                    System.out.println("Статус обчислень: " + status);
                    if ("done".equals(status) || "error".equals(status)) break;
                }

                sendCommand(writer, gson, "GET_RESULT", null);
                String response = reader.readLine();
                try {
                    OutputData result = gson.fromJson(response, OutputData.class);
                    System.out.println("Мінімум: " + result.min);
                    System.out.println("Максимум: " + result.max);
                } catch (Exception e) {
                    System.out.println("Результат ще не готовий або сталася помилка: " + response);
                }

                break;
            } catch (IOException | InterruptedException e) {
                System.err.println("Помилка з'єднання з сервером: " + e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private static void sendCommand(BufferedWriter writer, Gson gson, String command, InputData data) throws IOException {
        Request req = new Request();
        req.command = command;
        req.data = data;
        writer.write(gson.toJson(req) + "\n");
        writer.flush();
    }
}
