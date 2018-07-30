package org.camunda.optimize.test.it.rule;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Map;

public class EngineDatabaseRule extends TestWatcher {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private static final String DATABASE_H2 = "h2";
  private static final String DATABASE_POSTGRESQL = "postgresql";

  private static final String JDBC_DRIVER_H2 = "org.h2.Driver";
  private static final String DB_URL_H2 = "jdbc:h2:tcp://localhost:9092/mem:camunda1";
  private static final String USER_H2 = "sa";
  private static final String PASS_H2 = "";

  private static final String JDBC_DRIVER_POSTGRESQL = "org.postgresql.Driver";
  private static final String DB_URL_POSTGRESQL = "jdbc:postgresql://localhost:5432/engine";
  private static final String USER_POSTGRESQL = "camunda";
  private static final String PASS_POSTGRESQL = "camunda";

  private static Connection connection = null;

  private String database = System.getProperty("database", "h2");

  public EngineDatabaseRule() {
    try {
      if (connection == null || connection.isClosed()) {
        String jdbcDriver;
        String dbUrl;
        String user;
        String pass;

        switch (database) {
          case DATABASE_H2:
            jdbcDriver = JDBC_DRIVER_H2;
            dbUrl = DB_URL_H2;
            user = USER_H2;
            pass = PASS_H2;
            break;
          case DATABASE_POSTGRESQL:
            jdbcDriver = JDBC_DRIVER_POSTGRESQL;
            dbUrl = DB_URL_POSTGRESQL;
            user = USER_POSTGRESQL;
            pass = PASS_POSTGRESQL;
            break;
          default:
            throw new IllegalArgumentException("Unable to discover database " + database);
        }
        // Register JDBC driver
        Class.forName(jdbcDriver);

        logger.info("Connecting to a selected " + database + " database...");
        connection = DriverManager.getConnection(dbUrl, user, pass);
        logger.info("Connected to " + database + " database successfully...");

        // to be able to batch sql statements
        connection.setAutoCommit(false);
      }
    } catch (SQLException e) {
      logger.error("Error while trying to connect to database " + database + "!", e);
    } catch (ClassNotFoundException e) {
      logger.error("Could not find " + database + " jdbc driver class!", e);
    }
  }

  protected String handleDatabaseSyntax(String statement) {
    return (database == DATABASE_POSTGRESQL) ? statement.toLowerCase() : statement;
  }

  @Override
  protected void starting(Description description) {
    super.starting(description);
  }

  public void changeActivityDuration(String processInstanceId,
                                     String activityId,
                                     long duration) throws SQLException {
    String sql = "UPDATE ACT_HI_ACTINST " +
      "SET DURATION_ = ? WHERE " +
      "PROC_INST_ID_ = ? AND " +
      "ACT_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setLong(1, duration);
    statement.setString(2, processInstanceId);
    statement.setString(3, activityId);
    statement.executeUpdate();
    connection.commit();
  }

  public void changeActivityDuration(String processInstanceId,
                                     long duration) throws SQLException {
    String sql = "UPDATE ACT_HI_ACTINST " +
      "SET DURATION_ = ? WHERE " +
      "PROC_INST_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setLong(1, duration);
    statement.setString(2, processInstanceId);
    statement.executeUpdate();
    connection.commit();
  }

  public void changeActivityDurationForProcessDefinition(String processDefinitionId,
                                     long duration) throws SQLException {
    String sql = "UPDATE ACT_HI_ACTINST " +
      "SET DURATION_ = ? WHERE " +
      "PROC_DEF_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setLong(1, duration);
    statement.setString(2, processDefinitionId);
    statement.executeUpdate();
    connection.commit();
  }

  public void changeProcessInstanceStartDate(String processInstanceId, OffsetDateTime startDate) throws SQLException {
    String sql = "UPDATE ACT_HI_PROCINST " +
      "SET START_TIME_ = ? WHERE PROC_INST_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setTimestamp(1, java.sql.Timestamp.valueOf(startDate.toLocalDateTime()));
    statement.setString(2, processInstanceId);
    statement.executeUpdate();
    connection.commit();
  }

  public void changeProcessInstanceState(String processInstanceId, String newState) throws SQLException {
    String sql = "UPDATE ACT_HI_PROCINST " +
            "SET STATE_ = ? WHERE PROC_INST_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setString(1, newState);
    statement.setString(2, processInstanceId);
    statement.executeUpdate();
    connection.commit();
  }

  public void updateProcessInstanceStartDates(Map<String, OffsetDateTime> processInstanceIdToStartDate) throws SQLException {
    String sql = "UPDATE ACT_HI_PROCINST " +
      "SET START_TIME_ = ? WHERE PROC_INST_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    for (Map.Entry<String, OffsetDateTime> idToStartDate : processInstanceIdToStartDate.entrySet()) {
      statement.setTimestamp(1, java.sql.Timestamp.valueOf(idToStartDate.getValue().toLocalDateTime()));
      statement.setString(2, idToStartDate.getKey());
      statement.executeUpdate();
    }
    connection.commit();
  }

   public void changeProcessInstanceEndDate(String processInstanceId, OffsetDateTime endDate) throws SQLException {
    String sql = "UPDATE ACT_HI_PROCINST " +
      "SET END_TIME_ = ? WHERE PROC_INST_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setTimestamp(1, java.sql.Timestamp.valueOf(endDate.toLocalDateTime()));
    statement.setString(2, processInstanceId);
    statement.executeUpdate();
    connection.commit();
  }

  public void updateProcessInstanceEndDates(Map<String, OffsetDateTime> processInstanceIdToEndDate) throws SQLException {
    String sql = "UPDATE ACT_HI_PROCINST " +
      "SET END_TIME_ = ? WHERE PROC_INST_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    for (Map.Entry<String, OffsetDateTime> idToStartDate : processInstanceIdToEndDate.entrySet()) {
      statement.setTimestamp(1, java.sql.Timestamp.valueOf(idToStartDate.getValue().toLocalDateTime()));
      statement.setString(2, idToStartDate.getKey());
      statement.executeUpdate();
    }
    connection.commit();
  }

  @Override
  protected void finished(Description description) {
    super.finished(description);
  }
}
