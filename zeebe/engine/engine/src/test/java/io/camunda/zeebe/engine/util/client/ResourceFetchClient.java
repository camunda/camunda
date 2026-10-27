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
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.Resource;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.function.Function;

public class ResourceFetchClient {

  private static final Function<Long, Record<Resource>> SUCCESS_EXPECTATION =
      (position) ->
          RecordingExporter.resourceRecords()
              .withIntent(ResourceIntent.FETCHED)
              .withSourceRecordPosition(position)
              .getFirst();
  private static final Function<Long, Record<Resource>> REJECTION_EXPECTATION =
      (position) ->
          RecordingExporter.resourceRecords()
              .onlyCommandRejections()
              .withIntent(ResourceIntent.FETCH)
              .withSourceRecordPosition(position)
              .getFirst();

  private final CommandWriter writer;
  private final ResourceRecord resourceRecord = new ResourceRecord();
  private Function<Long, Record<Resource>> expectation = SUCCESS_EXPECTATION;
  private List<String> authorizedTenantIds = List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private int requestStreamId = 1;
  private long requestId = 1L;

  public ResourceFetchClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public ResourceFetchClient withResourceKey(final long resourceKey) {
    resourceRecord.setResourceKey(resourceKey);
    return this;
  }

  public ResourceFetchClient withAuthorizedTenantIds(final String... tenantIds) {
    authorizedTenantIds = List.of(tenantIds);
    return this;
  }

  public ResourceFetchClient withRequestStreamId(final int requestStreamId) {
    this.requestStreamId = requestStreamId;
    return this;
  }

  public ResourceFetchClient withRequestId(final long requestId) {
    this.requestId = requestId;
    return this;
  }

  public Record<Resource> fetch() {
    final long position =
        writer.writeCommand(
            resourceRecord.getResourceKey(),
            requestStreamId,
            requestId,
            ResourceIntent.FETCH,
            resourceRecord,
            authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }

  public Record<Resource> fetch(final AuthInfo authorizations) {
    final long position = writer.writeCommand(ResourceIntent.FETCH, resourceRecord, authorizations);
    return expectation.apply(position);
  }

  public Record<Resource> fetch(final String username) {
    return fetch(
        AuthorizationUtil.getUsernameAuthInfo(username, TenantOwned.DEFAULT_TENANT_IDENTIFIER));
  }

  public ResourceFetchClient expectRejection() {
    expectation = REJECTION_EXPECTATION;
    return this;
  }
}
