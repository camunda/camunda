/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.probe;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.ProbeRecordValue;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class ProbeBatchRecordTest {

  @Test
  void shouldKeepAddedProbeWhenTheProbeInstanceIsReused() {
    // given
    final var probe =
        new ProbeRecord()
            .setProbeId("validate-configuration/io.camunda:aws-credential:1")
            .setInput(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(Map.of("region", "eu"))))
            .addSecretReference("default", "awsKey", "/accessKey")
            .setWorker("connectors");
    final var batch = new ProbeBatchRecord().addProbe(1L, probe);

    // when
    probe
        .setProbeId("validate-configuration/io.camunda:slack:1")
        .setInput(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(Map.of("other", "x"))))
        .setWorker("other-worker");

    // then
    assertThat(batch.getProbeKeys()).containsExactly(1L);
    assertThat(batch.getProbes())
        .singleElement()
        .satisfies(
            collected -> {
              assertThat(collected.getProbeId())
                  .isEqualTo("validate-configuration/io.camunda:aws-credential:1");
              assertThat(collected.getInput()).containsExactlyEntriesOf(Map.of("region", "eu"));
              assertThat(collected.getWorker()).isEqualTo("connectors");
              assertThat(collected.getSecretReferences())
                  .singleElement()
                  .satisfies(reference -> assertThat(reference.getPath()).isEqualTo("/accessKey"));
            });
  }

  @Test
  void shouldReturnProbesThatOutliveTheBatch() {
    // given
    final var batch =
        new ProbeBatchRecord()
            .addProbe(
                1L,
                new ProbeRecord().setProbeId("validate-configuration/io.camunda:aws-credential:1"));
    final ProbeRecordValue collected = batch.getProbes().getFirst();

    // when
    batch.reset();

    // then
    assertThat(collected.getProbeId())
        .isEqualTo("validate-configuration/io.camunda:aws-credential:1");
  }
}
