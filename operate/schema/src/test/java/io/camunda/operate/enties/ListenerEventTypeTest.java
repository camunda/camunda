/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.enties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.camunda.webapps.schema.entities.listener.ListenerEventType;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ListenerEventTypeTest {

  static Stream<Arguments> listenerEventTypeProvider() {
    return Stream.of(
        // Known Execution Listener event types
        arguments("START", ListenerEventType.START),
        arguments("END", ListenerEventType.END),

        // Known User Task Listener event types
        arguments("CREATING", ListenerEventType.CREATING),
        arguments("ASSIGNING", ListenerEventType.ASSIGNING),
        arguments("UPDATING", ListenerEventType.UPDATING),
        arguments("COMPLETING", ListenerEventType.COMPLETING),
        arguments("CANCELING", ListenerEventType.CANCELING),

        // Unknown event types (invalid or unexpected input)
        arguments("FOO", ListenerEventType.UNKNOWN),
        arguments("", ListenerEventType.UNKNOWN),

        // Lowercase variants (not supported, should map to 'UNKNOWN')
        arguments("start", ListenerEventType.UNKNOWN),
        arguments("end", ListenerEventType.UNKNOWN),
        arguments("creating", ListenerEventType.UNKNOWN),
        arguments("canceling", ListenerEventType.UNKNOWN),

        // Explicit 'UNSPECIFIED' and 'null' input
        arguments("UNSPECIFIED", ListenerEventType.UNSPECIFIED),
        arguments(null, ListenerEventType.UNSPECIFIED));
  }

  @ParameterizedTest(name = "should map \"{0}\" to ListenerEventType.{1}")
  @MethodSource("listenerEventTypeProvider")
  void verifyMappingFromZeebeListenerEventTypeToEnumValue(
      final String input, final ListenerEventType expected) {
    // when
    final var result = ListenerEventType.fromZeebeListenerEventType(input);

    // then
    assertThat(result).isEqualTo(expected);
  }
}
