/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson.record;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRelated;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import java.util.stream.Stream;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * This test ensures that there is an immutable variant of {@link Record} generated, and one for
 * each interface found in {@link io.camunda.zeebe.protocol.record.value} (and its subpackages).
 *
 * <p>This assumes that all interfaces in there are meant to be protocol types.
 */
@Execution(ExecutionMode.CONCURRENT)
final class CompletenessTest {
  private static final ClassInfoList ABSTRACT_TYPES =
      new ClassGraph()
          .acceptPackages("io.camunda.zeebe.protocol.jackson.record")
          .scan()
          .getAllStandardClasses()
          .filter(ClassInfo::isAbstract)
          .filter(info -> !info.isInnerClass());

  @ParameterizedTest(name = "{0}")
  @MethodSource("provideProtocolTypes")
  void shouldGenerateImmutableRecord(
      @SuppressWarnings("unused") final String className, final Class<?> protocolClass) {
    // given
    final ClassInfoList abstractClass =
        ABSTRACT_TYPES.filter(info -> info.implementsInterface(protocolClass)).directOnly();

    // then
    assertThat(abstractClass)
        .as(
            "there should be exactly one abstract variant '%s' in this module; "
                + "if the interface in question is actually a meta interface, like '%s', "
                + "please add it to the rejectClasses(...) call below",
            protocolClass, ProcessInstanceRelated.class)
        .isNotEmpty();
  }

  private static Stream<Arguments> provideProtocolTypes() {
    return findProtocolTypes().stream()
        .map(info -> Arguments.of(info.getSimpleName(), info.loadClass()));
  }

  private static ClassInfoList findProtocolTypes() {
    return new ClassGraph()
        .acceptClasses(Record.class.getName())
        .acceptPackages(
            "io.camunda.zeebe.protocol.record.value", "io.camunda.zeebe.protocol.record.value.*")
        .rejectClasses(ProcessInstanceRelated.class.getName())
        .scan()
        .getAllInterfaces();
  }
}
