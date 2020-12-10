/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.it.extension;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.IdentityLinkLogType;
import org.junit.jupiter.api.extension.Extension;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Engine Database Extension
 */
@Slf4j
public class EngineDatabaseExtension implements Extension {
  private static final String DATABASE_H2 = "h2";
  private static final String DATABASE_POSTGRESQL = "postgresql";

  private static final String JDBC_DRIVER_H2 = "org.h2.Driver";
  private static final String DB_URL_H2_TEMPLATE = "jdbc:h2:tcp://localhost:9092/mem:%s";
  private static final String USER_H2 = "sa";
  private static final String PASS_H2 = "";

  private static final String UPDATE_INSTANCE_END_TIME_SQL =
    "UPDATE ACT_HI_PROCINST SET END_TIME_ = ? WHERE PROC_INST_ID_ = ?";
  private static final String UPDATE_INSTANCE_START_TIME_SQL =
    "UPDATE ACT_HI_PROCINST SET START_TIME_ = ? WHERE PROC_INST_ID_ = ?";

  private static final Map<String, Connection> CONNECTION_CACHE = new HashMap<>();

  private static String database = DATABASE_H2;

  private Connection connection = null;
  private Boolean usePostgresOptimizations = true;

  public EngineDatabaseExtension(final Properties properties) {
    database = properties.getProperty("db.name");
    usePostgresOptimizations = Optional.ofNullable(properties.getProperty("db.usePostgresOptimizations"))
      .map(Boolean::valueOf)
      .orElse(true);
    String jdbcDriver = properties.getProperty("db.jdbc.driver");
    String dbUrl = properties.getProperty("db.url");
    if (dbUrl == null || dbUrl.isEmpty() || dbUrl.startsWith("${")) {
      dbUrl = properties.getProperty("db.url.default");
    }
    String dbUser = properties.getProperty("db.username");
    String dbPassword = properties.getProperty("db.password");
    initDatabaseConnection(jdbcDriver, dbUrl, dbUser, dbPassword);
  }

  public EngineDatabaseExtension(@NonNull final String dataBaseName) {
    final String dbUrl = String.format(DB_URL_H2_TEMPLATE, dataBaseName);
    initDatabaseConnection(JDBC_DRIVER_H2, dbUrl, USER_H2, PASS_H2);
  }

  private void initDatabaseConnection(String jdbcDriver, String dbUrl, String dbUser, String dbPassword) {
    try {
      if (CONNECTION_CACHE.containsKey(dbUrl)) {
        connection = CONNECTION_CACHE.get(dbUrl);
      } else {
        Class.forName(jdbcDriver);

        log.debug("Connecting to a selected " + database + " database...");
        connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        log.debug("Connected to " + database + " database successfully...");

        // to be able to batch sql statements
        connection.setAutoCommit(false);

        CONNECTION_CACHE.put(dbUrl, connection);
      }
    } catch (SQLException e) {
      log.error("Error while trying to connect to database " + database + "!", e);
    } catch (ClassNotFoundException e) {
      log.error("Could not find " + database + " jdbc driver class!", e);
    }
  }

  private String handleDatabaseSyntax(String statement) {
    return (database.equals(DATABASE_POSTGRESQL)) ? statement.toLowerCase() : statement;
  }

  @SneakyThrows
  public void changeActivityDuration(String processInstanceId,
                                     String activityId,
                                     Number durationInMs) {
    String sql = "UPDATE ACT_HI_ACTINST " +
      "SET DURATION_ = ? WHERE " +
      "PROC_INST_ID_ = ? AND " +
      "ACT_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setLong(1, durationInMs.longValue());
    statement.setString(2, processInstanceId);
    statement.setString(3, activityId);
    statement.executeUpdate();
    connection.commit();
  }

