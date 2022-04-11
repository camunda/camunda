/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.data.generation.generators;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class DBConnector {
  private Connection connection = null;
  private static final int BATCH_SIZE = 10000;
  private static final String PROCESS_INSTANCE_TABLE = "ACT_HI_PROCINST";

  public DBConnector(final String driver, final String dbUrl, final String dbUser, final String dbUserPassword) {
    initDatabaseConnection(driver, dbUrl, dbUser, dbUserPassword);
  }

  private void initDatabaseConnection(String jdbcDriver, String dbUrl, String dbUser, String dbPassword) {
    try {
      Class.forName(jdbcDriver);
      log.debug("Connecting to a selected " + dbUrl + " database...");
      connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
      log.debug("Connected to " + dbUrl + " database successfully...");
      // to be able to batch sql statements
      connection.setAutoCommit(false);
    } catch (SQLException e) {
      log.error("Error while trying to connect to database " + dbUrl + "!", e);
    } catch (ClassNotFoundException e) {
      log.error("Could not find " + dbUrl + " jdbc driver class!", e);
    }
  }

  @SneakyThrows
  public void updateProcessInstances(String startDate, String endDate) {
    Map<String, String> ids = new HashMap<>();
    //get ids of first batch
    String sql = "SELECT ID_, END_TIME_ FROM " + PROCESS_INSTANCE_TABLE + " ORDER BY ID_ LIMIT ? OFFSET ?";
    try (final PreparedStatement statement = connection.prepareStatement(sql)) {
      int currentOffset = 0;
      while (true) {
        statement.setInt(1, BATCH_SIZE);
        statement.setInt(2, currentOffset);
        currentOffset += BATCH_SIZE;
        ResultSet rs = statement.executeQuery();
        int currentBatchRowCount = 0;

        while (rs.next()) {
          currentBatchRowCount++;
          ids.put(rs.getString(1), rs.getString( 2));
        }
        OffsetDateTime[] startDates = new OffsetDateTime[currentBatchRowCount];
        OffsetDateTime[] endDates = new OffsetDateTime[currentBatchRowCount];
        String[] instanceIds = ids.keySet().toArray(new String[0]);

        for (int i = 0; i < instanceIds.length; i++) {
          List<LocalDateTime> dates = generateDates(startDate, endDate);
          OffsetDateTime startDateTime = OffsetDateTime.of(dates.get(0), ZoneOffset.UTC);
          OffsetDateTime endDateTime = OffsetDateTime.of(dates.get(1), ZoneOffset.UTC);
          startDates[i] = startDateTime;
          //Only set the new end date in case an end date already existed (process is not done)
          if(ids.get(instanceIds[i]) != null) {
            endDates[i] = endDateTime;
          } else {
            endDates[i] = null;
          }

        }
        changeProcessInstanceStartAndEndDateInBatches(startDates, endDates, instanceIds);
        ids.clear();

        if (currentBatchRowCount < BATCH_SIZE) {
          break;
        }
      }
    } catch (SQLException e) {
      log.error("Error while fetching ids from process instance table", e);
    }
  }

  @SneakyThrows
  public void changeProcessInstanceStartAndEndDateInBatches(OffsetDateTime[] startDateTime,
                                                            OffsetDateTime[] endDateTime, String[] processInstanceId) {
    String sql = "UPDATE " + PROCESS_INSTANCE_TABLE +
      " SET START_TIME_ = ? , END_TIME_ = ?" +
      "WHERE ID_ = ?";
    try (final PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < processInstanceId.length; i++) {
        statement.setTimestamp(1, toLocalTimestampWithoutNanos(startDateTime[i]));
        //Only add the end date to the SQL statement if there is an end date to set
        if(endDateTime[i] != null) {
          statement.setTimestamp(2, toLocalTimestampWithoutNanos(endDateTime[i]));
        } else {
          statement.setNull(2, java.sql.Types.TIMESTAMP);
        }
        statement.setString(3, processInstanceId[i]);
        statement.addBatch();
      }
      statement.executeBatch();
      connection.commit();
    } catch (SQLException e) {
      log.error("Error while updating process instance table ", e);
    }
  }

  public List<LocalDateTime> generateDates(String startDate, String endDate) {
    List<LocalDateTime> dates = new ArrayList<>();

    int[] startDateComponets = getDateComponents(startDate);
    int[] endDateComponets = getDateComponents(endDate);

    LocalDateTime from = LocalDateTime.of(startDateComponets[2], startDateComponets[1], startDateComponets[0], 1, 1);
    LocalDateTime to = LocalDateTime.of(endDateComponets[2], endDateComponets[1], endDateComponets[0], 1, 1);

    long days = from.until(to, ChronoUnit.DAYS);
    long randomDays = ThreadLocalRandom.current().nextLong(days + 1);
    LocalDateTime randomStartDate = from.plusDays(randomDays);
    dates.add(randomStartDate);

    days = randomStartDate.until(to, ChronoUnit.DAYS);
    randomDays = ThreadLocalRandom.current().nextLong(days + 1);
    LocalDateTime randomEndDate = randomStartDate.plusDays(randomDays);
    dates.add(randomEndDate);
    return dates;
  }

  public static int[] getDateComponents(String date) {
    return Arrays.stream(date.split("/")).mapToInt(Integer::parseInt).toArray();
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
}
