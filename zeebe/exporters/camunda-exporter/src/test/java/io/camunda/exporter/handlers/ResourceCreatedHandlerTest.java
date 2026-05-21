/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.resource.DeployedResourceEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableResource;
import io.camunda.zeebe.protocol.record.value.deployment.Resource;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

public class ResourceCreatedHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-resource";
  private final ResourceCreatedHandler underTest = new ResourceCreatedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.RESOURCE);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(DeployedResourceEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Resource value =
        ImmutableResource.builder()
            .from(factory.generateObject(Resource.class))
            .withResourceName("my-script.rpa")
            .build();
    final Record<Resource> record =
        factory.generateRecord(
            ValueType.RESOURCE, r -> r.withIntent(ResourceIntent.CREATED).withValue(value));

    // when - then
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @Test
  void shouldHandleReexportedRecord() {
    // given
    final Resource value =
        ImmutableResource.builder()
            .from(factory.generateObject(Resource.class))
            .withResourceName("my-script.rpa")
            .build();
    final Record<Resource> record =
        factory.generateRecord(
            ValueType.RESOURCE, r -> r.withIntent(ResourceIntent.REEXPORTED).withValue(value));

    // when - then
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ResourceIntent.class,
      names = {"CREATED", "REEXPORTED"},
      mode = Mode.EXCLUDE)
  void shouldNotHandleRecordForUnsupportedIntent(final ResourceIntent intent) {
    // given
    final Resource value =
        ImmutableResource.builder()
            .from(factory.generateObject(Resource.class))
            .withResourceName("my-script.rpa")
            .build();
    final Record<Resource> record =
        factory.generateRecord(ValueType.RESOURCE, r -> r.withIntent(intent).withValue(value));

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Resource value =
        ImmutableResource.builder()
            .from(factory.generateObject(Resource.class))
            .withResourceKey(42L)
            .withResourceName("my-script.rpa")
            .build();
    final Record<Resource> record =
        factory.generateRecord(
            ValueType.RESOURCE, r -> r.withIntent(ResourceIntent.CREATED).withValue(value));

    // when
    final var idList = underTest.generateIds(record);

    // then
    assertThat(idList).containsExactly("42");
  }

  @Test
  void shouldAddEntityOnFlush() throws PersistenceException {
    // given
    final DeployedResourceEntity entity =
        new DeployedResourceEntity()
            .setId("42")
            .setResourceKey(42L)
            .setResourceId("resource-id")
            .setResourceName("my-script.rpa")
            .setResourceType("rpa")
            .setVersion(1)
            .setVersionTag("v1")
            .setDeploymentKey(100L)
            .setTenantId("<default>");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(entity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, entity);
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final Resource value =
        ImmutableResource.builder()
            .from(factory.generateObject(Resource.class))
            .withResourceKey(42L)
            .withResourceId("resource-id")
            .withResourceName("my-script.rpa")
            .withVersion(2)
            .withVersionTag("v2")
            .withDeploymentKey(100L)
            .withTenantId("tenant-1")
            .withResourceProp("rpa-content")
            .build();
    final Record<Resource> record =
        factory.generateRecord(
            ValueType.RESOURCE, r -> r.withIntent(ResourceIntent.CREATED).withValue(value));

    final DeployedResourceEntity entity = underTest.createNewEntity("42");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getId()).isEqualTo("42");
    assertThat(entity.getResourceKey()).isEqualTo(42L);
    assertThat(entity.getResourceId()).isEqualTo("resource-id");
    assertThat(entity.getResourceName()).isEqualTo("my-script.rpa");
    assertThat(entity.getResourceType()).isEqualTo("rpa");
    assertThat(entity.getVersion()).isEqualTo(2);
    assertThat(entity.getVersionTag()).isEqualTo("v2");
    assertThat(entity.getDeploymentKey()).isEqualTo(100L);
    assertThat(entity.getTenantId()).isEqualTo("tenant-1");
    assertThat(entity.getResourceContent()).isEqualTo("rpa-content");
  }

  @Test
  void shouldExtractFileTypeFromFilenameWithMultipleDots() {
    // given
    final Resource value =
        ImmutableResource.builder()
            .from(factory.generateObject(Resource.class))
            .withResourceKey(43L)
            .withResourceId("resource-id")
            .withResourceName("user.1/my.script.rpa")
            .withVersion(1)
            .withVersionTag("v1")
            .withDeploymentKey(100L)
            .withTenantId("tenant-1")
            .withResourceProp("rpa-content")
            .build();
    final Record<Resource> record =
        factory.generateRecord(
            ValueType.RESOURCE, r -> r.withIntent(ResourceIntent.CREATED).withValue(value));

    final DeployedResourceEntity entity = underTest.createNewEntity("43");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getResourceName()).isEqualTo("user.1/my.script.rpa");
    assertThat(entity.getResourceType()).isEqualTo("rpa");
  }
}
