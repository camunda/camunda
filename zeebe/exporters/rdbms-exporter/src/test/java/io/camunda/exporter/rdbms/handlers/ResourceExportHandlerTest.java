/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.db.rdbms.write.domain.DeployedResourceDbModel;
import io.camunda.db.rdbms.write.service.DeployedResourceWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableResource;
import io.camunda.zeebe.protocol.record.value.deployment.Resource;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourceExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private DeployedResourceWriter writer;

  @Captor private ArgumentCaptor<DeployedResourceDbModel> dbModelCaptor;

  private ResourceExportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new ResourceExportHandler(writer);
  }

  @ParameterizedTest
  @EnumSource(
      value = ResourceIntent.class,
      names = {"CREATED", "DELETED"},
      mode = Mode.INCLUDE)
  void shouldExportSupportedIntents(final ResourceIntent intent) {
    // given
    final Resource value =
        ImmutableResource.builder()
            .from(factory.generateObject(Resource.class))
            .withResourceName("my-script.rpa")
            .build();
    final Record<Resource> record =
        factory.generateRecord(ValueType.RESOURCE, r -> r.withIntent(intent).withValue(value));

    // when - then
    assertThat(handler.canExport(record)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ResourceIntent.class,
      names = {"CREATED", "DELETED"},
      mode = Mode.EXCLUDE)
  void shouldNotExportUnsupportedIntents(final ResourceIntent intent) {
    // given
    final Resource value =
        ImmutableResource.builder()
            .from(factory.generateObject(Resource.class))
            .withResourceName("my-script.rpa")
            .build();
    final Record<Resource> record =
        factory.generateRecord(ValueType.RESOURCE, r -> r.withIntent(intent).withValue(value));

    // when - then
    assertThat(handler.canExport(record)).isFalse();
  }

  @Test
  void shouldNotExportNonRpaResourceName() {
    // given
    final Resource value =
        ImmutableResource.builder()
            .from(factory.generateObject(Resource.class))
            .withResourceName("process.bpmn")
            .build();
    final Record<Resource> record =
        factory.generateRecord(
            ValueType.RESOURCE, r -> r.withIntent(ResourceIntent.CREATED).withValue(value));

    // when - then
    assertThat(handler.canExport(record)).isFalse();
  }

  @Test
  void shouldNotExportNullResourceName() {
    // given
    final Resource value =
        ImmutableResource.builder()
            .from(factory.generateObject(Resource.class))
            .withResourceName(null)
            .build();
    final Record<Resource> record =
        factory.generateRecord(
            ValueType.RESOURCE, r -> r.withIntent(ResourceIntent.CREATED).withValue(value));

    // when - then
    assertThat(handler.canExport(record)).isFalse();
  }

  @Test
  void shouldHandleCreatedRecord() {
    // given
    final Resource value =
        ImmutableResource.builder()
            .from(factory.generateObject(Resource.class))
            .withResourceKey(42L)
            .withResourceId("res-id")
            .withResourceName("my-script.rpa")
            .withVersion(3)
            .withVersionTag("v3")
            .withDeploymentKey(100L)
            .withTenantId("tenant-1")
            .withResourceProp("rpa-content")
            .build();
    final Record<Resource> record =
        factory.generateRecord(
            ValueType.RESOURCE, r -> r.withIntent(ResourceIntent.CREATED).withValue(value));

    // when
    handler.export(record);

    // then
    verify(writer).create(dbModelCaptor.capture());
    final DeployedResourceDbModel model = dbModelCaptor.getValue();
    assertThat(model.resourceKey()).isEqualTo(42L);
    assertThat(model.resourceId()).isEqualTo("res-id");
    assertThat(model.resourceName()).isEqualTo("my-script.rpa");
    assertThat(model.version()).isEqualTo(3);
    assertThat(model.versionTag()).isEqualTo("v3");
    assertThat(model.deploymentKey()).isEqualTo(100L);
    assertThat(model.tenantId()).isEqualTo("tenant-1");
    assertThat(model.resourceContent()).isEqualTo("rpa-content");

    verifyNoMoreInteractions(writer);
  }

  @Test
  void shouldHandleDeletedRecord() {
    // given
    final Resource value =
        ImmutableResource.builder()
            .from(factory.generateObject(Resource.class))
            .withResourceKey(99L)
            .withResourceName("my-script.rpa")
            .build();
    final Record<Resource> record =
        factory.generateRecord(
            ValueType.RESOURCE, r -> r.withIntent(ResourceIntent.DELETED).withValue(value));

    // when
    handler.export(record);

    // then
    verify(writer).delete(99L);
    verifyNoMoreInteractions(writer);
  }
}
