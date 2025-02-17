/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableResourceState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class ResourceStateTest {
  private final String tenantId = "<default>";
  private MutableProcessingState processingState;
  private MutableResourceState resourceState;

  @BeforeEach
  public void setup() {
    resourceState = processingState.getResourceState();
  }

  @Test
  void shouldReturnEmptyIfNoResourceIsDeployedForResourceId() {
    // when
    final var persistedResource = resourceState.findLatestResourceById("form-1", tenantId);

    // then
    assertThat(persistedResource).isEmpty();
  }

  @Test
  void shouldReturnEmptyIfNoResourceIsDeployedForResourceKey() {
    // when
    final var persistedResource = resourceState.findResourceByKey(1L, tenantId);

    // then
    assertThat(persistedResource).isEmpty();
  }

  @Test
  void shouldReturnEmptyIfNoResourceIsDeployedForResourceIdAndDeploymentKey() {
    // when
    final var persistedResource =
        resourceState.findResourceByIdAndDeploymentKey("form-1", 1L, tenantId);

    // then
    assertThat(persistedResource).isEmpty();
  }

  @Test
  void shouldReturnEmptyIfNoResourceIsDeployedForResourceIdAndVersionTag() {
    // when
    final var persistedResource =
        resourceState.findResourceByIdAndVersionTag("form-1", "v1.0", tenantId);

    // then
    assertThat(persistedResource).isEmpty();
  }

  @Test
  void shouldStoreAndDeleteResourceInByKeyColumFamily() {
    resourceState.storeResourceInResourceColumnFamily(sampleResourceRecord());

    final Optional<PersistedResource> resourceByKey = resourceState.findResourceByKey(1L, tenantId);
    assertThat(resourceByKey).isPresent();
    assertThat(bufferAsString(resourceByKey.get().getResourceId())).isEqualTo("resource-id");
    assertThat(resourceByKey.get().getVersion()).isEqualTo(0);
    assertThat(resourceByKey.get().getDeploymentKey()).isEqualTo(1L);
    assertThat(resourceByKey.get().getVersionTag()).isEqualTo("v1.0");

    resourceState.deleteResourceInResourcesColumnFamily(sampleResourceRecord());
    final Optional<PersistedResource> deleted = resourceState.findResourceByKey(1L, tenantId);
    assertThat(deleted).isEmpty();
  }

  @Test
  void shouldStoreAndDeleteResourceInByIdColumFamily() {
    final ResourceRecord resourceRecord = sampleResourceRecord();
    resourceState.storeResourceInResourceColumnFamily(sampleResourceRecord());
    resourceState.storeResourceInResourceByIdAndVersionColumnFamily(resourceRecord);

    final Optional<PersistedResource> resourceByKey =
        resourceState.findLatestResourceById(resourceRecord.getResourceId(), tenantId);
    assertThat(resourceByKey).isPresent();
    assertThat(bufferAsString(resourceByKey.get().getResourceId())).isEqualTo("resource-id");
    assertThat(resourceByKey.get().getVersion()).isEqualTo(0);
    assertThat(resourceByKey.get().getDeploymentKey()).isEqualTo(1L);
    assertThat(resourceByKey.get().getVersionTag()).isEqualTo("v1.0");

    resourceState.deleteResourceInResourceByIdAndVersionColumnFamily(resourceRecord);
    resourceState.clearCache();
    final Optional<PersistedResource> deleted =
        resourceState.findLatestResourceById(resourceRecord.getResourceId(), tenantId);
    assertThat(deleted).isEmpty();
  }

  @Test
  void shouldStoreAndDeleteResourceInResourceIdAndDeploymentKeyColumFamily() {
    final ResourceRecord resourceRecord = sampleResourceRecord();
    resourceState.storeResourceInResourceColumnFamily(resourceRecord);
    resourceState.storeResourceInResourceKeyByResourceIdAndDeploymentKeyColumnFamily(
        resourceRecord);

    final Optional<PersistedResource> resourceByKey =
        resourceState.findResourceByIdAndDeploymentKey(
            resourceRecord.getResourceId(), 1L, tenantId);
    assertThat(resourceByKey).isPresent();
    assertThat(bufferAsString(resourceByKey.get().getResourceId())).isEqualTo("resource-id");
    assertThat(resourceByKey.get().getVersion()).isEqualTo(0);
    assertThat(resourceByKey.get().getDeploymentKey()).isEqualTo(1L);
    assertThat(resourceByKey.get().getVersionTag()).isEqualTo("v1.0");

    resourceState.deleteResourceInResourceKeyByResourceIdAndDeploymentKeyColumnFamily(
        resourceRecord);
    final Optional<PersistedResource> deleted =
        resourceState.findResourceByIdAndDeploymentKey(
            resourceRecord.getResourceId(), 1L, tenantId);
    assertThat(deleted).isEmpty();
  }

  @Test
  void shouldStoreAndDeleteResourceInResourceIdAndVersionTagColumFamily() {
    final ResourceRecord resourceRecord = sampleResourceRecord();
    resourceState.storeResourceInResourceColumnFamily(resourceRecord);
    resourceState.storeResourceInResourceKeyByResourceIdAndVersionTagColumnFamily(resourceRecord);

    final Optional<PersistedResource> resourceByKey =
        resourceState.findResourceByIdAndVersionTag(
            resourceRecord.getResourceId(), "v1.0", tenantId);
    assertThat(resourceByKey).isPresent();
    assertThat(bufferAsString(resourceByKey.get().getResourceId())).isEqualTo("resource-id");
    assertThat(resourceByKey.get().getVersion()).isEqualTo(0);
    assertThat(resourceByKey.get().getDeploymentKey()).isEqualTo(1L);
    assertThat(resourceByKey.get().getVersionTag()).isEqualTo("v1.0");

    resourceState.deleteResourceInResourceKeyByResourceIdAndVersionTagColumnFamily(resourceRecord);
    final Optional<PersistedResource> deleted =
        resourceState.findResourceByIdAndVersionTag(
            resourceRecord.getResourceId(), "v1.0", tenantId);
    assertThat(deleted).isEmpty();
  }

  private ResourceRecord sampleResourceRecord() {
    return new ResourceRecord()
        .setResourceId("resource-id")
        .setVersion(0)
        .setResourceKey(1L)
        .setTenantId(tenantId)
        .setDeploymentKey(1L)
        .setResourceName("resource-name")
        .setVersionTag("v1.0");
  }
}