  @SneakyThrows
  public void changeAllActivityDurations(String processInstanceId,
                                         Number durationInMs) {
    String sql = "UPDATE ACT_HI_ACTINST " +
      "SET DURATION_ = ? WHERE " +
      "PROC_INST_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setLong(1, durationInMs.longValue());
    statement.setString(2, processInstanceId);
    statement.executeUpdate();
    connection.commit();
  }

  public void changeActivityDurationForProcessDefinition(String processDefinitionId,
                                                         Number durationInMs) throws SQLException {
    String sql = "UPDATE ACT_HI_ACTINST " +
      "SET DURATION_ = ? WHERE " +
      "PROC_DEF_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setLong(1, durationInMs.longValue());
    statement.setString(2, processDefinitionId);
    statement.executeUpdate();
    connection.commit();
  }

  @SneakyThrows
  public void changeActivityInstanceStartDate(String processInstanceId,
                                              OffsetDateTime startDate) {
    String sql = "UPDATE ACT_HI_ACTINST " +
      "SET START_TIME_ = ? WHERE " +
      "PROC_INST_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setTimestamp(1, toLocalTimestampWithoutNanos(startDate));
    statement.setString(2, processInstanceId);
    statement.executeUpdate();
    connection.commit();
  }

  @SneakyThrows
  public void changeActivityInstanceStartDate(String processInstanceId,
                                              String activityId,
                                              OffsetDateTime startDate) {
    String sql = "UPDATE ACT_HI_ACTINST " +
      "SET START_TIME_ = ? WHERE " +
      "PROC_INST_ID_ = ?" +
      "AND ACT_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setTimestamp(1, toLocalTimestampWithoutNanos(startDate));
    statement.setString(2, processInstanceId);
    statement.setString(3, activityId);
    statement.executeUpdate();
    connection.commit();
  }

