/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordAssert;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.ValueTypeMapping;
import io.camunda.zeebe.protocol.record.ValueTypeMapping.Mapping;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("unchecked")
@Execution(ExecutionMode.CONCURRENT)
final class ZeebeProtocolModuleTest {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new ZeebeProtocolModule());

  private final ProtocolFactory factory = new ProtocolFactory();

  @ParameterizedTest
  @MethodSource("provideValueTypes")
  void shouldDeserializeRecord(final ValueType valueType) throws IOException {
    // given
    final Record<RecordValue> record = factory.generateRecord(valueType);
    final byte[] serialized = MAPPER.writeValueAsBytes(record);

    // when
    final Record<RecordValue> deserialized =
        (Record<RecordValue>) MAPPER.readValue(serialized, Record.class);

    // then
    RecordAssert.assertThat(deserialized).isEqualTo(record);
  }

  @ParameterizedTest
  @MethodSource("provideValueTypes")
  void shouldIgnoreUnknownPropertiesOfRecord(final ValueType valueType) throws IOException {
    // given
    final Record<RecordValue> record = factory.generateRecord(valueType);
    final ObjectNode jsonRecord = MAPPER.valueToTree(record);

    // when
    jsonRecord.put("nonExistentProperty", "something");
    final byte[] serialized = MAPPER.writeValueAsBytes(jsonRecord);
    final Record<RecordValue> deserialized =
        (Record<RecordValue>) MAPPER.readValue(serialized, Record.class);

    // then
    RecordAssert.assertThat(deserialized).isEqualTo(record);
  }

  @ParameterizedTest
  @MethodSource("provideValueTypes")
  void shouldIgnorePropertiesOfRecordValue(final ValueType valueType) throws IOException {
    // given
    final Mapping<?, ?> typeInfo = ValueTypeMapping.get(valueType);
    final RecordValue value = factory.generateObject(typeInfo.getValueClass());
    final ObjectNode jsonValue = MAPPER.valueToTree(value);

    // when
    jsonValue.put("nonExistentProperty", "something");
    final byte[] serialized = MAPPER.writeValueAsBytes(jsonValue);
    final RecordValue deserialized = MAPPER.readValue(serialized, typeInfo.getValueClass());

    // then
    assertThat(deserialized).isEqualTo(value);
  }

  private static Stream<ValueType> provideValueTypes() {
    return ValueTypeMapping.getAcceptedValueTypes().stream();
  }
}
