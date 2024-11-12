/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class ExporterStateDistributeMessageTest {

  @Test
  public void shouldEncodeExporterState() {
    // given
    final var metadata1 = BufferUtil.wrapString("e1");
    final var metadata2 = BufferUtil.wrapString("e2");

    final var distributeMessage = new ExporterStateDistributeMessage();
    distributeMessage.putExporter("elasticsearch", 1001, metadata1);
    distributeMessage.putExporter("metrics", 95, metadata2);
    final var length = distributeMessage.getLength();

    // when
    final var bytes = new byte[length];
    final var buffer = new UnsafeBuffer(bytes);
    distributeMessage.write(buffer, 0);

    // then
    assertThat(buffer.capacity()).isEqualTo(distributeMessage.getLength());
    assertThat(bytes)
        .containsSubsequence("elasticsearch".getBytes())
        .containsSubsequence(metadata1.byteArray())
        .containsSubsequence("metrics".getBytes())
        .containsSubsequence(metadata2.byteArray());
  }

  @Test
  public void shouldDecodeExporterState() {
    // given
    final var metadata1 = BufferUtil.wrapString("e1");
    final var metadata2 = BufferUtil.wrapString("e2");

    final var distributeMessage = new ExporterStateDistributeMessage();
    distributeMessage.putExporter("elasticsearch", 1001, metadata1);
    distributeMessage.putExporter("metrics", 95, metadata2);

    final var length = distributeMessage.getLength();
    final var buffer = new UnsafeBuffer(new byte[length]);
    distributeMessage.write(buffer, 0);

    // when
    final var otherMessage = new ExporterStateDistributeMessage();
    otherMessage.wrap(buffer, 0, length);

    // then
    assertThat(otherMessage.getExporterState()).isEqualTo(distributeMessage.getExporterState());
  }
}
