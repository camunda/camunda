/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.engine.util.AuthorizationUtil;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;

public class ProcessDefinitionSuspensionClient {

  private final CommandWriter writer;
  private final ProcessDefinitionSuspensionRecord record = new ProcessDefinitionSuspensionRecord();
  private boolean rejectionExpected;
  private List<String> authorizedTenantIds = List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public ProcessDefinitionSuspensionClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public ProcessDefinitionSuspensionClient withProcessDefinitionKey(final long key) {
    record.setProcessDefinitionKey(key);
    return this;
  }

  public ProcessDefinitionSuspensionClient withBpmnProcessId(final String bpmnProcessId) {
    record.setBpmnProcessId(bpmnProcessId);
    return this;
  }

  public ProcessDefinitionSuspensionClient withVersion(final int version) {
    record.setVersion(version);
    return this;
  }

  public ProcessDefinitionSuspensionClient withTenantId(final String tenantId) {
    record.setTenantId(tenantId);
    return this;
  }

  public ProcessDefinitionSuspensionClient withAuthorizedTenantIds(final String... tenantIds) {
    authorizedTenantIds = List.of(tenantIds);
    return this;
  }

  public ProcessDefinitionSuspensionClient expectRejection() {
    rejectionExpected = true;
    return this;
  }

  public Record<ProcessDefinitionSuspensionRecordValue> suspend() {
    return write(ProcessDefinitionSuspensionIntent.SUSPEND, defaultAuthInfo());
  }

  public Record<ProcessDefinitionSuspensionRecordValue> suspend(final String username) {
    return write(ProcessDefinitionSuspensionIntent.SUSPEND, usernameAuthInfo(username));
  }

  public Record<ProcessDefinitionSuspensionRecordValue> resume() {
    return write(ProcessDefinitionSuspensionIntent.RESUME, defaultAuthInfo());
  }

  public Record<ProcessDefinitionSuspensionRecordValue> resume(final String username) {
    return write(ProcessDefinitionSuspensionIntent.RESUME, usernameAuthInfo(username));
  }

  private AuthInfo defaultAuthInfo() {
    return AuthorizationUtil.getAuthInfo(authorizedTenantIds.toArray(new String[0]));
  }

  private AuthInfo usernameAuthInfo(final String username) {
    return AuthorizationUtil.getUsernameAuthInfo(
        username, authorizedTenantIds.toArray(new String[0]));
  }

  private Record<ProcessDefinitionSuspensionRecordValue> write(
      final ProcessDefinitionSuspensionIntent intent, final AuthInfo authInfo) {
    final long position = writer.writeCommand(intent, record, authInfo);
    if (rejectionExpected) {
      return RecordingExporter.processDefinitionSuspensionRecords(intent)
          .onlyCommandRejections()
          .withSourceRecordPosition(position)
          .getFirst();
    }
    final var event =
        intent == ProcessDefinitionSuspensionIntent.SUSPEND
            ? ProcessDefinitionSuspensionIntent.SUSPENDED
            : ProcessDefinitionSuspensionIntent.RESUMED;
    return RecordingExporter.processDefinitionSuspensionRecords(event)
        .withSourceRecordPosition(position)
        .getFirst();
  }
}
