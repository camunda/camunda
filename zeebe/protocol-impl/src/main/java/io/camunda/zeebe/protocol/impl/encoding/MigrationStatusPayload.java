/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import java.nio.charset.StandardCharsets;

/**
 * Encodes/decodes a {@link PartitionMigrationStatus} into the {@code payload} byte blob of an
 * {@code AdminRequest}/{@code AdminResponse} ({@code GET_MIGRATION_STATUS}, {@code
 * GET_EXPORTING_MIGRATION_STATUS}).
 */
public final class MigrationStatusPayload {

  private static final String DELIMITER = "\n";

  private MigrationStatusPayload() {}

  public static byte[] encode(final PartitionMigrationStatus status) {
    return (status.code().name() + DELIMITER + status.detail()).getBytes(StandardCharsets.UTF_8);
  }

  public static PartitionMigrationStatus decode(final byte[] payload) {
    final var decoded = new String(payload, StandardCharsets.UTF_8);
    final var parts = decoded.split(DELIMITER, 2);
    final var code = MigrationStatusCode.valueOf(parts[0]);
    final var detail = parts.length > 1 ? parts[1] : "";
    return new PartitionMigrationStatus(code, detail);
  }
}
