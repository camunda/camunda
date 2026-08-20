/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableResource;
import io.camunda.zeebe.protocol.record.value.deployment.Resource;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class ResourceAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ResourceAuditLogTransformer transformer = new ResourceAuditLogTransformer();

  @Test
  void shouldTransformResourceRecord() {
    // given
    final Resource recordValue =
        ImmutableResource.builder()
            .from(factory.generateObject(Resource.class))
            .withResourceName("resourceName")
            .withDeploymentKey(123L)
            .withResourceKey(456L)
            .withTenantId("tenant-1")
            .build();

    final Record<Resource> record =
        factory.generateRecord(
            ValueType.RESOURCE, r -> r.withIntent(ResourceIntent.CREATED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getEntityKey()).isEqualTo("456");
    assertThat(entity.getDeploymentKey()).isEqualTo(123L);
    assertThat(entity.getResourceKey()).isEqualTo(456L);
    assertThat(entity.getEntityDescription()).isEqualTo("resourceName");
  }
}
