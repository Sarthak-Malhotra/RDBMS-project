import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class Main {
    public static void main(String[] args) {
        String dbUrl = "jdbc:mysql://localhost:3306/project";
        String dbUsername = "root";
        String dbPassword = "root";

        Connection connection;
        try {
            connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
            System.out.println("✅ Connected to Database!");
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        Path frontendPath = Paths.get("frontend");
        if (!Files.exists(frontendPath)) {
            frontendPath = Paths.get("..", "frontend");
        }
        String resolvedFrontend = frontendPath.toAbsolutePath().normalize().toString();

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add(resolvedFrontend, Location.EXTERNAL);
        }).start(8080);

        System.out.println("🚀 Web Server running at http://localhost:8080");

        // --- PAGE ROUTES ---
        app.get("/user", ctx -> {
            Path userFile = Paths.get(resolvedFrontend, "user.html");
            if (Files.exists(userFile)) {
                ctx.contentType("text/html").result(Files.readString(userFile));
            } else {
                ctx.redirect("/user.html");
            }
        });

        // --- AUTHENTICATION ---
        app.post("/api/login", ctx -> {
            String identifier = ctx.formParam("username");
            if (identifier == null || identifier.trim().isEmpty()) {
                identifier = ctx.formParam("email");
            }
            String loginPassword = ctx.formParam("password");

            if (identifier == null || loginPassword == null || identifier.trim().isEmpty() || loginPassword.trim().isEmpty()) {
                ctx.status(400).result("Username/Email and Password are required");
                return;
            }

            String sql = "SELECT user_id, username, email FROM users WHERE (username = ? OR email = ?) AND password_hash = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, identifier.trim());
                pstmt.setString(2, identifier.trim());
                pstmt.setString(3, loginPassword);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> user = new HashMap<>();
                        user.put("id", rs.getInt("user_id"));
                        user.put("username", rs.getString("username"));
                        user.put("email", rs.getString("email"));
                        ctx.sessionAttribute("currentUser", user);
                        ctx.json(user);
                    } else {
                        ctx.status(401).result("Invalid username/email or password");
                    }
                }
            } catch (SQLException e) {
                ctx.status(500).result("Database error: " + e.getMessage());
            }
        });

        app.get("/api/me", ctx -> {
            Map<String, Object> user = ctx.sessionAttribute("currentUser");
            if (user != null) {
                ctx.json(user);
            } else {
                ctx.status(401).result("Not authenticated");
            }
        });

        app.post("/api/logout", ctx -> {
            try {
                ctx.req().getSession().invalidate();
            } catch (Exception ignored) {
            }
            ctx.result("Logged out");
        });

        // --- USERS ---
        app.get("/api/users", ctx -> {
            List<Map<String, Object>> list = new ArrayList<>();
            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", rs.getInt("user_id"));
                    map.put("username", rs.getString("username"));
                    map.put("email", rs.getString("email"));
                    list.add(map);
                }
            }
            ctx.json(list);
        });

        app.get("/api/users/{userId}/groups", ctx -> {
            int userId;
            try {
                userId = Integer.parseInt(ctx.pathParam("userId"));
            } catch (NumberFormatException e) {
                ctx.status(400).result("Invalid user ID");
                return;
            }

            List<Map<String, Object>> list = new ArrayList<>();
            String sql = "SELECT g.group_id, g.name, g.description, g.created_by, gm.role, gm.joined_at, u.username AS creator_username "
                    + "FROM group_members gm "
                    + "JOIN travel_groups g ON gm.group_id = g.group_id "
                    + "LEFT JOIN users u ON g.created_by = u.user_id "
                    + "WHERE gm.user_id = ? "
                    + "ORDER BY gm.joined_at DESC";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("groupId", rs.getInt("group_id"));
                        map.put("name", rs.getString("name"));
                        map.put("description", rs.getString("description"));
                        map.put("createdBy", rs.getInt("created_by"));
                        map.put("creatorUsername", rs.getString("creator_username"));
                        map.put("role", rs.getString("role"));
                        map.put("joinedAt", rs.getString("joined_at"));
                        list.add(map);
                    }
                }
            } catch (SQLException e) {
                ctx.status(500).result("Database error: " + e.getMessage());
                return;
            }
            ctx.json(list);
        });

        app.get("/api/users/{userId}/trips", ctx -> {
            int userId;
            try {
                userId = Integer.parseInt(ctx.pathParam("userId"));
            } catch (NumberFormatException e) {
                ctx.status(400).result("Invalid user ID");
                return;
            }

            List<Map<String, Object>> list = new ArrayList<>();
            String sql = "SELECT t.trip_id, t.title, t.destination, t.start_date, t.end_date, t.status, "
                    + "g.group_id, g.name AS group_name, gm.role AS user_role "
                    + "FROM trips t "
                    + "JOIN travel_groups g ON t.group_id = g.group_id "
                    + "JOIN group_members gm ON g.group_id = gm.group_id "
                    + "WHERE gm.user_id = ? "
                    + "ORDER BY t.start_date ASC";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("tripId", rs.getInt("trip_id"));
                        map.put("title", rs.getString("title"));
                        map.put("destination", rs.getString("destination"));
                        map.put("startDate", rs.getString("start_date"));
                        map.put("endDate", rs.getString("end_date"));
                        map.put("status", rs.getString("status"));
                        map.put("groupId", rs.getInt("group_id"));
                        map.put("groupName", rs.getString("group_name"));
                        map.put("userRole", rs.getString("user_role"));
                        list.add(map);
                    }
                }
            } catch (SQLException e) {
                ctx.status(500).result("Database error: " + e.getMessage());
                return;
            }
            ctx.json(list);
        });

        app.post("/api/users", ctx -> {
            String sql = "INSERT INTO users (user_id, username, email, password_hash) VALUES (?, ?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, Integer.parseInt(ctx.formParam("user_id")));
                pstmt.setString(2, ctx.formParam("username"));
                pstmt.setString(3, ctx.formParam("email"));
                pstmt.setString(4, ctx.formParam("password"));
                pstmt.executeUpdate();
                ctx.result("User added");
            } catch (Exception e) {
                ctx.status(500).result(e.getMessage());
            }
        });

        // --- GROUPS ---
        app.get("/api/groups", ctx -> {
            List<Map<String, Object>> list = new ArrayList<>();
            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT * FROM travel_groups")) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", rs.getInt("group_id"));
                    map.put("name", rs.getString("name"));
                    list.add(map);
                }
            }
            ctx.json(list);
        });

        app.post("/api/groups", ctx -> {
            String groupSql = "INSERT INTO travel_groups (group_id, name, description, created_by) VALUES (?, ?, ?, ?)";
            String memberSql = "INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'CREATOR')";
            try {
                int groupId = Integer.parseInt(ctx.formParam("group_id"));
                int creatorId = Integer.parseInt(ctx.formParam("creatorId"));
                boolean originalAutoCommit = connection.getAutoCommit();
                try {
                    connection.setAutoCommit(false);
                    try (PreparedStatement groupStmt = connection.prepareStatement(groupSql);
                            PreparedStatement memberStmt = connection.prepareStatement(memberSql)) {
                        groupStmt.setInt(1, groupId);
                        groupStmt.setString(2, ctx.formParam("groupName"));
                        groupStmt.setString(3, ctx.formParam("groupDesc"));
                        groupStmt.setInt(4, creatorId);
                        groupStmt.executeUpdate();

                        memberStmt.setInt(1, groupId);
                        memberStmt.setInt(2, creatorId);
                        memberStmt.executeUpdate();

                        connection.commit();
                    }
                } catch (Exception e) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackError) {
                        e.addSuppressed(rollbackError);
                    }
                    throw e;
                } finally {
                    connection.setAutoCommit(originalAutoCommit);
                }
                ctx.result("Group created");
            } catch (Exception e) {
                ctx.status(500).result("Error: " + e.getMessage());
            }
        });

        app.get("/api/groups/{groupId}/members", ctx -> {
            int groupId;
            try {
                groupId = Integer.parseInt(ctx.pathParam("groupId"));
            } catch (NumberFormatException e) {
                ctx.status(400).result("Invalid group ID");
                return;
            }

            List<Map<String, Object>> list = new ArrayList<>();
            String sql = "SELECT gm.user_id, u.username, gm.role "
                    + "FROM group_members gm JOIN users u ON gm.user_id = u.user_id "
                    + "WHERE gm.group_id = ? ORDER BY gm.joined_at";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, groupId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("userId", rs.getInt("user_id"));
                        map.put("username", rs.getString("username"));
                        map.put("role", rs.getString("role"));
                        list.add(map);
                    }
                }
            }
            ctx.json(list);
        });

        app.post("/api/groups/{groupId}/members", ctx -> {
            try {
                int groupId = Integer.parseInt(ctx.pathParam("groupId"));
                int userId = Integer.parseInt(ctx.formParam("user_id"));
                String sql = "INSERT INTO group_members (group_id, user_id) VALUES (?, ?)";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setInt(1, groupId);
                    stmt.setInt(2, userId);
                    stmt.executeUpdate();
                }
                ctx.status(201).result("Member added");
            } catch (NumberFormatException e) {
                ctx.status(400).result("Invalid group or user ID");
            } catch (SQLException e) {
                ctx.status(400).result("Could not add member: " + e.getMessage());
            }
        });

        // --- TRIPS ---
        app.get("/api/trips", ctx -> {
            List<Map<String, Object>> list = new ArrayList<>();
            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT * FROM trips")) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", rs.getInt("trip_id"));
                    map.put("title", rs.getString("title"));
                    map.put("destination", rs.getString("destination"));
                    list.add(map);
                }
            }
            ctx.json(list);
        });

        app.post("/api/trips", ctx -> {
            String sql = "INSERT INTO trips (trip_id, title, destination, start_date, end_date, group_id) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, Integer.parseInt(ctx.formParam("trip_id")));
                pstmt.setString(2, ctx.formParam("title"));
                pstmt.setString(3, ctx.formParam("destination"));
                pstmt.setString(4, ctx.formParam("start_date"));
                pstmt.setString(5, ctx.formParam("end_date"));
                pstmt.setInt(6, Integer.parseInt(ctx.formParam("group_id")));
                pstmt.executeUpdate();
                ctx.result("Trip added");
            } catch (Exception e) {
                ctx.status(500).result("Error: " + e.getMessage());
            }
        });

        // --- ITINERARIES ---
        app.get("/api/itineraries", ctx -> {
            List<Map<String, Object>> list = new ArrayList<>();
            String tripIdParam = ctx.queryParam("trip_id");
            String sql = "SELECT * FROM itineraries";
            if (tripIdParam != null) {
                sql += " WHERE trip_id = " + tripIdParam;
            }
            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", rs.getInt("itinerary_id"));
                    map.put("title", rs.getString("title"));
                    map.put("location", rs.getString("location"));
                    map.put("trip_id", rs.getInt("trip_id"));
                    map.put("cost", rs.getDouble("cost"));
                    list.add(map);
                }
            }
            ctx.json(list);
        });

        app.post("/api/itineraries", ctx -> {
            String sql = "INSERT INTO itineraries (itinerary_id, title, start_time, location, cost, trip_id) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, Integer.parseInt(ctx.formParam("itinerary_id")));
                pstmt.setString(2, ctx.formParam("title"));
                pstmt.setString(3, ctx.formParam("start_time"));
                pstmt.setString(4, ctx.formParam("location"));
                pstmt.setDouble(5, Double.parseDouble(ctx.formParam("cost")));
                pstmt.setInt(6, Integer.parseInt(ctx.formParam("trip_id")));
                pstmt.executeUpdate();
                ctx.result("Itinerary added");
            } catch (Exception e) {
                ctx.status(500).result("Error: " + e.getMessage());
            }
        });
    }
}