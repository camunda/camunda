/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.BackupType;
import io.camunda.management.backups.CheckpointState;
import io.camunda.management.backups.Error;
import io.camunda.management.backups.PartitionBackupRange;
import io.camunda.management.backups.PartitionBackupState;
import io.camunda.management.backups.PartitionCheckpointState;
import io.camunda.management.backups.TakeBackupRuntimeResponse;
import io.camunda.zeebe.backup.client.api.BackupAlreadyExistException;
import io.camunda.zeebe.backup.client.api.BackupApi;
import io.camunda.zeebe.backup.client.api.BackupRequestHandler;
import io.camunda.zeebe.backup.client.api.BackupStatus;
import io.camunda.zeebe.backup.client.api.PartitionBackupStatus;
import io.camunda.zeebe.backup.client.api.State;
import io.camunda.zeebe.backup.common.CheckpointIdGenerator;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import io.camunda.zeebe.gateway.admin.IncompleteTopologyException;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse.CheckpointInfo;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.netty.channel.ConnectTimeoutException;
import java.net.ConnectException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;

@Execution(ExecutionMode.CONCURRENT)
final class BackupEndpointTest {

  static class Failures implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(final ExtensionContext extensionContext) {
      return Stream.of(
          Arguments.of(new ConnectException("failure"), 502),
          Arguments.of(new ConnectTimeoutException("failure"), 504),
          Arguments.of(new TimeoutException("failure"), 504),
          Arguments.of(new IncompleteTopologyException("failure"), 502),
          Arguments.of(
              new BrokerErrorException(
                  new BrokerError(ErrorCode.PARTITION_LEADER_MISMATCH, "failure")),
              502),
          Arguments.of(
              new BrokerErrorException(new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "failure")),
              503),
          Arguments.of(
              new BrokerErrorException(new BrokerError(ErrorCode.PARTITION_UNAVAILABLE, "failure")),
              500),
          Arguments.of(
              new BrokerErrorException(new BrokerError(ErrorCode.INTERNAL_ERROR, "failure")), 500),
          Arguments.of(
              new BrokerErrorException(new BrokerError(ErrorCode.UNSUPPORTED_MESSAGE, "failure")),
              400),
          Arguments.of(new RuntimeException("failure"), 500));
    }
  }

  @Nested
  final class TakeTest {
    @Test
    void shouldReturnErrorOnException() {
      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      when(config.isContinuous()).thenReturn(false);
      final var endpoint = new BackupEndpoint(api, config);
      final var failure = new RuntimeException("failure");
      doThrow(failure).when(api).takeBackup(anyLong());

      // when
      final WebEndpointResponse<?> response = endpoint.take(1);

      // then
      assertThat(response.getBody())
          .asInstanceOf(InstanceOfAssertFactories.type(Error.class))
          .isEqualTo(new Error().message("failure"));
    }

    @Test
    void shouldReturn409WhenBackupAlreadyExists() {

      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      when(config.isContinuous()).thenReturn(false);
      final var endpoint = new BackupEndpoint(api, config);
      final var failure = new BackupAlreadyExistException(2, 1);
      doReturn(CompletableFuture.failedFuture(failure)).when(api).takeBackup(anyLong());

      // when
      final WebEndpointResponse<?> response = endpoint.take(1);

      // then
      assertThat(response.getStatus()).isEqualTo(409);
      assertThat(response.getBody()).isInstanceOf(Error.class);
    }

    @ParameterizedTest
    @ArgumentsSource(Failures.class)
    void shouldReturnCorrectErrorCode(final Throwable error, final int expectedCode) {
      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      when(config.isContinuous()).thenReturn(false);
      final var endpoint = new BackupEndpoint(api, config);
      doReturn(CompletableFuture.failedFuture(error)).when(api).takeBackup(anyLong());

      // when
      final var response = endpoint.take(1);

      // then
      assertThat(response.getStatus()).isEqualTo(expectedCode);
      assertThat(response.getBody())
          .asInstanceOf(InstanceOfAssertFactories.type(Error.class))
          .extracting(Error::getMessage)
          .asString()
          .contains("failure");
    }

    @Test
    void shouldReturn400WhenContinuousBackupsAreEnabled() {

      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      when(config.isContinuous()).thenReturn(true);
      final var endpoint = new BackupEndpoint(api, config);

      // when
      final WebEndpointResponse<?> response = endpoint.take(1);

      // then
      assertThat(response.getStatus()).isEqualTo(400);
      assertThat(response.getBody())
          .isInstanceOf(Error.class)
          .extracting("message")
          .isEqualTo(
              "Cannot take backup with predetermined backupId when continuous backups are enabled."
                  + " Use POST actuator/backupRuntime without specifying a backupId.");
    }

    @Test
    void backupIdShouldBeCloseToNowWhenContinuousBackupsEnabled() throws InterruptedException {

      // given
      final var requestHandler =
          mock(BackupRequestHandler.class, withSettings().useConstructor(mock(BrokerClient.class)));
      final var config = mock(BackupCfg.class);
      when(config.isContinuous()).thenReturn(true);
      final var endpoint = new BackupEndpoint(requestHandler, config);

      doAnswer(
              invocation -> {
                final Long backupId = invocation.getArgument(0);
                return CompletableFuture.completedFuture(backupId);
              })
          .when(requestHandler)
          .takeBackup(anyLong());
      when(requestHandler.takeBackup()).thenCallRealMethod();

      // when
      final var now = Instant.now();
      final WebEndpointResponse<?> firstBackup = endpoint.take();
      Thread.sleep(100);
      final WebEndpointResponse<?> secondBackup = endpoint.take();

      // then
      verify(requestHandler, times(2)).takeBackup();
      assertThat(firstBackup.getStatus()).isEqualTo(202);
      var msg = ((TakeBackupRuntimeResponse) firstBackup.getBody()).getMessage();
      final var backupId1 = Long.parseLong(msg.replaceAll(".*id (\\d+).*", "$1"));

      assertThat(secondBackup.getStatus()).isEqualTo(202);
      msg = ((TakeBackupRuntimeResponse) secondBackup.getBody()).getMessage();
      final var backupId2 = Long.parseLong(msg.replaceAll(".*id (\\d+).*", "$1"));

      assertThat(backupId2).isGreaterThan(backupId1);
      assertThat(Instant.ofEpochMilli(backupId2))
          .isCloseTo(Instant.ofEpochMilli(backupId1), within(2, ChronoUnit.SECONDS))
          .isCloseTo(now, within(2, ChronoUnit.SECONDS));

      assertThat(Instant.ofEpochMilli(backupId1)).isCloseTo(now, within(2, ChronoUnit.SECONDS));
    }

    @Test
    void backupIdShouldIncludeOffsetOnContinuousBackups() {

      // given
      final long offset = 20251104131520L;
      final var requestHandler =
          mock(
              BackupRequestHandler.class,
              withSettings()
                  .useConstructor(mock(BrokerClient.class), new CheckpointIdGenerator(offset)));
      final var config = mock(BackupCfg.class);
      when(config.isContinuous()).thenReturn(true);
      when(config.getOffset()).thenReturn(offset);
      final var endpoint = new BackupEndpoint(requestHandler, config);

      when(requestHandler.takeBackup()).thenCallRealMethod();
      doAnswer(
              invocation -> {
                final var backupId = invocation.getArgument(0);
                return CompletableFuture.completedFuture(backupId);
              })
          .when(requestHandler)
          .takeBackup(anyLong());

      // when
      final var now = Instant.now();
      final WebEndpointResponse<?> response = endpoint.take();

      // then
      verify(requestHandler, times(1)).takeBackup(anyLong());
      assertThat(response.getStatus()).isEqualTo(202);
      final var msg = ((TakeBackupRuntimeResponse) response.getBody()).getMessage();
      final var backupId = Long.parseLong(msg.replaceAll(".*id (\\d+).*", "$1"));

      final var actualTimestamp = backupId - offset;

      assertThat(Instant.ofEpochMilli(actualTimestamp))
          .isCloseTo(now, within(2, ChronoUnit.SECONDS));
    }
  }

  @Nested
  final class StatusTest {
    @Test
    void shouldReturnErrorOnException() {
      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      when(config.isContinuous()).thenReturn(false);
      final var endpoint = new BackupEndpoint(api, config);
      final var failure = new RuntimeException("failure");
      doThrow(failure).when(api).getStatus(anyLong());

      // when
      final WebEndpointResponse<?> response = endpoint.query(new String[] {"1"});

      // then
      assertThat(response.getBody())
          .asInstanceOf(InstanceOfAssertFactories.type(Error.class))
          .isEqualTo(new Error().message("failure"));
    }

    @Test
    void shouldReturn404WhenBackupDoesNotExist() {
      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      when(config.isContinuous()).thenReturn(false);
      final var endpoint = new BackupEndpoint(api, config);
      final var backupStatus =
          new BackupStatus(1, State.DOES_NOT_EXIST, Optional.empty(), List.of());
      doReturn(CompletableFuture.completedFuture(backupStatus)).when(api).getStatus(anyLong());

      // when
      final WebEndpointResponse<?> response = endpoint.query(new String[] {"1"});

      // then
      assertThat(response.getStatus()).isEqualTo(404);
      assertThat(response.getBody())
          .asInstanceOf(InstanceOfAssertFactories.type(Error.class))
          .isEqualTo(new Error().message("Backup with id 1 does not exist"));
    }

    @ParameterizedTest
    @ArgumentsSource(Failures.class)
    void shouldReturnCorrectErrorCode(final Throwable error, final int expectedCode) {
      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      when(config.isContinuous()).thenReturn(false);
      final var endpoint = new BackupEndpoint(api, config);
      doReturn(CompletableFuture.failedFuture(error)).when(api).getStatus(anyLong());

      // when
      final var response = endpoint.query(new String[] {"1"});

      // then
      assertThat(response.getStatus()).isEqualTo(expectedCode);
      assertThat(response.getBody())
          .asInstanceOf(InstanceOfAssertFactories.type(Error.class))
          .extracting(Error::getMessage)
          .asString()
          .contains("failure");
    }

    @Test
    void shouldReturnCompletedBackupStatus() throws JsonProcessingException {
      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      when(config.isContinuous()).thenReturn(false);
      final var endpoint = new BackupEndpoint(api, config);
      final var status = createPartitionBackupStatus();
      doReturn(CompletableFuture.completedFuture(status)).when(api).getStatus(anyLong());

      final String expectedJson =
          """
          {
             "backupId" : 1,
             "state" : "COMPLETED",
             "details": [
               {
                 "partitionId": 1,
                 "state": "COMPLETED",
                 "createdAt": "2022-09-19T14:44:17.340409393Z",
                 "lastUpdatedAt": "2022-09-20T14:44:17.340409393Z",
                 "snapshotId" : "1-1-1-1",
                 "firstLogPosition": 5,
                 "checkpointPosition": 1,
                 "brokerId": 0,
                 "brokerVersion": "8.0.6"
               },
               {
                 "partitionId": 2,
                 "state": "COMPLETED",
                 "createdAt": "2022-09-19T14:44:17.340409393Z",
                 "lastUpdatedAt": "2022-09-20T14:44:17.340409393Z",
                 "snapshotId" : "1-1-1-1",
                 "firstLogPosition": 5,
                 "checkpointPosition": 1,
                 "brokerId": 0,
                 "brokerVersion": "8.0.6"
               }
             ]
           }
          """;
      final ObjectMapper mapper = new ObjectMapper();
      mapper.registerModule(new JavaTimeModule());
      final BackupInfo expectedResponse = mapper.readValue(expectedJson, BackupInfo.class);

      // when
      final WebEndpointResponse<?> response = endpoint.query(new String[] {"1"});

      // then
      assertThat(response.getBody())
          .asInstanceOf(InstanceOfAssertFactories.type(BackupInfo.class))
          .isEqualTo(expectedResponse);
    }

    @Test
    void shouldReturnFailedBackupStatus() throws JsonProcessingException {
      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      when(config.isContinuous()).thenReturn(false);
      final var endpoint = new BackupEndpoint(api, config);
      final var status = createFailedBackupStatus();
      doReturn(CompletableFuture.completedFuture(status)).when(api).getStatus(anyLong());

      final String expectedJson =
          """
          {
             "backupId" : 1,
             "state" : "FAILED",
             "failureReason": "Failed backup",
             "details": [
               {
                 "partitionId": 1,
                 "state": "COMPLETED",
                 "createdAt": "2022-09-19T14:44:17.340409393Z",
                 "lastUpdatedAt": "2022-09-20T14:44:17.340409393Z",
                 "snapshotId" : "1-1-1-1",
                 "firstLogPosition": 5,
                 "checkpointPosition": 1,
                 "brokerId": 0,
                 "brokerVersion": "8.0.6"
               },
               {
                 "partitionId": 2,
                 "state": "FAILED",
                 "failureReason": "Failed backup"
               }
             ]
           }
          """;
      final ObjectMapper mapper = new ObjectMapper();
      mapper.registerModule(new JavaTimeModule());
      final BackupInfo expectedResponse = mapper.readValue(expectedJson, BackupInfo.class);

      // when
      final WebEndpointResponse<?> response = endpoint.query(new String[] {"1"});

      // then
      assertThat(response.getBody())
          .asInstanceOf(InstanceOfAssertFactories.type(BackupInfo.class))
          .isEqualTo(expectedResponse);
    }

    private BackupStatus createPartitionBackupStatus() {
      return new BackupStatus(
          1L,
          State.COMPLETED,
          Optional.empty(),
          List.of(createPartitionBackupStatus(1), createPartitionBackupStatus(2)));
    }

    private PartitionBackupStatus createPartitionBackupStatus(final int partitionId) {
      return new PartitionBackupStatus(
          partitionId,
          BackupStatusCode.COMPLETED,
          Optional.empty(),
          Optional.of("2022-09-19T14:44:17.340409393Z"),
          Optional.of("2022-09-20T14:44:17.340409393Z"),
          Optional.of("1-1-1-1"),
          OptionalLong.of(5),
          OptionalLong.of(1),
          OptionalInt.of(0),
          Optional.of("8.0.6"));
    }

    private BackupStatus createFailedBackupStatus() {
      return new BackupStatus(
          1L,
          State.FAILED,
          Optional.of("Failed backup"),
          List.of(createPartitionBackupStatus(1), createFailedPartitionBackupStatus(2)));
    }

    @SuppressWarnings("SameParameterValue")
    private PartitionBackupStatus createFailedPartitionBackupStatus(final int partitionId) {
      return new PartitionBackupStatus(
          partitionId,
          BackupStatusCode.FAILED,
          Optional.of("Failed backup"),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalInt.empty(),
          Optional.empty());
    }
  }

  @Nested
  final class ListTest {
    @Test
    void shouldReturnErrorOnException() {
      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      when(config.isContinuous()).thenReturn(false);
      final var endpoint = new BackupEndpoint(api, config);
      final var failure = new RuntimeException("failure");
      doThrow(failure).when(api).listBackups("*");

      // when
      final WebEndpointResponse<?> response = endpoint.listAll();

      // then
      assertThat(response.getBody())
          .asInstanceOf(InstanceOfAssertFactories.type(Error.class))
          .isEqualTo(new Error().message("failure"));
    }

    @ParameterizedTest
    @ArgumentsSource(Failures.class)
    void shouldReturnCorrectErrorCode(final Throwable error, final int expectedCode) {
      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      when(config.isContinuous()).thenReturn(false);
      final var endpoint = new BackupEndpoint(api, config);
      doReturn(CompletableFuture.failedFuture(error)).when(api).listBackups("*");

      // when
      final var response = endpoint.listAll();

      // then
      assertThat(response.getStatus()).isEqualTo(expectedCode);
      assertThat(response.getBody())
          .asInstanceOf(InstanceOfAssertFactories.type(Error.class))
          .extracting(Error::getMessage)
          .asString()
          .contains("failure");
    }

    @Test
    void shouldReturnListOfBackups() throws JsonProcessingException {
      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      when(config.isContinuous()).thenReturn(false);
      final var endpoint = new BackupEndpoint(api, config);
      final var backup1 =
          new BackupStatus(
              1,
              State.COMPLETED,
              Optional.empty(),
              List.of(createPartialPartitionStatus(BackupStatusCode.COMPLETED)));
      final var backup2 =
          new BackupStatus(
              2,
              State.IN_PROGRESS,
              Optional.empty(),
              List.of(createPartialPartitionStatus(BackupStatusCode.IN_PROGRESS)));
      doReturn(CompletableFuture.completedFuture(List.of(backup1, backup2)))
          .when(api)
          .listBackups("*");

      final String expectedJson =
          """
          [
            {
               "backupId" : 1,
               "state" : "COMPLETED",
               "details": [
                 {
                   "partitionId": 1,
                   "state": "COMPLETED",
                   "createdAt": "2022-09-19T14:44:17.340409393Z",
                   "brokerVersion": "8.0.6"
                 }
               ]
             },
             {
               "backupId" : 2,
               "state" : "IN_PROGRESS",
               "details": [
                 {
                   "partitionId": 1,
                   "state": "IN_PROGRESS",
                   "createdAt": "2022-09-19T14:44:17.340409393Z",
                   "brokerVersion": "8.0.6"
                 }
               ]
             }
           ]
          """;
      final ObjectMapper mapper = new ObjectMapper();
      mapper.registerModule(new JavaTimeModule());
      final List<BackupInfo> expectedResponse =
          mapper.readValue(
              expectedJson,
              mapper.getTypeFactory().constructCollectionType(List.class, BackupInfo.class));

      // when
      final WebEndpointResponse<?> response = endpoint.listAll();

      // then
      assertThat(response.getBody())
          .asInstanceOf(InstanceOfAssertFactories.list(BackupInfo.class))
          .containsExactlyInAnyOrderElementsOf(expectedResponse);
    }

    @Test
    void shouldReturnEmptyList() {
      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      when(config.isContinuous()).thenReturn(false);
      final var endpoint = new BackupEndpoint(api, config);
      doReturn(CompletableFuture.completedFuture(List.of())).when(api).listBackups("*");

      // when
      final WebEndpointResponse<?> response = endpoint.listAll();

      // then
      assertThat(response.getBody())
          .asInstanceOf(InstanceOfAssertFactories.list(BackupInfo.class))
          .isEmpty();
    }

    private PartitionBackupStatus createPartialPartitionStatus(final BackupStatusCode completed) {
      return new PartitionBackupStatus(
          1,
          completed,
          Optional.empty(),
          Optional.of("2022-09-19T14:44:17.340409393Z"),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalInt.empty(),
          Optional.of("8.0.6"));
    }
  }

  @Nested
  final class DeleteTest {

    @Test
    void shouldReturnErrorOnException() {
      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      when(config.isContinuous()).thenReturn(false);
      final var endpoint = new BackupEndpoint(api, config);
      final var failure = new RuntimeException("failure");
      doThrow(failure).when(api).deleteBackup(1);

      // when
      final WebEndpointResponse<?> response = endpoint.delete(1);

      // then
      assertThat(response.getBody())
          .asInstanceOf(InstanceOfAssertFactories.type(Error.class))
          .isEqualTo(new Error().message("failure"));
    }

    @ParameterizedTest
    @ArgumentsSource(Failures.class)
    void shouldReturnCorrectErrorCode(final Throwable error, final int expectedCode) {
      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      when(config.isContinuous()).thenReturn(false);
      final var endpoint = new BackupEndpoint(api, config);
      doReturn(CompletableFuture.failedFuture(error)).when(api).deleteBackup(anyLong());

      // when
      final var response = endpoint.delete(1);

      // then
      assertThat(response.getStatus()).isEqualTo(expectedCode);
      assertThat(response.getBody())
          .asInstanceOf(InstanceOfAssertFactories.type(Error.class))
          .extracting(Error::getMessage)
          .asString()
          .contains("failure");
    }

    @Test
    void shouldDeleteBackup() {
      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      when(config.isContinuous()).thenReturn(false);
      final var endpoint = new BackupEndpoint(api, config);
      doReturn(CompletableFuture.completedFuture(null)).when(api).deleteBackup(1);

      // when
      final WebEndpointResponse<?> response = endpoint.delete(1);

      // then
      assertThat(response.getStatus()).isEqualTo(204);
    }
  }

  @Nested
  final class StateTest {
    @Test
    void shouldReturnCheckpointState() {
      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      final var endpoint = new BackupEndpoint(api, config);

      final var stateResponse = new CheckpointStateResponse();
      stateResponse.setCheckpointStates(
          Set.of(
              new CheckpointStateResponse.PartitionCheckpointState(
                  1, 1L, CheckpointType.MARKER, 20L, 10L)));
      final var rangesResponse = new BackupRangesResponse();
      rangesResponse.setRanges(
          List.of(
              new BackupRangesResponse.PartitionBackupRange(
                  1,
                  new CheckpointInfo(
                      1,
                      1L,
                      1L,
                      CheckpointType.SCHEDULED_BACKUP,
                      Instant.now().minus(1, ChronoUnit.DAYS)),
                  new CheckpointInfo(
                      10, 100L, 150L, CheckpointType.SCHEDULED_BACKUP, Instant.now()),
                  Set.of())));

      when(api.getCheckpointState()).thenReturn(CompletableFuture.completedFuture(stateResponse));
      when(api.getBackupRanges()).thenReturn(CompletableFuture.completedFuture(rangesResponse));

      // when
      final WebEndpointResponse<?> response = endpoint.query(new String[] {BackupApi.STATE});

      // then
      assertThat(response.getBody())
          .isInstanceOf(CheckpointState.class)
          .isEqualTo(
              new CheckpointState()
                  .checkpointStates(
                      List.of(
                          new PartitionCheckpointState()
                              .partitionId(1)
                              .checkpointId(1L)
                              .checkpointPosition(10L)
                              .checkpointTimestamp(
                                  OffsetDateTime.ofInstant(
                                      Instant.ofEpochMilli(20L), ZoneId.of("UTC")))
                              .checkpointType(io.camunda.management.backups.CheckpointType.MARKER)))
                  .ranges(
                      List.of(
                          new PartitionBackupRange()
                              .partitionId(1)
                              .start(1L)
                              .end(10L)
                              .missingCheckpoints(List.of()))));
    }

    @Test
    void shouldReturnBackupOnDifferentTypeState() {
      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      final var endpoint = new BackupEndpoint(api, config);

      final var stateResponse = new CheckpointStateResponse();
      stateResponse.setBackupStates(
          Set.of(
              new CheckpointStateResponse.PartitionCheckpointState(
                  1, 1L, CheckpointType.MANUAL_BACKUP, 20L, 10L)));
      when(api.getCheckpointState()).thenReturn(CompletableFuture.completedFuture(stateResponse));
      when(api.getBackupRanges())
          .thenReturn(CompletableFuture.completedFuture(new BackupRangesResponse()));

      // when
      final WebEndpointResponse<?> response = endpoint.query(new String[] {BackupApi.STATE});

      // then
      assertThat(response.getBody())
          .isInstanceOf(CheckpointState.class)
          .isEqualTo(
              new CheckpointState()
                  .backupStates(
                      List.of(
                          new PartitionBackupState()
                              .partitionId(1)
                              .checkpointId(1L)
                              .checkpointPosition(10L)
                              .checkpointTimestamp(
                                  OffsetDateTime.ofInstant(
                                      Instant.ofEpochMilli(20L), ZoneId.of("UTC")))
                              .checkpointType(BackupType.MANUAL_BACKUP))));
    }

    @Test
    void shouldReturnBothCheckpointAndBackupState() {
      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      final var endpoint = new BackupEndpoint(api, config);

      final var stateResponse = new CheckpointStateResponse();
      stateResponse.setBackupStates(
          Set.of(
              new CheckpointStateResponse.PartitionCheckpointState(
                  1, 1L, CheckpointType.MANUAL_BACKUP, 20L, 10L)));
      stateResponse.setCheckpointStates(
          Set.of(
              new CheckpointStateResponse.PartitionCheckpointState(
                  1, 2L, CheckpointType.MARKER, 30L, 50L)));
      when(api.getCheckpointState()).thenReturn(CompletableFuture.completedFuture(stateResponse));
      when(api.getBackupRanges())
          .thenReturn(CompletableFuture.completedFuture(new BackupRangesResponse()));

      // when
      final WebEndpointResponse<?> response = endpoint.query(new String[] {BackupApi.STATE});

      // then
      assertThat(response.getBody())
          .isInstanceOf(CheckpointState.class)
          .isEqualTo(
              new CheckpointState()
                  .backupStates(
                      List.of(
                          new PartitionBackupState()
                              .partitionId(1)
                              .checkpointId(1L)
                              .checkpointPosition(10L)
                              .checkpointTimestamp(
                                  OffsetDateTime.ofInstant(
                                      Instant.ofEpochMilli(20L), ZoneId.of("UTC")))
                              .checkpointType(BackupType.MANUAL_BACKUP)))
                  .checkpointStates(
                      List.of(
                          new PartitionCheckpointState()
                              .partitionId(1)
                              .checkpointId(2L)
                              .checkpointPosition(50L)
                              .checkpointTimestamp(
                                  OffsetDateTime.ofInstant(
                                      Instant.ofEpochMilli(30L), ZoneId.of("UTC")))
                              .checkpointType(
                                  io.camunda.management.backups.CheckpointType.MARKER))));
    }

    @Test
    void shouldReturnEmptyState() {
      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      final var endpoint = new BackupEndpoint(api, config);

      final var stateResponse = new CheckpointStateResponse();

      when(api.getCheckpointState()).thenReturn(CompletableFuture.completedFuture(stateResponse));
      when(api.getBackupRanges())
          .thenReturn(CompletableFuture.completedFuture(new BackupRangesResponse()));

      // when
      final WebEndpointResponse<?> response = endpoint.query(new String[] {BackupApi.STATE});

      // then
      assertThat(response.getBody())
          .isInstanceOf(CheckpointState.class)
          .isEqualTo(new CheckpointState());
    }

    @Test
    void shouldReturnAllPartitionsState() {
      // given
      final var api = mock(BackupApi.class);
      final var config = mock(BackupCfg.class);
      final var endpoint = new BackupEndpoint(api, config);

      final var stateResponse = new CheckpointStateResponse();

      final CheckpointStateResponse.PartitionCheckpointState p1State =
          new CheckpointStateResponse.PartitionCheckpointState(
              1, 1L, CheckpointType.MARKER, 20L, 10L);
      final CheckpointStateResponse.PartitionCheckpointState p2State =
          new CheckpointStateResponse.PartitionCheckpointState(
              2, 1L, CheckpointType.MARKER, 20L, 20L);
      final CheckpointStateResponse.PartitionCheckpointState p3State =
          new CheckpointStateResponse.PartitionCheckpointState(
              3, 1L, CheckpointType.MARKER, 30L, 40L);

      final CheckpointStateResponse.PartitionCheckpointState p1BackupState =
          new CheckpointStateResponse.PartitionCheckpointState(
              1, 1L, CheckpointType.MANUAL_BACKUP, 20L, 10L);
      final CheckpointStateResponse.PartitionCheckpointState p2BackupState =
          new CheckpointStateResponse.PartitionCheckpointState(
              2, 1L, CheckpointType.MANUAL_BACKUP, 20L, 20L);
      final CheckpointStateResponse.PartitionCheckpointState p3BackupState =
          new CheckpointStateResponse.PartitionCheckpointState(
              3, 1L, CheckpointType.MANUAL_BACKUP, 30L, 40L);

      stateResponse.setCheckpointStates(Set.of(p1State, p2State, p3State));
      stateResponse.setBackupStates(Set.of(p1BackupState, p2BackupState, p3BackupState));

      when(api.getCheckpointState()).thenReturn(CompletableFuture.completedFuture(stateResponse));
      when(api.getBackupRanges())
          .thenReturn(CompletableFuture.completedFuture(new BackupRangesResponse()));

      // when
      final WebEndpointResponse<?> response = endpoint.query(new String[] {BackupApi.STATE});

      // then
      assertThat(response.getBody())
          .isInstanceOf(CheckpointState.class)
          .isEqualTo(
              new CheckpointState()
                  .checkpointStates(
                      List.of(
                          new PartitionCheckpointState()
                              .partitionId(1)
                              .checkpointId(1L)
                              .checkpointPosition(10L)
                              .checkpointTimestamp(
                                  OffsetDateTime.ofInstant(
                                      Instant.ofEpochMilli(20L), ZoneId.of("UTC")))
                              .checkpointType(io.camunda.management.backups.CheckpointType.MARKER),
                          new PartitionCheckpointState()
                              .partitionId(2)
                              .checkpointId(1L)
                              .checkpointPosition(20L)
                              .checkpointTimestamp(
                                  OffsetDateTime.ofInstant(
                                      Instant.ofEpochMilli(20L), ZoneId.of("UTC")))
                              .checkpointType(io.camunda.management.backups.CheckpointType.MARKER),
                          new PartitionCheckpointState()
                              .partitionId(3)
                              .checkpointId(1L)
                              .checkpointPosition(40L)
                              .checkpointTimestamp(
                                  OffsetDateTime.ofInstant(
                                      Instant.ofEpochMilli(30L), ZoneId.of("UTC")))
                              .checkpointType(io.camunda.management.backups.CheckpointType.MARKER)))
                  .backupStates(
                      List.of(
                          new PartitionBackupState()
                              .partitionId(1)
                              .checkpointId(1L)
                              .checkpointPosition(10L)
                              .checkpointTimestamp(
                                  OffsetDateTime.ofInstant(
                                      Instant.ofEpochMilli(20L), ZoneId.of("UTC")))
                              .checkpointType(BackupType.MANUAL_BACKUP),
                          new PartitionBackupState()
                              .partitionId(2)
                              .checkpointId(1L)
                              .checkpointPosition(20L)
                              .checkpointTimestamp(
                                  OffsetDateTime.ofInstant(
                                      Instant.ofEpochMilli(20L), ZoneId.of("UTC")))
                              .checkpointType(BackupType.MANUAL_BACKUP),
                          new PartitionBackupState()
                              .partitionId(3)
                              .checkpointId(1L)
                              .checkpointPosition(40L)
                              .checkpointTimestamp(
                                  OffsetDateTime.ofInstant(
                                      Instant.ofEpochMilli(30L), ZoneId.of("UTC")))
                              .checkpointType(BackupType.MANUAL_BACKUP))));
    }
  }
}
