/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.process;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.process.util.ProcessModelUtil;
import org.junit.jupiter.api.Test;

public class ProcessModelUtilTest {

  @Test
  void shouldExtractFormKey() {
    final var entity = TestData.processEntityWithPublicFormKey(1L);

    final var startEvent =
        ProcessModelUtil.processStartEvent(
            entity.getBpmnXml().getBytes(), entity.getBpmnProcessId());
    assertThat(startEvent).isPresent();
    final var formKey = ProcessModelUtil.extractFormKey(startEvent.get());
    final var formId = ProcessModelUtil.extractFormId(startEvent.get());

    assertThat(formKey).isPresent();
    assertThat(formKey.get()).isEqualTo("camunda-forms:bpmn:testForm");

    assertThat(formId).isEmpty();
  }

  @Test
  void shouldExtractFormId() {
    final var entity = TestData.processEntityWithPublicFormId(1L);

    final var startEvent =
        ProcessModelUtil.processStartEvent(
            entity.getBpmnXml().getBytes(), entity.getBpmnProcessId());
    assertThat(startEvent).isPresent();
    final var formId = ProcessModelUtil.extractFormId(startEvent.get());
    final var formKey = ProcessModelUtil.extractFormKey(startEvent.get());

    assertThat(formId).isPresent();
    assertThat(formId.get()).isEqualTo("testForm");

    assertThat(formKey).isEmpty();
  }

  @Test
  void shouldExtractIsPublic() {
    final var entity = TestData.processEntityWithPublicFormId(1L);

    final var startEvent =
        ProcessModelUtil.processStartEvent(
            entity.getBpmnXml().getBytes(), entity.getBpmnProcessId());
    assertThat(startEvent).isPresent();
    final var isPublic = ProcessModelUtil.extractIsPublic(startEvent.get());

    assertThat(isPublic).isPresent();
    assertThat(isPublic.get()).isTrue();
  }

  @Test
  void shouldNotExtractData() {
    final var entity = TestData.processEntityWithoutForm(1L);

    final var startEvent =
        ProcessModelUtil.processStartEvent(
            entity.getBpmnXml().getBytes(), entity.getBpmnProcessId());
    assertThat(startEvent).isPresent();

    final var isPublic = ProcessModelUtil.extractIsPublic(startEvent.get());
    final var formId = ProcessModelUtil.extractFormId(startEvent.get());
    final var formKey = ProcessModelUtil.extractFormKey(startEvent.get());

    assertThat(isPublic).isEmpty();
    assertThat(formId).isEmpty();
    assertThat(formKey).isEmpty();
  }
}
