/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Manifest(
    BackupIdentifierImpl id,
    BackupDescriptorImpl descriptor,
    Set<String> snapshotFileNames,
    Set<String> segmentFileNames,
    BackupStatusCode statusCode,
    Optional<String> failureReason,
    Instant created,
    Instant lastModified) {

  static Manifest fromNewBackup(final Backup backup) {
    final var now = Instant.now();
    return new Manifest(
        BackupIdentifierImpl.from(backup.id()),
        BackupDescriptorImpl.from(backup.descriptor()),
        backup.snapshot().names(),
        backup.segments().names(),
        BackupStatusCode.IN_PROGRESS,
        Optional.empty(),
        now,
        now);
  }

  Manifest withStatus(final BackupStatusCode statusCode) {
    return new Manifest(
        id,
        descriptor,
        snapshotFileNames,
        segmentFileNames,
        statusCode,
        failureReason,
        created,
        Instant.now());
  }

  Manifest withFailedStatus(final Throwable throwable) {
    final var writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));
    return withFailedStatus(writer.toString());
  }

  Manifest withFailedStatus(final String failureReason) {
    return new Manifest(
        id,
        descriptor,
        snapshotFileNames,
        segmentFileNames,
        BackupStatusCode.FAILED,
        Optional.of(failureReason),
        created,
        Instant.now());
  }

  BackupStatus toStatus() {
    return new BackupStatusImpl(
        id,
        Optional.of(descriptor),
        statusCode,
        failureReason,
        Optional.of(created),
        Optional.of(lastModified));
  }
}
