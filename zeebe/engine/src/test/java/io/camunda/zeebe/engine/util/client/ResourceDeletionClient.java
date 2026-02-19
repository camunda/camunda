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
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.value.ResourceDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.ResourceType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.function.Function;

public class ResourceDeletionClient {

  private static final Function<Long, Record<ResourceDeletionRecordValue>> SUCCESS_EXPECTATION =
      (position) ->
          RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETED)
              .withSourceRecordPosition(position)
              .getFirst();
  private static final Function<Long, Record<ResourceDeletionRecordValue>> REJECTION_EXPECTATION =
      (position) ->
          RecordingExporter.resourceDeletionRecords()
              .onlyCommandRejections()
              .withIntent(ResourceDeletionIntent.DELETE)
              .withSourceRecordPosition(position)
              .getFirst();

  private final CommandWriter writer;
  private final ResourceDeletionRecord resourceDeletionRecord = new ResourceDeletionRecord();
  private Function<Long, Record<ResourceDeletionRecordValue>> expectation = SUCCESS_EXPECTATION;
  private List<String> authorizedTenantIds = List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public ResourceDeletionClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public ResourceDeletionClient withResourceKey(final long resourceKey) {
    resourceDeletionRecord.setResourceKey(resourceKey);
    return this;
  }

  public ResourceDeletionClient withResourceType(final ResourceType resourceType) {
    resourceDeletionRecord.setResourceType(resourceType);
    return this;
  }

  public ResourceDeletionClient withResourceId(final String resourceId) {
    resourceDeletionRecord.setResourceId(resourceId);
    return this;
  }

  public ResourceDeletionClient withTenantId(final String tenantId) {
    resourceDeletionRecord.setTenantId(tenantId);
    return this;
  }

  public ResourceDeletionClient withDeleteHistory(final boolean deleteHistory) {
    resourceDeletionRecord.setDeleteHistory(deleteHistory);
    return this;
  }

  public ResourceDeletionClient withAuthorizedTenantIds(final String... tenantIds) {
    authorizedTenantIds = List.of(tenantIds);
    return this;
  }

  public Record<ResourceDeletionRecordValue> delete() {
    final long position =
        writer.writeCommand(
            ResourceDeletionIntent.DELETE,
            resourceDeletionRecord,
            authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }

  public Record<ResourceDeletionRecordValue> delete(final AuthInfo authorizations) {
    final long position =
        writer.writeCommand(ResourceDeletionIntent.DELETE, resourceDeletionRecord, authorizations);
    return expectation.apply(position);
  }

  public Record<ResourceDeletionRecordValue> delete(final String username) {
    return delete(
        AuthorizationUtil.getUsernameAuthInfo(
            username, authorizedTenantIds.toArray(new String[0])));
  }

  public ResourceDeletionClient expectRejection() {
    expectation = REJECTION_EXPECTATION;
    return this;
  }
}
