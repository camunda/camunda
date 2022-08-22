/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.camunda.zeebe.backup.api.BackupStatus;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Holds the current {@link BackupStatus backup status} and an optional description in case of
 * failed backups.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Status(BackupStatus status, @JsonInclude(Include.NON_EMPTY) String description) {

  public static final String OBJECT_KEY = "status.json";

  public Status(BackupStatus status) {
    this(status, "");
  }

  public static Status inProgress() {
    return new Status(BackupStatus.IN_PROGRESS);
  }

  public static Status complete() {
    return new Status(BackupStatus.COMPLETED);
  }

  public static Status failed(Throwable throwable) {
    final var writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));
    return new Status(BackupStatus.FAILED, writer.toString());
  }
}
