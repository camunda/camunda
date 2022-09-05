/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.common;

import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import java.time.Instant;
import java.util.Optional;

public record BackupStatusImpl(
    BackupIdentifier id,
    Optional<BackupDescriptor> descriptor,
    BackupStatusCode statusCode,
    Optional<String> failureReason,
    Optional<Instant> created,
    Optional<Instant> lastModified)
    implements BackupStatus {}
