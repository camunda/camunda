/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.recover;

import io.camunda.zeebe.engine.state.deployment.PersistedProcess;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.agrona.concurrent.UnsafeBuffer;

/** Shared helpers for the {@code recover} unit tests. */
final class RecoverTestSupport {

  private RecoverTestSupport() {}

  static byte[] readResource(final String name) throws IOException {
    try (final var in = RecoverTestSupport.class.getClassLoader().getResourceAsStream(name)) {
      if (in == null) {
        throw new IllegalStateException("Missing test resource: " + name);
      }
      return in.readAllBytes();
    }
  }

  static String asString(final byte[] resource) {
    return new String(resource, StandardCharsets.UTF_8);
  }

  static PersistedProcess persistedProcess(
      final long key,
      final String bpmnProcessId,
      final int version,
      final String versionTag,
      final String tenantId,
      final String resourceName,
      final byte[] resource,
      final PersistedProcessState state) {
    final var record =
        new ProcessRecord()
            .setKey(key)
            .setBpmnProcessId(bpmnProcessId)
            .setVersion(version)
            .setVersionTag(versionTag)
            .setTenantId(tenantId)
            .setResourceName(resourceName)
            .setResource(new UnsafeBuffer(resource));
    final var persisted = new PersistedProcess();
    persisted.wrap(record, key);
    persisted.setState(state);
    return persisted;
  }
}
