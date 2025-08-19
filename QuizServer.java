import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.*;

public class QuizServer {

    static Map<String, String[]> questionsMap = new HashMap<>();
    static Map<String, String[][]> optionsMap = new HashMap<>();
    static Map<String, int[]> answersMap = new HashMap<>();

    // Leaderboard: topic -> list of username-score
    static Map<String, List<String>> leaderboardMap = new HashMap<>();

    public static void main(String[] args) throws IOException {
        loadQuestions();

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        System.out.println("Server running at http://localhost:8080/");

        server.createContext("/", new StaticFileHandler("index.html")); // login page
        server.createContext("/quiz", new QuizHandler()); // quiz handler
        server.createContext("/leaderboard", new LeaderboardHandler()); // leaderboard handler
        server.createContext("/style.css", new StaticFileHandler("style.css"));

        server.setExecutor(null);
        server.start();
    }

    private static void loadQuestions() {
        // General Knowledge
        questionsMap.put("GK", new String[]{
                "What is the capital of France?",
                "Which planet is known as the Red Planet?",
                "Who is known as the Father of the Nation in India?",
                "Which is the largest ocean on Earth?",
                "Who invented the telephone?"
        });
        optionsMap.put("GK", new String[][]{
                {"Paris", "London", "Rome", "Berlin"},
                {"Earth", "Mars", "Jupiter", "Venus"},
                {"Mahatma Gandhi", "Jawaharlal Nehru", "Subhash Chandra Bose", "B. R. Ambedkar"},
                {"Atlantic", "Pacific", "Indian", "Arctic"},
                {"Alexander Graham Bell", "Edison", "Newton", "Tesla"}
        });
        answersMap.put("GK", new int[]{0, 1, 0, 1, 0});

        // Software
        questionsMap.put("Software", new String[]{
                "Who developed Java?",
                "Which keyword is used to inherit a class in Java?",
                "Which company developed Windows OS?",
                "HTML stands for?",
                "What does CSS stand for?"
        });
        optionsMap.put("Software", new String[][]{
                {"Microsoft", "Sun Microsystems", "Google", "Apple"},
                {"implements", "extends", "inherits", "super"},
                {"Apple", "Google", "Microsoft", "IBM"},
                {"HyperText Markup Language", "HighText Machine Language", "Hyperlinks and Text Markup Language", "Home Tool Markup Language"},
                {"Cascading Style Sheets", "Colorful Style Sheets", "Computer Style Sheets", "Creative Style Sheets"}
        });
        answersMap.put("Software", new int[]{1, 1, 2, 0, 0});

        // Sports
        questionsMap.put("Sports", new String[]{
                "How many players are there in a football team?",
                "Who is called the 'God of Cricket'?",
                "Which country hosted the 2016 Summer Olympics?",
                "In which sport is the term 'Love' used?",
                "Who has won the most Olympic gold medals?"
        });
        optionsMap.put("Sports", new String[][]{
                {"9", "10", "11", "12"},
                {"Sachin Tendulkar", "Virat Kohli", "MS Dhoni", "Brian Lara"},
                {"China", "Brazil", "UK", "USA"},
                {"Football", "Tennis", "Hockey", "Cricket"},
                {"Michael Phelps", "Usain Bolt", "Carl Lewis", "Mark Spitz"}
        });
        answersMap.put("Sports", new int[]{2, 0, 1, 1, 0});
    }

    // Handles quiz logic
    static class QuizHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            Map<String, String> params = new HashMap<>();

            if ("POST".equals(method)) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                BufferedReader br = new BufferedReader(isr);
                String formData = br.readLine();
                if (formData != null) {
                    String[] pairs = formData.split("&");
                    for (String pair : pairs) {
                        String[] keyValue = pair.split("=");
                        String key = URLDecoder.decode(keyValue[0], "UTF-8");
                        String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], "UTF-8") : "";
                        params.put(key, value);
                    }
                }
            }

            String username = params.getOrDefault("username", "");
            String topic = params.getOrDefault("topic", "");
            int current = Integer.parseInt(params.getOrDefault("qIndex", "0"));
            int score = Integer.parseInt(params.getOrDefault("score", "0"));

            String response;

            if (username.isEmpty() || topic.isEmpty()) {
                response = readFile("index.html"); // back to login
            } else {
                String[] questions = questionsMap.get(topic);
                String[][] options = optionsMap.get(topic);
                int[] answers = answersMap.get(topic);

                if (params.containsKey("answer") && current > 0) {
                    int selected = Integer.parseInt(params.get("answer"));
                    if (selected == answers[current - 1]) {
                        score++;
                    }
                }

                if (current >= questions.length) {
                    // add to leaderboard
                    leaderboardMap.putIfAbsent(topic, new ArrayList<>());
                    leaderboardMap.get(topic).add(username + " - " + score + "/" + questions.length);

                    // show result page with leaderboard button
                    response = readFile("result.html")
                            .replace("{{username}}", username)
                            .replace("{{score}}", score + "/" + questions.length)
                            .replace("{{leaderboardButton}}",
                                    "<form action='/leaderboard' method='get'>" +
                                            "<input type='hidden' name='topic' value='" + topic + "'>" +
                                            "<button type='submit'>View Leaderboard</button>" +
                                            "</form>");
                } else {
                    // show next question
                    StringBuilder opts = new StringBuilder();
                    for (int i = 0; i < options[current].length; i++) {
                        opts.append("<label><input type='radio' name='answer' value='")
                                .append(i).append("' required> ")
                                .append(options[current][i]).append("</label><br>");
                    }

                    response = readFile("quiz.html")
                            .replace("{{username}}", username)
                            .replace("{{topic}}", topic)
                            .replace("{{qIndex}}", String.valueOf(current + 1))
                            .replace("{{score}}", String.valueOf(score))
                            .replace("{{question}}", questions[current])
                            .replace("{{options}}", opts.toString())
                            .replace("{{buttonText}}", (current == questions.length - 1 ? "Submit" : "Next"));
                }
            }

            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    // Leaderboard handler
    static class LeaderboardHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = new HashMap<>();
            String query = exchange.getRequestURI().getQuery(); // ?topic=GK
            if (query != null) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=");
                    params.put(URLDecoder.decode(keyValue[0], "UTF-8"),
                            keyValue.length > 1 ? URLDecoder.decode(keyValue[1], "UTF-8") : "");
                }
            }

            String topic = params.getOrDefault("topic", "GK");
            List<String> lbList = leaderboardMap.getOrDefault(topic, new ArrayList<>());

            StringBuilder lb = new StringBuilder("<ul>");
            for (String entry : lbList) {
                lb.append("<li>").append(entry).append("</li>");
            }
            lb.append("</ul>");

            String response = "<h1>Leaderboard for " + topic + "</h1>"
                    + "<a href='/'>Back to Home</a><br><br>"
                    + lb.toString();

            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    // Serve static files (HTML/CSS)
    static class StaticFileHandler implements HttpHandler {
        private String filename;
        StaticFileHandler(String filename) {
            this.filename = filename;
        }
        public void handle(HttpExchange exchange) throws IOException {
            String response = readFile(filename);
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    private static String readFile(String fileName) throws IOException {
        File file = new File(fileName);
        if (!file.exists()) return "<h1>File not found</h1>";
        BufferedReader br = new BufferedReader(new FileReader(file));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }
}
