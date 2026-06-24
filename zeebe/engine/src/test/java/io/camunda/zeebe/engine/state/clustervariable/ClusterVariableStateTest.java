/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.clustervariable;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableClusterVariableState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
class ClusterVariableStateTest {

  private MutableProcessingState processingState;
  private MutableClusterVariableState clusterVariableState;

  @BeforeEach
  void setup() {
    clusterVariableState = processingState.getClusterVariableState();
  }

  @Test
  void shouldStoreAndRetrieveMetadataForGloballyScopedVariable() {
    // given
    final var metadata = Map.<String, Object>of("credentialType", "OAUTH2");
    final var record =
        new ClusterVariableRecord()
            .setName("globalVar")
            .setGlobalScope()
            .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("\"value\"")))
            .setMetadata(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(metadata)));

    // when
    clusterVariableState.createGloballyScopedClusterVariable(record);

    // then
    final var stored =
        clusterVariableState.getGloballyScopedClusterVariable(BufferUtil.wrapString("globalVar"));
    assertThat(stored).isPresent();
    assertThat(stored.get().getRecord().getMetadata()).containsExactlyInAnyOrderEntriesOf(metadata);
  }

  @Test
  void shouldStoreAndRetrieveMetadataForTenantScopedVariable() {
    // given
    final var tenantId = "tenant-1";
    final var metadata = Map.<String, Object>of("credentialType", "API_KEY", "version", "2");
    final var record =
        new ClusterVariableRecord()
            .setName("tenantVar")
            .setTenantScope()
            .setTenantId(tenantId)
            .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("\"value\"")))
            .setMetadata(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(metadata)));

    // when
    clusterVariableState.createTenantScopedClusterVariable(record);

    // then
    final var stored =
        clusterVariableState.getTenantScopedClusterVariable(
            BufferUtil.wrapString("tenantVar"), tenantId);
    assertThat(stored).isPresent();
    assertThat(stored.get().getRecord().getMetadata()).containsExactlyInAnyOrderEntriesOf(metadata);
  }

  @Test
  void shouldPreserveUpdatedMetadataOnGloballyScopedVariable() {
    // given
    final var initialMetadata = Map.<String, Object>of("credentialType", "API_KEY");
    final var updatedMetadata = Map.<String, Object>of("credentialType", "OAUTH2", "version", "2");
    final var record =
        new ClusterVariableRecord()
            .setName("updateVar")
            .setGlobalScope()
            .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("\"oldValue\"")))
            .setMetadata(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(initialMetadata)));
    clusterVariableState.createGloballyScopedClusterVariable(record);

    // when
    record
        .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("\"newValue\"")))
        .setMetadata(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(updatedMetadata)));
    clusterVariableState.updateGloballyScopedClusterVariable(record);

    // then
    final var stored =
        clusterVariableState.getGloballyScopedClusterVariable(BufferUtil.wrapString("updateVar"));
    assertThat(stored).isPresent();
    assertThat(stored.get().getRecord().getMetadata())
        .containsExactlyInAnyOrderEntriesOf(updatedMetadata);
  }
}
