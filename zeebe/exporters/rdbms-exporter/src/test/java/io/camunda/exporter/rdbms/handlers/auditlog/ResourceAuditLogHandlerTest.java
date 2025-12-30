/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableResource;
import io.camunda.zeebe.protocol.record.value.deployment.Resource;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class ResourceAuditLogHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ResourceAuditLogTransformer transformer = new ResourceAuditLogTransformer();

  @Test
  void shouldTransformResourceRecord() {
    // given
    final Resource recordValue =
        ImmutableResource.builder()
            .from(factory.generateObject(Resource.class))
            .withDeploymentKey(123L)
            .withResourceKey(456L)
            .withTenantId("tenant-1")
            .build();

    final Record<Resource> record =
        factory.generateRecord(
            ValueType.RESOURCE, r -> r.withIntent(ResourceIntent.CREATED).withValue(recordValue));

    // when
    final AuditLogDbModel.Builder builder = new AuditLogDbModel.Builder();
    transformer.transform(record, builder);
    final var entity = builder.build();

    // then
    assertThat(entity.deploymentKey()).isEqualTo(123L);
    assertThat(entity.resourceKey()).isEqualTo(456L);
  }
}
