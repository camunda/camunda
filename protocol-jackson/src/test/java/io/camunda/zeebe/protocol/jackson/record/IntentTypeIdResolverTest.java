/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson.record;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.BeanDeserializerFactory;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.io.IOException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

final class IntentTypeIdResolverTest {

  /**
   * This test checks that every known intent type is handled. It doesn't validate the correctness
   * of the result - its goal is to be a smoke test to make sure no intents are forgotten
   *
   * @deprecated to be removed when intent classes are directly mapped via the {@link ValueType}
   *     enum
   */
  @EnumSource(
      value = ValueType.class,
      names = {"NULL_VAL", "SBE_UNKNOWN"},
      mode = Mode.EXCLUDE)
  @ParameterizedTest
  void shouldHandleEveryKnownValueType(final ValueType type) throws IOException {
    // given
    final var mapper = new ObjectMapper();
    final var baseContext =
        new DefaultDeserializationContext.Impl(BeanDeserializerFactory.instance);
    final var context =
        baseContext.createInstance(
            mapper.getDeserializationConfig(),
            mapper.createParser("{}"),
            mapper.getInjectableValues());
    final var resolver = new IntentTypeIdResolver();
    final var resolvedType = resolver.typeFromId(context, resolver.idFromValue(type));

    assertThat(Intent.class).isAssignableFrom(resolvedType.getRawClass());
  }
}
