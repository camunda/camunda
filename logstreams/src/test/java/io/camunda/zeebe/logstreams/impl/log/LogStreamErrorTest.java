/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.logstreams.storage.LogStorage;
import java.lang.reflect.InvocationTargetException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("resource")
final class LogStreamErrorTest {

  @ParameterizedTest
  @ValueSource(classes = {RuntimeException.class, Error.class})
  void createFailsIfReaderFails(final Class<? extends Throwable> exceptionType)
      throws NoSuchMethodException, InvocationTargetException, InstantiationException,
          IllegalAccessException {
    // given
    final var logStorage = mock(LogStorage.class);

    // when
    final var readerException = exceptionType.getConstructor().newInstance();
    doThrow(readerException).when(logStorage).newReader();

    // then
    Assertions.assertThatCode(
            () -> new LogStreamImpl("test-log", 1, 1, 16 * 1024 * 1024, logStorage))
        .isEqualTo(readerException);
  }
}
