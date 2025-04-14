import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;

class InputData {
    int[][] matrix;
    int threadCount;
}

class OutputData {
    int min;
    int max;
    String status;
}

class Request {
    String command;
    InputData data;
}

public class Server {
    private static final int PORT = 5000;
    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Сервер запущено на порту " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Клієнт підключився: " + clientSocket.getInetAddress());
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Помилка запуску сервера: " + e.getMessage());
        }
    }

    private static void handleClient(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            OutputData result = new OutputData();
            AtomicBoolean isComputing = new AtomicBoolean(false);
            InputData receivedData = null;

            String line;
            while ((line = reader.readLine()) != null) {
                Request req = gson.fromJson(line, Request.class);
                System.out.println("Отримано команду: " + req.command);

                switch (req.command) {
                    case "SEND_DATA":
                        receivedData = req.data;
                        result.status = "pending";
                        writer.write("DATA_RECEIVED\n");
                        writer.flush();
                        System.out.println("Дані отримано від клієнта.");
                        break;

                    case "START_COMPUTE":
                        if (!isComputing.get() && receivedData != null) {
                            isComputing.set(true);
                            result.status = "computing";
                            writer.write("COMPUTE_STARTED\n");
                            writer.flush();
                            System.out.println("Розпочато обчислення.");
                            computeMinMax(receivedData, result, () -> isComputing.set(false));
                        } else {
                            writer.write("COMPUTE_ALREADY_RUNNING\n");
                            writer.flush();
                            System.out.println("Обчислення вже виконується або немає даних.");
                        }
                        break;

                    case "GET_STATUS":
                        writer.write(result.status + "\n");
                        writer.flush();
                        System.out.println("Надано статус: " + result.status);
                        break;

                    case "GET_RESULT":
                        if ("done".equals(result.status)) {
                            writer.write(gson.toJson(result) + "\n");
                            System.out.println("Надано результат обчислення.");
                        } else {
                            writer.write("RESULT_NOT_READY\n");
                            System.out.println("Результат ще не готовий.");
                        }
                        writer.flush();
                        break;

                    default:
                        writer.write("UNKNOWN_COMMAND\n");
                        writer.flush();
                        System.out.println("Невідома команда.");
                }
            }

            System.out.println("Завершено сесію з клієнтом: " + socket.getInetAddress());
        } catch (IOException e) {
            System.err.println("З'єднання з клієнтом розірвано або сталася помилка: " + e.getMessage());
        }
    }

    private static void computeMinMax(InputData inputData, OutputData result, Runnable onFinish) {
        new Thread(() -> {
            ExecutorService executor = Executors.newFixedThreadPool(inputData.threadCount);
            List<Future<int[]>> futures = new ArrayList<>();

            int totalRows = inputData.matrix.length;
            int rowsPerThread = (int) Math.ceil((double) totalRows / inputData.threadCount);

            for (int i = 0; i < inputData.threadCount; i++) {
                final int startRow = i * rowsPerThread;
                final int endRow = Math.min(startRow + rowsPerThread, totalRows);

                futures.add(executor.submit(() -> {
                    int localMin = Integer.MAX_VALUE;
                    int localMax = Integer.MIN_VALUE;
                    for (int row = startRow; row < endRow; row++) {
                        for (int value : inputData.matrix[row]) {
                            localMin = Math.min(localMin, value);
                            localMax = Math.max(localMax, value);
                        }
                    }
                    return new int[]{localMin, localMax};
                }));
            }

            int globalMin = Integer.MAX_VALUE;
            int globalMax = Integer.MIN_VALUE;

            try {
                for (Future<int[]> future : futures) {
                    int[] minMax = future.get();
                    globalMin = Math.min(globalMin, minMax[0]);
                    globalMax = Math.max(globalMax, minMax[1]);
                }
                result.min = globalMin;
                result.max = globalMax;
                result.status = "done";
                System.out.println("Обчислення завершено. Мінімум: " + globalMin + ", Максимум: " + globalMax);
            } catch (Exception e) {
                result.status = "error";
                System.err.println("Помилка при обчисленні: " + e.getMessage());
            }

            executor.shutdown();
            onFinish.run();
        }).start();
    }
}