  @SneakyThrows
  public void changeActivityInstanceStartDates(Map<String, OffsetDateTime> processInstanceIdToEndDate) {
    String sql = "UPDATE ACT_HI_ACTINST " +
      "SET START_TIME_ = ? WHERE PROC_INST_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    for (Map.Entry<String, OffsetDateTime> idToStartDate : processInstanceIdToEndDate.entrySet()) {
      statement.setTimestamp(1, toLocalTimestampWithoutNanos(idToStartDate.getValue()));
      statement.setString(2, idToStartDate.getKey());
      statement.executeUpdate();
    }
    connection.commit();
  }

  @SneakyThrows
  public void changeFirstActivityInstanceStartDate(String activityInstanceId,
                                                   OffsetDateTime startDate) {
    String sql = "UPDATE ACT_HI_ACTINST " +
      "SET START_TIME_ = ? WHERE " +
      "ID_ = (SELECT ID_ FROM ACT_HI_ACTINST WHERE ACT_ID_ = ? ORDER BY START_TIME_ LIMIT 1) ";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setTimestamp(1, toLocalTimestampWithoutNanos(startDate));
    statement.setString(2, activityInstanceId);
    statement.executeUpdate();
    connection.commit();
  }

  @SneakyThrows
  public void changeActivityInstanceStartDateForProcessDefinition(final String processDefinitionId,
                                                                  final OffsetDateTime startDate) {
    String sql = "UPDATE ACT_HI_ACTINST " +
      "SET START_TIME_ = ? WHERE " +
      "PROC_DEF_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setTimestamp(1, toLocalTimestampWithoutNanos(startDate));
    statement.setString(2, processDefinitionId);
    statement.executeUpdate();
    connection.commit();
  }

  @SneakyThrows
  public void updateActivityInstanceStartDates(Map<String, OffsetDateTime> processInstanceToDates) {
    String sql = "UPDATE ACT_HI_ACTINST " +
      "SET START_TIME_ = ? WHERE PROC_INST_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    for (Map.Entry<String, OffsetDateTime> idToStartDate : processInstanceToDates.entrySet()) {
      statement.setTimestamp(1, toLocalTimestampWithoutNanos(idToStartDate.getValue()));
      statement.setString(2, idToStartDate.getKey());
      statement.executeUpdate();
    }
    connection.commit();
  }

  @SneakyThrows
  public void changeActivityInstanceEndDate(String processInstanceId,
                                            OffsetDateTime endDate) {
    String sql = "UPDATE ACT_HI_ACTINST " +
      "SET END_TIME_ = ? WHERE " +
      "PROC_INST_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setTimestamp(1, toLocalTimestampWithoutNanos(endDate));
    statement.setString(2, processInstanceId);
    statement.executeUpdate();
    connection.commit();
  }

  @SneakyThrows
  public void changeActivityInstanceEndDate(String processInstanceId,
                                            String activityId,
                                            OffsetDateTime endDate) {
    String sql = "UPDATE ACT_HI_ACTINST " +
      "SET END_TIME_ = ? WHERE " +
      "PROC_INST_ID_ = ?" +
      "AND ACT_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setTimestamp(1, toLocalTimestampWithoutNanos(endDate));
    statement.setString(2, processInstanceId);
    statement.setString(3, activityId);
    statement.executeUpdate();
    connection.commit();
  }

  @SneakyThrows
  public void changeActivityInstanceEndDates(Map<String, OffsetDateTime> processInstanceIdToEndDate) {
    String sql = "UPDATE ACT_HI_ACTINST " +
      "SET END_TIME_ = ? WHERE PROC_INST_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    for (Map.Entry<String, OffsetDateTime> idToStartDate : processInstanceIdToEndDate.entrySet()) {
      statement.setTimestamp(1, toLocalTimestampWithoutNanos(idToStartDate.getValue()));
      statement.setString(2, idToStartDate.getKey());
      statement.executeUpdate();
    }
    connection.commit();
  }

  @SneakyThrows
  public void changeFirstActivityInstanceEndDate(String activityId,
                                                 OffsetDateTime endDate) {
    String sql = "UPDATE ACT_HI_ACTINST " +
      "SET END_TIME_ = ? WHERE " +
      "ID_ = (SELECT ID_ FROM ACT_HI_ACTINST WHERE ACT_ID_ = ? ORDER BY END_TIME_ LIMIT 1) ";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setTimestamp(1, toLocalTimestampWithoutNanos(endDate));
    statement.setString(2, activityId);
    statement.executeUpdate();
    connection.commit();
  }

  @SneakyThrows
  public void changeActivityInstanceEndDateForProcessDefinition(final String processDefinitionId,
                                                                final OffsetDateTime endDate) {
    String sql = "UPDATE ACT_HI_ACTINST " +
      "SET END_TIME_ = ? WHERE " +
      "PROC_DEF_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setTimestamp(1, toLocalTimestampWithoutNanos(endDate));
    statement.setString(2, processDefinitionId);
    statement.executeUpdate();
    connection.commit();
  }

  @SneakyThrows
  public void updateActivityInstanceEndDates(Map<String, OffsetDateTime> processInstanceToDates) {
    String sql = "UPDATE ACT_HI_ACTINST " +
      "SET END_TIME_ = ? WHERE PROC_INST_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    for (Map.Entry<String, OffsetDateTime> idToActivityEndDate : processInstanceToDates.entrySet()) {
      statement.setTimestamp(1, toLocalTimestampWithoutNanos(idToActivityEndDate.getValue()));
      statement.setString(2, idToActivityEndDate.getKey());
      statement.executeUpdate();
    }
    connection.commit();
  }

  @SneakyThrows
  public void changeUserTaskDuration(final String processInstanceId,
                                     final Number durationInMs) {
    String sql = "UPDATE ACT_HI_TASKINST " +
      "SET DURATION_ = ? WHERE " +
      "PROC_INST_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setLong(1, durationInMs.longValue());
    statement.setString(2, processInstanceId);
    statement.executeUpdate();
    connection.commit();
  }

  @SneakyThrows
  public void changeUserTaskDuration(final String processInstanceId,
                                     final String userTaskId,
                                     final Number durationInMs) {
    String sql = "UPDATE ACT_HI_TASKINST " +
      "SET DURATION_ = ? WHERE " +
      "PROC_INST_ID_ = ?" +
      "AND TASK_DEF_KEY_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setLong(1, durationInMs.longValue());
    statement.setString(2, processInstanceId);
    statement.setString(3, userTaskId);
    statement.executeUpdate();
    connection.commit();
  }

  @SneakyThrows
  public void changeUserTaskStartDates(Map<String, OffsetDateTime> processInstanceIdToStartDate) {
    String sql = "UPDATE ACT_HI_TASKINST " +
      "SET START_TIME_ = ? WHERE PROC_INST_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    for (Map.Entry<String, OffsetDateTime> idToStartDate : processInstanceIdToStartDate.entrySet()) {
      statement.setTimestamp(1, toLocalTimestampWithoutNanos(idToStartDate.getValue()));
      statement.setString(2, idToStartDate.getKey());
      statement.executeUpdate();
    }
    connection.commit();
  }

  @SneakyThrows
  public void changeUserTaskEndDates(Map<String, OffsetDateTime> processInstanceIdToEndDate) {
    String sql = "UPDATE ACT_HI_TASKINST " +
      "SET END_TIME_ = ? WHERE PROC_INST_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    for (Map.Entry<String, OffsetDateTime> idToStartDate : processInstanceIdToEndDate.entrySet()) {
      statement.setTimestamp(1, toLocalTimestampWithoutNanos(idToStartDate.getValue()));
      statement.setString(2, idToStartDate.getKey());
      statement.executeUpdate();
    }
    connection.commit();
  }

  @SneakyThrows
  public void changeUserTaskStartDate(final String processInstanceId,
                                      final String taskId,
                                      final OffsetDateTime startDate) {
    String sql = "UPDATE ACT_HI_TASKINST " +
      "SET START_TIME_ = ? WHERE " +
      "PROC_INST_ID_ = ?" +
      "AND TASK_DEF_KEY_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setTimestamp(1, toLocalTimestampWithoutNanos(startDate));
    statement.setString(2, processInstanceId);
    statement.setString(3, taskId);
    statement.executeUpdate();
    connection.commit();
  }

  @SneakyThrows
  public void changeUserTaskEndDate(final String processInstanceId,
                                    final String taskId,
                                    final OffsetDateTime endDate) {
    String sql = "UPDATE ACT_HI_TASKINST " +
      "SET END_TIME_ = ? WHERE " +
      "PROC_INST_ID_ = ?" +
      "AND TASK_DEF_KEY_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setTimestamp(1, toLocalTimestampWithoutNanos(endDate));
    statement.setString(2, processInstanceId);
    statement.setString(3, taskId);
    statement.executeUpdate();
    connection.commit();
  }

  public void changeUserTaskAssigneeOperationTimestamp(final String taskId,
                                                       final OffsetDateTime timestamp) throws SQLException {
    String sql = "UPDATE ACT_HI_IDENTITYLINK " +
      "SET TIMESTAMP_ = ? WHERE " +
      "TYPE_ = ?" +
      "AND TASK_ID_ = ?" +
      "AND OPERATION_TYPE_ = 'add'";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setTimestamp(1, toLocalTimestampWithoutNanos(timestamp));
    statement.setString(2, IdentityLinkLogType.ASSIGNEE.getId());
    statement.setString(3, taskId);
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


  public void changeVariableName(String processInstanceId, String oldVariableName, String newVariableName) throws
                                                                                                           SQLException {
    String sql = "UPDATE ACT_HI_DETAIL " +
      "SET NAME_ = ? WHERE PROC_INST_ID_ = ? AND NAME_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setString(1, newVariableName);
    statement.setString(2, processInstanceId);
    statement.setString(3, oldVariableName);
    statement.executeUpdate();
    connection.commit();
  }

  @SneakyThrows
  public void changeProcessInstanceStartDates(Map<String, OffsetDateTime> processInstanceIdToStartDate) {
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(UPDATE_INSTANCE_START_TIME_SQL));
    for (Map.Entry<String, OffsetDateTime> idToStartDate : processInstanceIdToStartDate.entrySet()) {
      prepareAndExecuteStatementForTimeFieldUpdate(
        statement,
        idToStartDate.getKey(),
        idToStartDate.getValue()
      );
    }
    connection.commit();
  }

  @SneakyThrows
  public void changeProcessInstanceEndDates(Map<String, OffsetDateTime> processInstanceIdToEndDate) {
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(UPDATE_INSTANCE_END_TIME_SQL));
    for (Map.Entry<String, OffsetDateTime> idToEndDate : processInstanceIdToEndDate.entrySet()) {
      prepareAndExecuteStatementForTimeFieldUpdate(
        statement,
        idToEndDate.getKey(),
        idToEndDate.getValue()
      );
    }
    connection.commit();
  }

  @SneakyThrows
  public void changeProcessInstanceStartDate(String processInstanceId, OffsetDateTime startDate) {
    changeProcessInstanceStartAndEndDate(processInstanceId, startDate, null);
  }

  @SneakyThrows
  public void changeProcessInstanceEndDate(String processInstanceId, OffsetDateTime endDate) {
    changeProcessInstanceStartAndEndDate(processInstanceId, null, endDate);
  }

  @SneakyThrows
  public void changeProcessInstanceStartAndEndDate(String processInstanceId,
                                                   OffsetDateTime startDate,
                                                   OffsetDateTime endDate) {
    PreparedStatement statement;
    if (startDate != null) {
      statement = connection.prepareStatement(handleDatabaseSyntax(UPDATE_INSTANCE_START_TIME_SQL));
      prepareAndExecuteStatementForTimeFieldUpdate(statement, processInstanceId, startDate);
    }
    if (endDate != null) {
      statement = connection.prepareStatement(handleDatabaseSyntax(UPDATE_INSTANCE_END_TIME_SQL));
      prepareAndExecuteStatementForTimeFieldUpdate(statement, processInstanceId, endDate);
    }
    if (startDate != null || endDate != null) {
      connection.commit();
    }
  }

  @SneakyThrows
  public void changeIncidentCreationDate(final String processInstanceId, final OffsetDateTime creationDate) {
    changeIncidentCreationAndEndDateIfPresent(processInstanceId, creationDate, null);
  }

  @SneakyThrows
  public void changeIncidentCreationAndEndDateIfPresent(final String processInstanceId,
                                                        final OffsetDateTime creationDate,
                                                        final OffsetDateTime endDate) {
    PreparedStatement statement;
    if (creationDate != null) {
      String sql = "UPDATE ACT_HI_INCIDENT SET CREATE_TIME_ = ? WHERE PROC_INST_ID_ = ?";
      statement = connection.prepareStatement(handleDatabaseSyntax(sql));
      prepareAndExecuteStatementForTimeFieldUpdate(statement, processInstanceId, creationDate);
    }
    if (endDate != null) {
      String sql = "UPDATE ACT_HI_INCIDENT SET END_TIME_ = ? WHERE PROC_INST_ID_ = ? AND END_TIME_ is not null";
      statement = connection.prepareStatement(handleDatabaseSyntax(sql));
      prepareAndExecuteStatementForTimeFieldUpdate(statement, processInstanceId, endDate);
    }
    if (creationDate != null || endDate != null) {
      connection.commit();
    }
  }

  public int countHistoricActivityInstances() throws SQLException {
    String sql = "select count(*) as total from act_hi_actinst;";
    String postgresSQL = "SELECT reltuples AS total FROM pg_class WHERE relname = 'act_hi_actinst';";
    sql = usePostgresOptimizations() ? postgresSQL : sql;
    ResultSet statement = connection.createStatement().executeQuery(sql);
    statement.next();
    return statement.getInt("total");
  }

  public int countHistoricProcessInstances() throws SQLException {
    String sql = "select count(*) as total from act_hi_procinst;";
    String postgresSQL =
      "SELECT reltuples AS total FROM pg_class WHERE relname = 'act_hi_procinst';";
    sql = usePostgresOptimizations() ? postgresSQL : sql;
    ResultSet statement =
      connection.createStatement().executeQuery(sql);
    statement.next();
    return statement.getInt("total");
  }

  public int countHistoricVariableInstances() throws SQLException {
    String sql = "select count(*) as total from act_hi_varinst;";
    String postgresSQL =
      "SELECT reltuples AS total FROM pg_class WHERE relname = 'act_hi_varinst';";
    sql = usePostgresOptimizations() ? postgresSQL : sql;
    ResultSet statement =
      connection.createStatement().executeQuery(sql);
    statement.next();
    int totalAmount = statement.getInt("total");

    // subtract all case and complex variables
    sql = "select count(*) as total from act_hi_varinst " +
      "where var_type_ not in ('string', 'double', 'integer', 'long', 'short', 'date', 'boolean' ) " +
      "or CASE_INST_ID_  is not null;";
    statement =
      connection.createStatement().executeQuery(sql);
    statement.next();
    totalAmount -= statement.getInt("total");

    return totalAmount;
  }

  public int countProcessDefinitions() throws SQLException {
    String sql = "select count(*) as total from act_re_procdef;";
    ResultSet statement =
      connection.createStatement().executeQuery(sql);
    statement.next();
    return statement.getInt("total");
  }

  @SneakyThrows
  public void changeDecisionInstanceEvaluationDate(OffsetDateTime fromEvaluationDateTime,
                                                   OffsetDateTime newEvaluationDateTime) {
    final String sql = "UPDATE ACT_HI_DECINST " +
      "SET EVAL_TIME_ = ? WHERE EVAL_TIME_ >= ?";
    final PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setTimestamp(1, toLocalTimestampWithoutNanos(newEvaluationDateTime));
    statement.setTimestamp(2, toLocalTimestampWithoutNanos(fromEvaluationDateTime));
    statement.executeUpdate();
    connection.commit();
  }

  public void changeDecisionInstanceEvaluationDate(String decisionDefinitionId,
                                                   OffsetDateTime newEvaluationDateTime) throws SQLException {
    final String sql = "UPDATE ACT_HI_DECINST " +
      "SET EVAL_TIME_ = ? WHERE DEC_DEF_ID_ = ?";
    final PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setTimestamp(1, toLocalTimestampWithoutNanos(newEvaluationDateTime));
    statement.setString(2, decisionDefinitionId);
    statement.executeUpdate();
    connection.commit();
  }

  @SneakyThrows
  public void setDecisionOutputStringVariableValueToNull(String variableName, String oldValue) {
    String sql = "UPDATE ACT_HI_DEC_OUT SET TEXT_ = NULL WHERE CLAUSE_ID_ = ? AND TEXT_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setString(1, variableName);
    statement.setString(2, oldValue);
    statement.executeUpdate();
    connection.commit();
  }

  public void changeLinkLogTimestampForLastTwoAssigneeOperations(OffsetDateTime timestamp) throws SQLException {
    String sql = "UPDATE ACT_HI_IDENTITYLINK " +
      "SET TIMESTAMP_ = ? WHERE " +
      "TYPE_ = ? AND " +
      "ID_ IN (SELECT ID_ FROM ACT_HI_IDENTITYLINK ORDER BY TIMESTAMP_ DESC LIMIT 2)";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setTimestamp(1, toLocalTimestampWithoutNanos(timestamp));
    statement.setString(2, IdentityLinkLogType.ASSIGNEE.getId());
    statement.executeUpdate();
    connection.commit();
  }

  public void changeProcessInstanceTenantId(final String processInstanceId,
                                            final String newTenantId) throws SQLException {
    String sql = "UPDATE ACT_HI_PROCINST " +
      "SET TENANT_ID_ = ? WHERE " +
      "PROC_INST_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setString(1, newTenantId);
    statement.setString(2, processInstanceId);
    statement.executeUpdate();
    connection.commit();
  }

  public List<String> getDecisionInstanceIdsWithEvaluationDateEqualTo(OffsetDateTime evaluationDateTime)
    throws SQLException {
    final List<String> result = new ArrayList<>();

    final String sql = "SELECT ID_ FROM ACT_HI_DECINST " +
      "WHERE EVAL_TIME_ = ?";
    final PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setTimestamp(1, toLocalTimestampWithoutNanos(evaluationDateTime));
    final ResultSet resultSet = statement.executeQuery();

    while (resultSet.next()) {
      result.add(resultSet.getString(1));
    }
    return result;
  }

  @SneakyThrows
  public void changeVersionOfDecisionDefinitionWithDeploymentId(final String version,
                                                                final String deploymentId) {
    String sql = "UPDATE ACT_RE_DECISION_DEF " +
      "SET VERSION_ = ? " +
      "WHERE DEPLOYMENT_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setString(1, version);
    statement.setString(2, deploymentId);
    statement.executeUpdate();
    connection.commit();
  }

  @SneakyThrows
  public void changeVersionOfProcessDefinitionWithDeploymentId(final String version,
                                                               final String deploymentId) {
    String sql = "UPDATE ACT_RE_PROCDEF " +
      "SET VERSION_ = ? " +
      "WHERE DEPLOYMENT_ID_ = ?";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.setString(1, version);
    statement.setString(2, deploymentId);
    statement.executeUpdate();
    connection.commit();
  }

  @SneakyThrows
  public void removeProcessInstanceIdFromAllHistoricIncidents() {
    String sql = "UPDATE ACT_HI_INCIDENT " +
      "SET PROC_INST_ID_ = NULL, ROOT_PROC_INST_ID_ = NULL";
    PreparedStatement statement = connection.prepareStatement(handleDatabaseSyntax(sql));
    statement.executeUpdate();
    connection.commit();
  }

  public int countDecisionDefinitions() throws SQLException {
    String sql = "select count(*) as total from act_re_decision_def;";
    ResultSet statement = connection.createStatement().executeQuery(sql);
    statement.next();
    return statement.getInt("total");
  }

  public int countHistoricDecisionInstances() throws SQLException {
    String sql = "select count(*) as total from act_hi_decinst;";
    String postgresSQL = "SELECT reltuples AS total FROM pg_class WHERE relname = 'act_hi_decinst';";
    sql = usePostgresOptimizations() ? postgresSQL : sql;
    ResultSet statement = connection.createStatement().executeQuery(sql);
    statement.next();
    return statement.getInt("total");
  }

  private boolean usePostgresOptimizations() {
    return DATABASE_POSTGRESQL.equals(database) && usePostgresOptimizations;
  }

  private Timestamp toLocalTimestampWithoutNanos(final OffsetDateTime offsetDateTime) {
    // since Java 9 there is a new implementation of the underlying clock in Java
    // https://bugs.openjdk.java.net/browse/JDK-8068730
    // this introduces system specific increased precision when creating new date instances
    //
    // when using timestamps with the data base we have to limit the precision to millis
    // otherwise date equals queries like finishedAt queries won't work as expected with modified timestamps
    // due to the added precision that is not available on the engines REST-API
    return Timestamp.valueOf(
      offsetDateTime
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
    );
  }

  private void prepareAndExecuteStatementForTimeFieldUpdate(final PreparedStatement statement,
                                                            final String processInstanceId,
                                                            final OffsetDateTime newDate) throws SQLException {
    statement.setTimestamp(1, toLocalTimestampWithoutNanos(newDate));
    statement.setString(2, processInstanceId);
    statement.executeUpdate();
  }
}
