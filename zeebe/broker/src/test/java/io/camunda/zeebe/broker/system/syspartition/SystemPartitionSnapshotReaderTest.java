/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.broker.system.syspartition.ClusterIdentity.IdValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SystemPartitionSnapshotReaderTest {

  @TempDir Path snapshot;

  @Test
  void shouldDecodeEntriesByKeyName() throws IOException {
    // given
    final var id = BufferUtil.bufferAsArray(BufferUtil.createCopy(new IdValue().setId("my-id")));
    final var state =
        new SystemPartitionState(
            SystemPartitionState.CURRENT_VERSION,
            Map.of(SystemPartitionKeyType.CLUSTER_ID.id(), id, 99L, id));
    new ObjectMapper()
        .writeValue(snapshot.resolve(SystemPartitionStateController.STATE_FILE).toFile(), state);

    // when
    final var result = SystemPartitionSnapshotReader.read(snapshot);

    // then
    assertThat(result.get("version").asInt()).isEqualTo(SystemPartitionState.CURRENT_VERSION);
    assertThat(result.at("/entries/CLUSTER_ID/id").asText()).isEqualTo("my-id");
    assertThat(result.at("/entries/UNKNOWN_99/id").asText()).isEqualTo("my-id");
  }
}
