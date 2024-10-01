package org.example.database;

import org.example.exceptions.*;
import org.example.model.*;
import org.example.util.Properties;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;


public class MySQL implements Database {

    private static Connection connection;

    /*
    public static void loadDriver(){
        try {
            Class.forName("com.mysql.cj.jdbc.Driver").getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
     */

    public static Connection getConnection(String dbName) throws SQLException, MySqlCredentialsException {

        if (MySQL.connection != null && MySQL.connection.isValid(0)) {
            return MySQL.connection;
        } else {
            String url = Properties.getProperty("db.url") + dbName;
            String user = Properties.getProperty("db.user");
            String password = Properties.getProperty("db.password");

            try {
                Connection connection = DriverManager.getConnection(url, user, password);
                System.out.printf("Connected to %s.%n", dbName.isEmpty() ? "DB" : dbName);
                return connection;

            } catch (SQLException e) {
                System.out.printf("Error connecting to '%s' as user '%s' and password '%s'%n", url, user, password);
                if (e.getErrorCode() == 1045) {
                    throw new MySqlCredentialsException(String.format(
                            "Error connecting to MySQL server. Modify the credentials at '%s'%n",
                            Path.of(Properties.PROPERTIES_FILE_PATH.getValue()).toAbsolutePath()),
                            e);
                }
                throw e;
            }
        }


    }

    public static Connection getConnection() throws SQLException, MySqlCredentialsException {
        return getConnection("");
    }

