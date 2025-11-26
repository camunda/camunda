/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static org.assertj.core.api.Assertions.*;

import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.stream.impl.state.NextValue;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

public class NextValueTest {
  @Test
  public void shouldNotSerializeWithoutValue() {
    final var value = new NextValue();

    final var writer = new MsgPackWriter();
    final var buffer = new UnsafeBuffer(new byte[1024]);
    writer.wrap(buffer, 0);

    assertThatThrownBy(() -> value.write(writer)).isInstanceOf(MsgpackPropertyException.class);
  }
}
