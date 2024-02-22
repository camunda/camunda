/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.BeanDeserializerFactory;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.ValueTypeMapping;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@Execution(ExecutionMode.CONCURRENT)
final class IntentTypeIdResolverTest {

  /**
   * This test checks that every known intent type is handled. It doesn't validate the correctness
   * of the result - its goal is to be a smoke test to make sure no intents are forgotten
   *
   * @deprecated to be removed when intent classes are directly mapped via the {@link ValueType}
   *     enum
   */
  @ParameterizedTest
  @MethodSource("provideValueTypes")
  void shouldHandleEveryKnownValueType(final ValueType type) throws IOException {
    // given
    final ObjectMapper mapper = new ObjectMapper();
    final DefaultDeserializationContext.Impl baseContext =
        new DefaultDeserializationContext.Impl(BeanDeserializerFactory.instance);
    final DefaultDeserializationContext context =
        baseContext.createInstance(
            mapper.getDeserializationConfig(),
            mapper.createParser("{}"),
            mapper.getInjectableValues());
    final IntentTypeIdResolver resolver = new IntentTypeIdResolver();
    final JavaType resolvedType = resolver.typeFromId(context, resolver.idFromValue(type));

    assertThat(Intent.class).isAssignableFrom(resolvedType.getRawClass());
  }

  private static Stream<ValueType> provideValueTypes() {
    return ValueTypeMapping.getAcceptedValueTypes().stream();
  }
}
