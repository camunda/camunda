/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeHeader;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskHeaders;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;
import org.junit.jupiter.api.Test;

public class ZeebeTaskHeadersValidatorTest {

  final ZeebeTaskHeadersValidator sut = new ZeebeTaskHeadersValidator();

  @Test
  public void shouldNotAddErrorAboutDuplicateKeys() {
    // given
    final ValidationResultCollector mockValidationResultCollector =
        mock(ValidationResultCollector.class);

    final ZeebeTaskHeaders element =
        creatMockTaskHeaders(
            List.of(
                new HeaderEntry("testKey", "testValue"),
                new HeaderEntry("testKey2", "testValue2")));

    // when
    sut.validate(element, mockValidationResultCollector);

    // then
    verifyNoInteractions(mockValidationResultCollector);
  }

  @Test
  public void shouldAddErrorAboutDuplicateKey() {
    // given
    final ValidationResultCollector mockValidationResultCollector =
        mock(ValidationResultCollector.class);

    final ZeebeTaskHeaders element =
        creatMockTaskHeaders(
            List.of(
                new HeaderEntry("testKey", "testValue"), new HeaderEntry("testKey", "testValue")));

    // when
    sut.validate(element, mockValidationResultCollector);

    // then
    verify(mockValidationResultCollector)
        .addError(0, "Headers contain duplicate entries for key 'testKey'");
  }

  @Test
  public void shouldAddErrorsAboutMultipleDuplicateKeys() {
    // given
    final ValidationResultCollector mockValidationResultCollector =
        mock(ValidationResultCollector.class);

    final ZeebeTaskHeaders element =
        creatMockTaskHeaders(
            List.of(
                new HeaderEntry("testKey", "testValue"),
                new HeaderEntry("testKey", "testValue"),
                new HeaderEntry("testKey2", "testValue2"), // not duplicate
                new HeaderEntry("testKey3", "testValue3"),
                new HeaderEntry("testKey3", "testValue3")));

    // when
    sut.validate(element, mockValidationResultCollector);

    // then
    verify(mockValidationResultCollector)
        .addError(0, "Headers contain duplicate entries for key 'testKey'");
    verify(mockValidationResultCollector)
        .addError(0, "Headers contain duplicate entries for key 'testKey3'");
    verify(mockValidationResultCollector, never())
        .addError(0, "Headers contain duplicate entries for key 'testKey2'");
  }

  private ZeebeTaskHeaders creatMockTaskHeaders(final Collection<HeaderEntry> headers) {
    final var mock = mock(ZeebeTaskHeaders.class);

    final Collection<ZeebeHeader> taskHeaders =
        headers.stream().map(this::createMockTestHeader).collect(Collectors.toList());

    when(mock.getHeaders()).thenReturn(taskHeaders);

    return mock;
  }

  private ZeebeHeader createMockTestHeader(final HeaderEntry headerEntry) {
    final var mock = mock(ZeebeHeader.class);
    when(mock.getKey()).thenReturn(headerEntry.key());
    when(mock.getValue()).thenReturn(headerEntry.value());
    return mock;
  }

  private record HeaderEntry(String key, String value) {}
}
