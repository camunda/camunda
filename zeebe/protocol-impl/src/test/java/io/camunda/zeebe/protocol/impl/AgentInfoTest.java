/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.AgentInfo;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class AgentInfoTest {

  @Nested
  class EncodeDecodeTests {
    @Test
    void shouldEncodeDecodeAgentInfo() {
      // given
      final AgentInfo info = new AgentInfo().setElementId("foo");

      // when
      encodeDecode(info);

      // then
      assertThat(info.getElementId()).isEqualTo("foo");
    }

    @Test
    void shouldEncodeDecodeEmptyAgentInfo() {
      // given
      final AgentInfo info = new AgentInfo();

      // when
      encodeDecode(info);

      // then
      assertThat(info.getElementId()).isEqualTo("");
    }

    private void encodeDecode(final AgentInfo info) {
      // encode
      final UnsafeBuffer buffer = new UnsafeBuffer(new byte[info.getLength()]);
      info.write(buffer, 0);

      // decode
      info.reset();
      info.wrap(buffer, 0, buffer.capacity());
    }
  }

  @Nested
  class OfTests {
    @Test
    void shouldReturnNullWhenOfCalledWithNull() {
      // when
      final AgentInfo result = AgentInfo.of(null);

      // then
      assertThat(result).isNull();
    }

    @Test
    void shouldCopyAgentInfoWhenOfCalledWithNonNull() {
      // given
      final AgentInfo original = new AgentInfo();
      original.setElementId("test-element-id");

      // when
      final AgentInfo copy = AgentInfo.of(original);

      // then
      assertThat(copy).isNotNull();
      assertThat(copy).isNotSameAs(original);
      assertThat(copy.getElementId()).isEqualTo(original.getElementId());
    }
  }
}
