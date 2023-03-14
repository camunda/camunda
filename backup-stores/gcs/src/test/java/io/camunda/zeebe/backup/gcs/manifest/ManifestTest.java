/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs.manifest;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static io.camunda.zeebe.backup.gcs.manifest.ManifestTest.BackupStatusCode.COMPLETED;
import static io.camunda.zeebe.backup.gcs.manifest.ManifestTest.BackupStatusCode.FAILED;
import static io.camunda.zeebe.backup.gcs.manifest.ManifestTest.BackupStatusCode.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
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
    final Manifest manifest = MAPPER.readValue(json, Manifest.class);

    // then
    final BackupIdentifierImpl id = manifest.id;
    assertThat(id).isNotNull();
    assertThat(id.nodeId()).isEqualTo(1);
    assertThat(manifest.id.partitionId()).isEqualTo(2);
    assertThat(manifest.id.checkpointId()).isEqualTo(43);

    final BackupDescriptorImpl descriptor = manifest.descriptor;
    assertThat(descriptor.brokerVersion()).isEqualTo("1.2.0-SNAPSHOT");
    assertThat(descriptor.checkpointPosition()).isEqualTo(2345234L);
    assertThat(descriptor.numberOfPartitions()).isEqualTo(3);
    assertThat(descriptor.snapshotId()).isNotPresent();

    assertThat(manifest.statusCode).isEqualTo(IN_PROGRESS);
    assertThat(manifest.createdAt).isEqualTo(Instant.ofEpochMilli(1678790708000L));
    assertThat(manifest.modifiedAt).isEqualTo(Instant.ofEpochMilli(1678790708000L));
  }

  @Test
  public void shouldSerialize() throws JsonProcessingException {
    // given
    final var manifest =
        new Manifest(
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
  public void shouldCreateManifestWithInProgress() throws JsonProcessingException {
    // given

    // when
    final Manifest manifest =
        Manifest.createManifest(
            new BackupIdentifierImpl(1, 2, 43),
            new BackupDescriptorImpl(Optional.empty(), 2345234L, 3, "1.2.0-SNAPSHOT"));

    // then
    assertThat(manifest.statusCode).isEqualTo(IN_PROGRESS);
    assertThat(manifest.createdAt.getEpochSecond()).isGreaterThan(0);
    assertThat(manifest.modifiedAt.getEpochSecond()).isGreaterThan(0);
    assertThat(manifest.createdAt).isEqualTo(manifest.modifiedAt);
  }

  @Test
  public void shouldUpdateManifestToCompleted() {
    // given
    final Manifest created =
        Manifest.createManifest(
            new BackupIdentifierImpl(1, 2, 43),
            new BackupDescriptorImpl(Optional.empty(), 2345234L, 3, "1.2.0-SNAPSHOT"));

    // when
    final var completed = created.complete();

    // then
    assertThat(completed.statusCode).isEqualTo(COMPLETED);
    assertThat(completed.createdAt.getEpochSecond()).isGreaterThan(0);
    assertThat(completed.modifiedAt.getEpochSecond()).isGreaterThan(0);
    assertThat(completed.createdAt).isBefore(completed.modifiedAt);
    assertThat(completed.createdAt).isEqualTo(created.modifiedAt);
    assertThat(completed.modifiedAt).isNotEqualTo(created.modifiedAt);
  }

  @Test
  public void shouldUpdateManifestToFailed() {
    // given
    final Manifest created =
        Manifest.createManifest(
            new BackupIdentifierImpl(1, 2, 43),
            new BackupDescriptorImpl(Optional.empty(), 2345234L, 3, "1.2.0-SNAPSHOT"));

    // when
    final var failed = created.fail();

    // then
    assertThat(failed.statusCode).isEqualTo(FAILED);
    assertThat(failed.createdAt.getEpochSecond()).isGreaterThan(0);
    assertThat(failed.modifiedAt.getEpochSecond()).isGreaterThan(0);
    assertThat(failed.createdAt).isBefore(failed.modifiedAt);
    assertThat(failed.createdAt).isEqualTo(created.modifiedAt);
    assertThat(failed.modifiedAt).isNotEqualTo(created.modifiedAt);
  }

  record Manifest(
      BackupIdentifierImpl id,
      BackupDescriptorImpl descriptor,
      BackupStatusCode statusCode,
      Instant createdAt,
      Instant modifiedAt) {

    public static Manifest createManifest(
        final BackupIdentifierImpl id, final BackupDescriptorImpl descriptor) {
      final Instant creationTime = Instant.now();
      return new Manifest(id, descriptor, IN_PROGRESS, creationTime, creationTime);
    }

    public Manifest complete() {
      return new Manifest(id, descriptor, COMPLETED, createdAt, Instant.now());
    }

    public Manifest fail() {
      return new Manifest(id, descriptor, FAILED, createdAt, Instant.now());
    }
  }

  enum BackupStatusCode {
    IN_PROGRESS,
    COMPLETED,

    FAILED
  }
}
