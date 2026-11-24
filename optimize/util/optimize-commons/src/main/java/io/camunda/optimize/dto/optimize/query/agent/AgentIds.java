/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Identifiers of a logical agent. The engine mints a new agent definition key for every process
 * version, so one agent across versions is identified by its BPMN process ID and element ID, and
 * scoped per tenant (zeebe ADR 0011).
 *
 * <p>Optimize uses C7 naming: {@code processDefinitionKey} is the BPMN process ID. See {@code
 * optimize/docs/adr/001-c7-naming-conventions.md}.
 */
public final class AgentIds {

  private AgentIds() {}

  public static String agentId(final String processDefinitionKey, final String flowNodeId) {
    return processDefinitionKey + ":" + flowNodeId;
  }

  /**
   * The document ID is a SHA-256 hash, because Zeebe accepts IDs of up to 32 KB by default and the
   * database rejects a document ID longer than 512 bytes. Such a document would fail on every retry
   * and stop the import of its partition. The parts are joined with {@code :}, which BPMN IDs and
   * tenant IDs cannot contain, so different agents never get the same input.
   */
  public static String agentDefinitionDocumentId(
      final String tenantId, final String processDefinitionKey, final String flowNodeId) {
    return sha256Hex(tenantId + ":" + agentId(processDefinitionKey, flowNodeId));
  }

  private static String sha256Hex(final String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (final NoSuchAlgorithmException e) {
      // Every Java platform must support SHA-256.
      throw new IllegalStateException(e);
    }
  }
}