    public void createIfMissing() throws MySqlCredentialsException {

        try {
            Connection connection = getConnection(Properties.DB_NAME.getValue());
        } catch (SQLException e) {
            if (e.getErrorCode() == 1049) {
                System.out.println("Unknown database -> Creating Database...");
                executeSqlFile(Properties.SQL_SCHEMA_CREATION_FILE.getValue());
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    private static void executeSqlFile(String file) throws MySqlCredentialsException {
        Path path = Path.of(file);
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             BufferedReader input = Files.newBufferedReader(path.toRealPath())
        ) {

            StringBuilder sql = new StringBuilder();
            input.lines().filter(s -> !s.isEmpty() && !s.matches("^--.*")).forEach(sql::append);

            Arrays.stream(sql.toString().split(";")).forEach(s -> {
                try {
                    System.out.println(s);
                    statement.execute(s);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            System.out.println("DB created successfully.");

        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute the .sql script: MySQL error.", e);
        } catch (IOException e) {
            throw new RunSqlFileException("Unable to execute the .sql script: error reading the '.sql' file.", e);
        }
    }


    public boolean createUser(User user) throws ExistingEmailException {

        try (Connection connection = getConnection(Properties.DB_NAME.getValue());
             Statement statement = connection.createStatement()) {
            String str = String.format("INSERT INTO user (name, email, password, role) VALUES('%s', '%s', '%s', '%s');",
                    user.getName(), user.getEmail(), user.getPassword(), user instanceof Player ? "player" : "admin");
            statement.execute(str);
            if (user instanceof Player) {
                Statement statement2 = connection.createStatement();
                str = "SELECT LAST_INSERT_ID();";
                ResultSet lastId = statement2.executeQuery(str);
                lastId.next();
                subscribePlayer(lastId.getInt(1));
                statement2.close();
            }
            return statement.getUpdateCount() == 1;
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new ExistingEmailException(e);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (MySqlCredentialsException e) {
            throw new RuntimeException(e);
        }
    }

    public static User getUser(String email, String password) {
        User user;
        try (Connection connection = getConnection(Properties.DB_NAME.getValue());
             Statement statement = connection.createStatement()) {
            String str = String.format("SELECT * FROM user WHERE email = '%s' AND password = '%s';", email, password);
            ResultSet result = statement.executeQuery(str);
            if (!result.next()){
                user = null;
            } else if (result.getString("role").equalsIgnoreCase("player")){
                user = new Player(result.getString("name"), result.getString("email"), result.getInt("user_id"));
            }else {
                user = new Admin(result.getString("name"), result.getString("email"), result.getInt("user_id"));
            }
            return user;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (MySqlCredentialsException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean createTicket(User user, BigDecimal price){
        try (Connection connection = getConnection(Properties.DB_NAME.getValue());
             Statement statement = connection.createStatement()) {
            String str = String.format("INSERT INTO ticket (user_id, price, cashed) VALUES('%s', '%s', '%s');",
                    user.getId(), price, 0);
            statement.execute(str);
            return statement.getUpdateCount() == 1;
        } catch (SQLException | MySqlCredentialsException e) {
            throw new RuntimeException(e);
        }
    }

    public static ArrayList<Ticket> getTickets(User user, boolean onlyNotCashed){
        ArrayList<Ticket> tickets = new ArrayList<>();
        try (Connection connection = getConnection(Properties.DB_NAME.getValue());
             Statement statement = connection.createStatement()) {
            String str = String.format("SELECT ticket_id, cashed FROM ticket WHERE user_id = '%d';", user.getId());
            ResultSet result = statement.executeQuery(str);
            while (result.next()){
                if (onlyNotCashed){
                    if(!result.getBoolean("cashed")){
                        tickets.add(new Ticket(result.getInt("ticket_id")));
                    }
                }else {
                    tickets.add(new Ticket(result.getInt("ticket_id")));
                }
            }
            return tickets;
        } catch (SQLException | MySqlCredentialsException e) {
            throw new RuntimeException(e);
        }
    }

    public static void cashTicket(int ticketId) {
        executeQuery(String.format("UPDATE ticket SET cashed = 1 WHERE ticket_id = %d;", ticketId));
    }

    @Deprecated
    public static BigDecimal getTotalIncomeOldVersion(){
        try (Connection connection = getConnection(Properties.DB_NAME.getValue());
             Statement statement = connection.createStatement()) {
            String str = "SELECT SUM(price) FROM ticket;";
            ResultSet result = statement.executeQuery(str);
            if (result.next()){
                return result.getBigDecimal(1);
            }else {
                return null;
            }
        } catch (SQLException | MySqlCredentialsException e) {
            throw new RuntimeException(e);
        }
    }

    public static BigDecimal getTotalIncome() {
        String query = "SELECT SUM(price) FROM ticket;";
        try {
            return retrieveSingleValueFromDatabase(query, BigDecimal.class);
        } catch (MySqlEmptyResultSetException e) {
            throw new RuntimeException(String.format("The query '%s' didn't yield a result.%n", query));
        }
    }

    public static void subscribePlayer(int playerId){
        executeQuery(String.format("INSERT INTO subscription (user_id) VALUES (%d);", playerId));
    }

    public static void unsubscribePlayer(int playerId){
        executeQuery(String.format("DELETE FROM subscription WHERE user_id = %d;", playerId));
    }

    @Deprecated
    public static boolean isSubscribedOldVersion(int playerId){
        try (Connection connection = getConnection(Properties.DB_NAME.getValue());
             Statement statement = connection.createStatement()) {
            String str = String.format("SELECT * FROM subscription WHERE user_id = %d;", playerId);
            ResultSet result = statement.executeQuery(str);
            return result.next();
        } catch (SQLException | MySqlCredentialsException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isSubscribed(int playerId){
        String query = String.format("SELECT user_id FROM subscription WHERE user_id = %d;", playerId);
        try{
            retrieveSingleValueFromDatabase(query, Integer.class);
            return true;
        }catch (MySqlEmptyResultSetException e){
            return false;
        }
    }

    public static <T> T retrieveSingleValueFromDatabase(String query, Class<T> type) throws MySqlEmptyResultSetException {
        T response;
        try (Connection connection = getConnection(Properties.DB_NAME.getValue());
             Statement statement = connection.createStatement()) {
            ResultSet result = statement.executeQuery(query);
            if (result.next()){
                if (type == Integer.class){
                    response = (T) Integer.valueOf(result.getInt(1));
                } else if (type == String.class) {
                    response = (T) result.getString(1);
                } else if (type == BigDecimal.class){
                    response = (T) result.getBigDecimal(1);
                }else {
                    throw new UnsupportedTypeException(
                            String.format("RetrieveColumnFromDatabase() doesn't accept %s as argument.", type));
                }
            }else {
                throw new MySqlEmptyResultSetException("Query results in an empty ResultSet.");
            }
        } catch (SQLException | MySqlCredentialsException e) {
            throw new RuntimeException(e);
        }
        return response;
    }

    public static void insertIntoDatabase(Storable storable){
        executeQuery(storable.insertQuery());
    }

    public static void deleteFromDatabase(Deletable deletable){
        executeQuery(deletable.deleteQuery());
    }

    public static void executeQuery(String query){
        try (Connection connection = getConnection(Properties.DB_NAME.getValue());
             Statement statement = connection.createStatement()) {
            statement.execute(query);
        } catch (SQLException | MySqlCredentialsException e) {
            throw new RuntimeException(e);
        }
    }

    public static ArrayList<Integer> getSubscribers() {
        return MySQL.retrieveSingleColumnFromDatabase("SELECT user_id FROM subscription;", Integer.class);
    }

    public static <T> ArrayList<T> retrieveSingleColumnFromDatabase(String query, Class<T> type){
        ArrayList<T> response = new ArrayList<>();
        try (Connection connection = getConnection(Properties.DB_NAME.getValue());
             Statement statement = connection.createStatement()) {
            ResultSet result = statement.executeQuery(query);
            while (result.next()){
                if (type == Integer.class){
                    response.add(type.cast(result.getInt(1)));
                } else if (type == String.class){
                    response.add(type.cast(result.getString(1)));
                }else {
                    throw new UnsupportedTypeException(
                            String.format("RetrieveColumnFromDatabase() doesn't accept %s as argument.", type));
                }
            }
        } catch (SQLException | MySqlCredentialsException e) {
            throw new RuntimeException(e);
        }
        return response;
    }

    public static Collection<? extends Notification> retrieveNotifications(int playerId) {
        ArrayList<Notification> response = new ArrayList<>();
        ArrayList<ArrayList<Object>> items = retrieveMultipleColumnsFromDatabase(
                String.format("SELECT notification_id, message FROM notification WHERE user_id = %d;", playerId),
                new String[] {"Integer", "String"});
        items.forEach(e -> response.add(new Notification((int) e.getFirst(), playerId, (String) e.get(1))));
        return response;
    }


    public static <T> ArrayList<ArrayList<Object>> retrieveMultipleColumnsFromDatabase(String query, String[] types) {
        ArrayList<ArrayList<Object>> response = new ArrayList<>();
        try (Connection connection = getConnection(Properties.DB_NAME.getValue());
             Statement statement = connection.createStatement()) {
            ResultSet result = statement.executeQuery(query);
            while (result.next()){
                response.add(new ArrayList<>());
                for (int i = 0; i < types.length; i++){
                    if (types[i].equalsIgnoreCase("Integer")){
                        response.getLast().add((result.getInt(i + 1)));
                    } else if (types[i].equalsIgnoreCase("String")){
                        response.getLast().add(result.getString(i + 1));
                    }else {
                        throw new UnsupportedTypeException(
                                String.format("retrieveMultipleColumnsFromDatabase() can't process %s's.",
                                        types[i]));
                    }

                }
            }
        } catch (SQLException | MySqlCredentialsException e) {
            throw new RuntimeException(e);
        }
        return response;
    }

    @Override
    public void execute(Element e) {

    }
}
