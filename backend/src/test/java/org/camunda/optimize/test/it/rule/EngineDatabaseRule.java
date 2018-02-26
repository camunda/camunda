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

  private static final String JDBC_DRIVER = "org.h2.Driver";
  private static final String DB_URL = "jdbc:h2:tcp://localhost:9092/mem:camunda1";

  private static final String USER = "sa";
  private static final String PASS = "";

  private static Connection connection = null;

  public EngineDatabaseRule() {
    try {
      if (connection == null || connection.isClosed()) {
        // Register JDBC driver
        Class.forName(JDBC_DRIVER);

        logger.debug("Connecting to a selected database...");
        connection = DriverManager.getConnection(DB_URL, USER, PASS);
        logger.debug("Connected database successfully...");

        // to be able to batch sql statements
        connection.setAutoCommit(false);
      }
    } catch (SQLException e) {
      logger.error("Error while trying to connect to database!", e);
    } catch (ClassNotFoundException e) {
      logger.error("Could not find h2 jdbc driver class!", e);
    }
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
    PreparedStatement statement = connection.prepareStatement(sql);
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
    PreparedStatement statement = connection.prepareStatement(sql);
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
    PreparedStatement statement = connection.prepareStatement(sql);
    statement.setLong(1, duration);
    statement.setString(2, processDefinitionId);
    statement.executeUpdate();
    connection.commit();
  }

  public void changeProcessInstanceStartDate(String processInstanceId, OffsetDateTime startDate) throws SQLException {
    String sql = "UPDATE ACT_HI_PROCINST " +
      "SET START_TIME_ = ? WHERE PROC_INST_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(sql);
    statement.setTimestamp(1, java.sql.Timestamp.valueOf(startDate.toLocalDateTime()));
    statement.setString(2, processInstanceId);
    statement.executeUpdate();
    connection.commit();
  }

  public void updateProcessInstanceStartDates(Map<String, OffsetDateTime> processInstanceIdToStartDate) throws SQLException {
    String sql = "UPDATE ACT_HI_PROCINST " +
      "SET START_TIME_ = ? WHERE PROC_INST_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(sql);
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
    PreparedStatement statement = connection.prepareStatement(sql);
    statement.setTimestamp(1, java.sql.Timestamp.valueOf(endDate.toLocalDateTime()));
    statement.setString(2, processInstanceId);
    statement.executeUpdate();
    connection.commit();
  }

  public void updateProcessInstanceEndDates(Map<String, OffsetDateTime> processInstanceIdToEndDate) throws SQLException {
    String sql = "UPDATE ACT_HI_PROCINST " +
      "SET END_TIME_ = ? WHERE PROC_INST_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(sql);
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
