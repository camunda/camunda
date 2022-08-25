/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

/**
 * Holds the current {@link BackupStatusCode} and an optional failureReason in case of failed
 * backups.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Status(BackupStatusCode statusCode, Optional<String> failureReason) {

  public static final String OBJECT_KEY = "status.json";

  public Status(BackupStatusCode status) {
    this(status, Optional.empty());
  }

  public static Status inProgress() {
    return new Status(BackupStatusCode.IN_PROGRESS);
  }

  public static Status complete() {
    return new Status(BackupStatusCode.COMPLETED);
  }

  public static Status failed(Throwable throwable) {
    final var writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));
    return new Status(BackupStatusCode.FAILED, Optional.of(writer.toString()));
  }
}
