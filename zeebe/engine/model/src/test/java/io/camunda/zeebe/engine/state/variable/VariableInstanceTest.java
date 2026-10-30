/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.variable;

import static io.camunda.zeebe.protocol.record.value.ProtectionMode.REDACT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableMetadataRecord;
import io.camunda.zeebe.test.util.MsgPackUtil;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class VariableInstanceTest {

  private static final long KEY = 1L;
  private static final DirectBuffer VALUE = MsgPackUtil.asMsgPack("\"secret\"");

  private static final long LEGACY_KEY = 7L;
  private static final DirectBuffer LEGACY_VALUE = MsgPackUtil.asMsgPack("\"bar\"");

  @Test
  void shouldReadKeyValueAndProtectionModesOfStoredVariable() {
    // given
    final var stored = redactedVariable();

    // when
    final var read = new VariableInstance();
    read.wrap(serialize(stored), 0, stored.getLength());

    // then
    assertThat(read.getKey()).isEqualTo(KEY);
    assertThat(read.getValue()).isEqualTo(VALUE);
    assertThat(read.getMetadata().getProtectionModes()).containsExactly(REDACT);
  }

  @Test
  void shouldReadNoProtectionModesFromVariableStoredBeforeMetadataExisted() {
    // given
    final var legacy = serialize(new LegacyVariableInstance());

    // when
    final var read = new VariableInstance();
    read.wrap(legacy, 0, legacy.capacity());

    // then
    assertThat(read.getKey()).isEqualTo(LEGACY_KEY);
    assertThat(read.getValue()).isEqualTo(LEGACY_VALUE);
    assertThat(read.getMetadata().getProtectionModes()).isEmpty();
  }

  @Test
  void shouldNotKeepStateOfPreviousReadWhenReusedForVariableStoredBeforeMetadataExisted() {
    // given
    final var protectedVariable = redactedVariable();
    final var shared = new VariableInstance();
    shared.wrap(serialize(protectedVariable), 0, protectedVariable.getLength());

    // when
    final var legacy = serialize(new LegacyVariableInstance());
    shared.wrap(legacy, 0, legacy.capacity());

    // then
    assertThat(shared.getKey()).isEqualTo(LEGACY_KEY);
    assertThat(shared.getValue()).isEqualTo(LEGACY_VALUE);
    assertThat(shared.getMetadata().getProtectionModes()).isEmpty();
  }

  @Test
  void shouldBeReadableByVersionWithoutMetadata() {
    // given
    final var stored = redactedVariable();
    final var bytes = serialize(stored);

    // when
    final var legacy = new LegacyVariableInstance();
    legacy.wrap(bytes, 0, bytes.capacity());

    // then
    assertThat(legacy.getKey()).isEqualTo(KEY);
    assertThat(legacy.getValue()).isEqualTo(VALUE);
  }

  private static VariableInstance redactedVariable() {
    final var variable = new VariableInstance();
    variable.setKey(KEY);
    variable.setValue(VALUE, 0, VALUE.capacity());
    variable.setMetadata(new VariableMetadataRecord().setProtectionModes(Set.of(REDACT)));
    return variable;
  }

  private static DirectBuffer serialize(final UnpackedObject object) {
    final var buffer = new UnsafeBuffer(new byte[object.getLength()]);
    object.write(buffer, 0);
    return buffer;
  }

  private static final class LegacyVariableInstance extends UnpackedObject {
    private final LongProperty keyProp = new LongProperty("key", LEGACY_KEY);
    private final BinaryProperty valueProp = new BinaryProperty("value");

    LegacyVariableInstance() {
      super(2);
      declareProperty(keyProp).declareProperty(valueProp);
      valueProp.setValue(LEGACY_VALUE);
    }

    long getKey() {
      return keyProp.getValue();
    }

    DirectBuffer getValue() {
      return valueProp.getValue();
    }
  }
}
