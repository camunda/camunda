/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.cmd;

import java.util.List;

public class InvalidTenantRequestException extends ClientException {

  private static final String MESSAGE_FORMAT =
      "Expected to handle gRPC request %s with tenant identifier '%s', but %s";
  private static final String MESSAGE_FORMAT_TENANTS =
      "Expected to handle gRPC request %s with tenant identifiers '%s', but %s";

  private final String commandName;
  private final String tenantId;
  private final List<String> tenantIds;
  private final String reason;

  public InvalidTenantRequestException(
      final String commandName, final String tenantId, final String reason) {
    super(String.format(MESSAGE_FORMAT, commandName, tenantId, reason));

    this.commandName = commandName;
    this.tenantId = tenantId;
    tenantIds = List.of();
    this.reason = reason;
  }

  public InvalidTenantRequestException(
      final String commandName, final String tenantId, final String reason, final Exception e) {
    super(String.format(MESSAGE_FORMAT, commandName, tenantId, reason), e);

    this.commandName = commandName;
    this.tenantId = tenantId;
    tenantIds = List.of();
    this.reason = reason;
  }

  public InvalidTenantRequestException(
      final String commandName, final List<String> tenantIds, final String reason) {
    super(String.format(MESSAGE_FORMAT_TENANTS, commandName, tenantIds.toString(), reason));

    this.commandName = commandName;
    tenantId = "";
    this.tenantIds = tenantIds;
    this.reason = reason;
  }

  public InvalidTenantRequestException(
      final String commandName,
      final List<String> tenantIds,
      final String reason,
      final Exception e) {
    super(String.format(MESSAGE_FORMAT_TENANTS, commandName, tenantIds.toString(), reason), e);

    this.commandName = commandName;
    tenantId = "";
    this.tenantIds = tenantIds;
    this.reason = reason;
  }

  public String getCommandName() {
    return commandName;
  }

  public String getReason() {
    return reason;
  }

  public String getTenantId() {
    return tenantId;
  }
}
