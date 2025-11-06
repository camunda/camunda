/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.io.IOException;
import java.util.Optional;

public record BackupDescriptorImpl(
    Optional<String> snapshotId,
    long checkpointPosition,
    int numberOfPartitions,
    String brokerVersion,
    long checkpointTimestamp,
    @JsonDeserialize(using = DescriptorCheckpointTypeDeserializer.class)
        CheckpointType checkpointType)
    implements BackupDescriptor {

  public static BackupDescriptorImpl from(final BackupDescriptor descriptor) {
    return new BackupDescriptorImpl(
        descriptor.snapshotId(),
        descriptor.checkpointPosition(),
        descriptor.numberOfPartitions(),
        descriptor.brokerVersion(),
        descriptor.checkpointTimestamp(),
        descriptor.checkpointType());
  }

  // CheckpointType backwards compatibility deserializer to handle missing fields
  static class DescriptorCheckpointTypeDeserializer extends JsonDeserializer<CheckpointType> {

    @Override
    public CheckpointType deserialize(final JsonParser p, final DeserializationContext ctxt)
        throws IOException {
      final String value = p.getText();
      return value == null || value.isEmpty()
          ? CheckpointType.MANUAL_BACKUP
          : CheckpointType.valueOf(value);
    }

    @Override
    public CheckpointType getNullValue(final DeserializationContext ctxt) {
      return CheckpointType.MANUAL_BACKUP;
    }
  }
}
