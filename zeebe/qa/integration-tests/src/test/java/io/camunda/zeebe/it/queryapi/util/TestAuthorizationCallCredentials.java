/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.queryapi.util;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import java.util.concurrent.Executor;

public class TestAuthorizationCallCredentials extends CallCredentials {
  private final String tenant;

  public TestAuthorizationCallCredentials(final String tenant) {
    this.tenant = tenant;
  }

  @Override
  public void applyRequestMetadata(
      final RequestInfo requestInfo, final Executor appExecutor, final MetadataApplier applier) {
    final var metadata = new Metadata();
    metadata.put(TestAuthorizationServerInterceptor.TENANT_KEY, tenant);
    applier.apply(metadata);
  }

  @Override
  public void thisUsesUnstableApi() {}
}
