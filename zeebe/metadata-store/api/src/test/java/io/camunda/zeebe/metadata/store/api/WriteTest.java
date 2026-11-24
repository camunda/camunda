/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.InvalidArgumentException;
import io.camunda.zeebe.metadata.store.api.Write.Check;
import io.camunda.zeebe.metadata.store.api.Write.Delete;
import io.camunda.zeebe.metadata.store.api.Write.DeletePrefix;
import io.camunda.zeebe.metadata.store.api.Write.Put;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Each write carries only valid arguments: no empty key, and a check always checks something. */
final class WriteTest {

  private static final Namespace NAMESPACE = Namespace.cluster("config");
  private static final StoreKey KEY = StoreKey.of(NAMESPACE, "a");
  private static final StoreValue VALUE = StoreValue.of(new byte[] {1});

  @ParameterizedTest(name = "{0}")
  @MethodSource("keyWrites")
  void shouldRejectEmptyKey(final String kind, final Function<StoreKey, Write> write) {
    // when - then
    assertThatThrownBy(() -> write.apply(StoreKey.of(NAMESPACE, "")))
        .isInstanceOf(InvalidArgumentException.class);
  }

  @Test
  void shouldMakeWriteWithoutConditionUnconditional() {
    // when - then
    assertThat(new Put(KEY, VALUE).condition()).isEqualTo(Condition.none());
    assertThat(new Delete(KEY).condition()).isEqualTo(Condition.none());
  }

  @Test
  void shouldRejectCheckWithoutCondition() {
    // when - then
    assertThatThrownBy(() -> new Check(KEY, Condition.none()))
        .isInstanceOf(InvalidArgumentException.class);
  }

  static Stream<Arguments> keyWrites() {
    return Stream.of(
        Arguments.of("Put", (Function<StoreKey, Write>) key -> new Put(key, VALUE)),
        Arguments.of("Delete", (Function<StoreKey, Write>) Delete::new),
        Arguments.of("DeletePrefix", (Function<StoreKey, Write>) DeletePrefix::new),
        Arguments.of(
            "Check", (Function<StoreKey, Write>) key -> new Check(key, Condition.exists())));
  }
}
