/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs.manifest;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static io.camunda.zeebe.backup.gcs.manifest.StatusCode.COMPLETED;
import static io.camunda.zeebe.backup.gcs.manifest.StatusCode.FAILED;
import static io.camunda.zeebe.backup.gcs.manifest.StatusCode.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.gcs.GcsBackupStoreException.InvalidPersistedManifestState;
import io.camunda.zeebe.backup.gcs.GcsBackupStoreException.UnexpectedManifestState;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class ManifestTest {

  static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .disable(WRITE_DATES_AS_TIMESTAMPS)
          .setSerializationInclusion(Include.NON_ABSENT);

  @Test
  public void shouldDeserialize() throws JsonProcessingException {
    // given
    final var json =
        """
        {
          "id": { "nodeId": 1, "partitionId": 2, "checkpointId": 43 },
          "descriptor": { "checkpointPosition": 2345234, "numberOfPartitions": 3, "brokerVersion": "1.2.0-SNAPSHOT"},
          "statusCode": "IN_PROGRESS",
          "createdAt": "2023-03-14T10:45:08+00:00",
          "modifiedAt": "2023-03-14T10:45:08+00:00"
        }
        """;

    // when
    final var manifest = MAPPER.readValue(json, Manifest.class);

    // then
    final BackupIdentifierImpl id = manifest.id();
    assertThat(id).isNotNull();
    assertThat(id.nodeId()).isEqualTo(1);
    assertThat(manifest.id().partitionId()).isEqualTo(2);
    assertThat(manifest.id().checkpointId()).isEqualTo(43);

    final BackupDescriptorImpl descriptor = manifest.descriptor();
    assertThat(descriptor.brokerVersion()).isEqualTo("1.2.0-SNAPSHOT");
    assertThat(descriptor.checkpointPosition()).isEqualTo(2345234L);
    assertThat(descriptor.numberOfPartitions()).isEqualTo(3);
    assertThat(descriptor.snapshotId()).isNotPresent();

    assertThat(manifest.statusCode()).isEqualTo(IN_PROGRESS);
    assertThat(manifest.createdAt()).isEqualTo(Instant.ofEpochMilli(1678790708000L));
    assertThat(manifest.modifiedAt()).isEqualTo(Instant.ofEpochMilli(1678790708000L));
  }

  @Test
  public void shouldSerialize() throws JsonProcessingException {
    // given
    final var manifest =
        new ManifestImpl(
            new BackupIdentifierImpl(1, 2, 43),
            new BackupDescriptorImpl(Optional.empty(), 2345234L, 3, "1.2.0-SNAPSHOT"),
            IN_PROGRESS,
            Instant.ofEpochMilli(1678790708000L),
            Instant.ofEpochMilli(1678790708000L));
    final var expectedJsonString =
        """
        {
          "id": { "nodeId": 1, "partitionId": 2, "checkpointId": 43 },
          "descriptor": { "checkpointPosition": 2345234, "numberOfPartitions": 3, "brokerVersion": "1.2.0-SNAPSHOT"},
          "statusCode": "IN_PROGRESS",
          "createdAt": "2023-03-14T10:45:08Z",
          "modifiedAt": "2023-03-14T10:45:08Z"
        }
        """;

    // when
    final String actualJsonString = MAPPER.writeValueAsString(manifest);

    // then

    final JsonNode actualJson = MAPPER.readTree(actualJsonString);
    final JsonNode expectedJson = MAPPER.readTree(expectedJsonString);

    assertThat(actualJson).isEqualTo(expectedJson);
  }

  @Test
  public void shouldSerializeFailedManifest() throws JsonProcessingException {
    // given
    final var created =
        Manifest.createManifest(
            new BackupIdentifierImpl(1, 2, 43),
            new BackupDescriptorImpl(Optional.empty(), 2345234L, 3, "1.2.0-SNAPSHOT"));
    final var failed = created.fail("expected failure reason");
    final var expectedJsonString =
        """
        {
          "id": { "nodeId": 1, "partitionId": 2, "checkpointId": 43 },
          "descriptor": { "checkpointPosition": 2345234, "numberOfPartitions": 3, "brokerVersion": "1.2.0-SNAPSHOT"},
          "statusCode": "FAILED",
          "createdAt": "2023-03-14T10:45:08Z",
          "modifiedAt": "2023-03-14T10:45:08Z",
          "failureReason": "expected failure reason"
        }
        """;

    // when
    final String actualJsonString = MAPPER.writeValueAsString(failed);

    // then
    final JsonNode actualJson = MAPPER.readTree(actualJsonString);
    final JsonNode expectedJson = MAPPER.readTree(expectedJsonString);

    // we exclude createdAt and modifiedAt from the assertion, due to using Instant (time)
    // which is not deterministic in tests
    assertThat(actualJson.get("statusCode")).isEqualTo(expectedJson.get("statusCode"));
    assertThat(actualJson.get("id")).isEqualTo(expectedJson.get("id"));
    assertThat(actualJson.get("descriptor")).isEqualTo(expectedJson.get("descriptor"));
    assertThat(actualJson.get("failureReason")).isEqualTo(expectedJson.get("failureReason"));

    assertThat(actualJson.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder(
            "id", "descriptor", "statusCode", "createdAt", "modifiedAt", "failureReason");
  }

  @Test
  public void shouldFailToDeserializeFailedManifestWithWrongStatusCode() {
    // given
    final var json =
        """
        {
          "id": { "nodeId": 1, "partitionId": 2, "checkpointId": 43 },
          "descriptor": { "checkpointPosition": 2345234, "numberOfPartitions": 3, "brokerVersion": "1.2.0-SNAPSHOT"},
          "statusCode": "IN_PROGRESS",
          "createdAt": "2023-03-14T10:45:08+00:00",
          "modifiedAt": "2023-03-14T10:45:08+00:00",
          "failureReason": "expected failure"
        }
        """;

    // when expect thrown
    assertThatThrownBy(() -> MAPPER.readValue(json, ManifestImpl.class))
        .hasRootCauseInstanceOf(InvalidPersistedManifestState.class)
        .hasMessageContaining(
            "Manifest in state 'IN_PROGRESS' must be 'FAILED to have have failureReason 'expected failure'");
  }

  @Test
  public void shouldDeserializeFailedManifest() throws JsonProcessingException {
    // given
    final var json =
        """
        {
          "id": { "nodeId": 1, "partitionId": 2, "checkpointId": 43 },
          "descriptor": { "checkpointPosition": 2345234, "numberOfPartitions": 3, "brokerVersion": "1.2.0-SNAPSHOT"},
          "statusCode": "FAILED",
          "createdAt": "2023-03-14T10:45:08+00:00",
          "modifiedAt": "2023-03-14T10:45:08+00:00",
          "failureReason": "expected failure"
        }
        """;

    // when
    final var manifest = MAPPER.readValue(json, Manifest.class).asFailed();

    // then
    final BackupIdentifierImpl id = manifest.id();
    assertThat(id).isNotNull();
    assertThat(id.nodeId()).isEqualTo(1);
    assertThat(manifest.id().partitionId()).isEqualTo(2);
    assertThat(manifest.id().checkpointId()).isEqualTo(43);

    final BackupDescriptorImpl descriptor = manifest.descriptor();
    assertThat(descriptor.brokerVersion()).isEqualTo("1.2.0-SNAPSHOT");
    assertThat(descriptor.checkpointPosition()).isEqualTo(2345234L);
    assertThat(descriptor.numberOfPartitions()).isEqualTo(3);
    assertThat(descriptor.snapshotId()).isNotPresent();

    assertThat(manifest.statusCode()).isEqualTo(FAILED);
    assertThat(manifest.createdAt()).isEqualTo(Instant.ofEpochMilli(1678790708000L));
    assertThat(manifest.modifiedAt()).isEqualTo(Instant.ofEpochMilli(1678790708000L));
    assertThat(manifest.failureReason()).isEqualTo("expected failure");
  }

  @Test
  public void shouldDeserializeInProgressManifest() throws JsonProcessingException {
    // given
    final var json =
        """
        {
          "id": { "nodeId": 1, "partitionId": 2, "checkpointId": 43 },
          "descriptor": { "checkpointPosition": 2345234, "numberOfPartitions": 3, "brokerVersion": "1.2.0-SNAPSHOT"},
          "statusCode": "IN_PROGRESS",
          "createdAt": "2023-03-14T10:45:08+00:00",
          "modifiedAt": "2023-03-14T10:45:08+00:00"
        }
        """;

    // when
    final var manifest = MAPPER.readValue(json, Manifest.class);

    // then
    final BackupIdentifierImpl id = manifest.id();
    assertThat(id).isNotNull();
    assertThat(id.nodeId()).isEqualTo(1);
    assertThat(manifest.id().partitionId()).isEqualTo(2);
    assertThat(manifest.id().checkpointId()).isEqualTo(43);

    final BackupDescriptorImpl descriptor = manifest.descriptor();
    assertThat(descriptor.brokerVersion()).isEqualTo("1.2.0-SNAPSHOT");
    assertThat(descriptor.checkpointPosition()).isEqualTo(2345234L);
    assertThat(descriptor.numberOfPartitions()).isEqualTo(3);
    assertThat(descriptor.snapshotId()).isNotPresent();

    assertThat(manifest.statusCode()).isEqualTo(IN_PROGRESS);
    assertThat(manifest.createdAt()).isEqualTo(Instant.ofEpochMilli(1678790708000L));
    assertThat(manifest.modifiedAt()).isEqualTo(Instant.ofEpochMilli(1678790708000L));
  }

  @Test
  public void shouldDeserializeInProgressAndComplete() throws JsonProcessingException {
    // given
    final var json =
        """
        {
          "id": { "nodeId": 1, "partitionId": 2, "checkpointId": 43 },
          "descriptor": { "checkpointPosition": 2345234, "numberOfPartitions": 3, "brokerVersion": "1.2.0-SNAPSHOT"},
          "statusCode": "IN_PROGRESS",
          "createdAt": "2023-03-14T10:45:08+00:00",
          "modifiedAt": "2023-03-14T10:45:08+00:00"
        }
        """;
    final var manifest = MAPPER.readValue(json, ManifestImpl.class);

    // when
    final var complete = manifest.asInProgress().complete();

    // then
    final BackupIdentifierImpl id = complete.id();
    assertThat(id).isNotNull();
    assertThat(id.nodeId()).isEqualTo(1);
    assertThat(complete.id().partitionId()).isEqualTo(2);
    assertThat(complete.id().checkpointId()).isEqualTo(43);

    final BackupDescriptorImpl descriptor = complete.descriptor();
    assertThat(descriptor.brokerVersion()).isEqualTo("1.2.0-SNAPSHOT");
    assertThat(descriptor.checkpointPosition()).isEqualTo(2345234L);
    assertThat(descriptor.numberOfPartitions()).isEqualTo(3);
    assertThat(descriptor.snapshotId()).isNotPresent();

    assertThat(complete.statusCode()).isEqualTo(COMPLETED);
    assertThat(complete.createdAt()).isEqualTo(Instant.ofEpochMilli(1678790708000L));
    assertThat(complete.modifiedAt()).isAfter(complete.createdAt());
  }

  @Test
  public void shouldDeserializeCompletedManifest() throws JsonProcessingException {
    // given
    final var json =
        """
        {
          "id": { "nodeId": 1, "partitionId": 2, "checkpointId": 43 },
          "descriptor": { "checkpointPosition": 2345234, "numberOfPartitions": 3, "brokerVersion": "1.2.0-SNAPSHOT"},
          "statusCode": "COMPLETED",
          "createdAt": "2023-03-14T10:45:08+00:00",
          "modifiedAt": "2023-03-14T10:45:08+00:00"
        }
        """;

    // when
    final var manifest = MAPPER.readValue(json, Manifest.class);

    // then
    final BackupIdentifierImpl id = manifest.id();
    assertThat(id).isNotNull();
    assertThat(id.nodeId()).isEqualTo(1);
    assertThat(manifest.id().partitionId()).isEqualTo(2);
    assertThat(manifest.id().checkpointId()).isEqualTo(43);

    final BackupDescriptorImpl descriptor = manifest.descriptor();
    assertThat(descriptor.brokerVersion()).isEqualTo("1.2.0-SNAPSHOT");
    assertThat(descriptor.checkpointPosition()).isEqualTo(2345234L);
    assertThat(descriptor.numberOfPartitions()).isEqualTo(3);
    assertThat(descriptor.snapshotId()).isNotPresent();

    assertThat(manifest.statusCode()).isEqualTo(COMPLETED);
    assertThat(manifest.createdAt()).isEqualTo(Instant.ofEpochMilli(1678790708000L));
    assertThat(manifest.modifiedAt()).isEqualTo(Instant.ofEpochMilli(1678790708000L));
  }

  @Test
  public void shouldFailOnAsInProgress() {
    // given
    final var manifest =
        Manifest.createManifest(
            new BackupIdentifierImpl(1, 2, 43),
            new BackupDescriptorImpl(Optional.empty(), 2345234L, 3, "1.2.0-SNAPSHOT"));

    final var complete = manifest.complete();

    // when expect thrown
    assertThatThrownBy(complete::asInProgress)
        .isInstanceOf(UnexpectedManifestState.class)
        .hasMessageContaining("but was in 'COMPLETED'");
  }

  @Test
  public void shouldFailOnAsCompleted() {
    // given
    final var manifest =
        Manifest.createManifest(
            new BackupIdentifierImpl(1, 2, 43),
            new BackupDescriptorImpl(Optional.empty(), 2345234L, 3, "1.2.0-SNAPSHOT"));

    // when expect thrown
    assertThatThrownBy(manifest::asCompleted)
        .isInstanceOf(UnexpectedManifestState.class)
        .hasMessageContaining("but was in 'IN_PROGRESS'");
  }

  @Test
  public void shouldFailOnAsFailed() {
    // given
    final var manifest =
        Manifest.createManifest(
            new BackupIdentifierImpl(1, 2, 43),
            new BackupDescriptorImpl(Optional.empty(), 2345234L, 3, "1.2.0-SNAPSHOT"));

    // when expect thrown
    assertThatThrownBy(manifest::asFailed)
        .isInstanceOf(UnexpectedManifestState.class)
        .hasMessageContaining("but was in 'IN_PROGRESS'");
  }

  @Test
  public void shouldCreateManifestWithInProgress() {
    // given

    // when
    final var manifest =
        Manifest.createManifest(
            new BackupIdentifierImpl(1, 2, 43),
            new BackupDescriptorImpl(Optional.empty(), 2345234L, 3, "1.2.0-SNAPSHOT"));

    // then
    assertThat(manifest.statusCode()).isEqualTo(IN_PROGRESS);
    assertThat(manifest.createdAt().getEpochSecond()).isGreaterThan(0);
    assertThat(manifest.modifiedAt().getEpochSecond()).isGreaterThan(0);
    assertThat(manifest.createdAt()).isEqualTo(manifest.modifiedAt());
  }

  @Test
  public void shouldUpdateManifestToCompleted() {
    // given
    final var created =
        Manifest.createManifest(
            new BackupIdentifierImpl(1, 2, 43),
            new BackupDescriptorImpl(Optional.empty(), 2345234L, 3, "1.2.0-SNAPSHOT"));

    // when
    final var completed = created.complete();

    // then
    assertThat(completed.statusCode()).isEqualTo(COMPLETED);
    assertThat(completed.createdAt().getEpochSecond()).isGreaterThan(0);
    assertThat(completed.modifiedAt().getEpochSecond()).isGreaterThan(0);
    assertThat(completed.createdAt()).isBefore(completed.modifiedAt());
    assertThat(completed.createdAt()).isEqualTo(created.modifiedAt());
    assertThat(completed.modifiedAt()).isNotEqualTo(created.modifiedAt());
  }

  @Test
  public void shouldUpdateManifestToFailed() {
    // given
    final var created =
        Manifest.createManifest(
            new BackupIdentifierImpl(1, 2, 43),
            new BackupDescriptorImpl(Optional.empty(), 2345234L, 3, "1.2.0-SNAPSHOT"));

    // when
    final var failed = created.fail("expected failure reason");

    // then
    assertThat(failed.statusCode()).isEqualTo(FAILED);
    assertThat(failed.createdAt().getEpochSecond()).isGreaterThan(0);
    assertThat(failed.modifiedAt().getEpochSecond()).isGreaterThan(0);
    assertThat(failed.createdAt()).isBefore(failed.modifiedAt());
    assertThat(failed.createdAt()).isEqualTo(created.modifiedAt());
    assertThat(failed.modifiedAt()).isNotEqualTo(created.modifiedAt());
    assertThat(failed.failureReason()).isEqualTo("expected failure reason");
  }

  @Test
  public void shouldUpdateManifestToFailedFromComplete() {
    // given
    final var created =
        Manifest.createManifest(
            new BackupIdentifierImpl(1, 2, 43),
            new BackupDescriptorImpl(Optional.empty(), 2345234L, 3, "1.2.0-SNAPSHOT"));

    final var completed = created.complete();

    // when
    final var failed = completed.fail("expected failure reason");

    // then
    assertThat(failed.statusCode()).isEqualTo(FAILED);
    assertThat(failed.createdAt().getEpochSecond()).isGreaterThan(0);
    assertThat(failed.modifiedAt().getEpochSecond()).isGreaterThan(0);
    assertThat(failed.createdAt()).isBefore(failed.modifiedAt());
    assertThat(failed.createdAt()).isEqualTo(created.modifiedAt());
    assertThat(failed.modifiedAt()).isNotEqualTo(created.modifiedAt());
    assertThat(failed.failureReason()).isEqualTo("expected failure reason");
  }
}
