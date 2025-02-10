/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.util.FakeExpressionLanguage;
import io.camunda.zeebe.model.bpmn.builder.AbstractBaseElementBuilder;
import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExecutionListenerTransformerTest {

  @Test
  void shouldNotAddListenersIfNoneProvided() {
    // given
    final var process = new ExecutableProcess("process");

    // when
    final Collection<ZeebeExecutionListener> listeners = List.of();
    new ExecutionListenerTransformer().transform(process, listeners, new FakeExpressionLanguage());

    // then
    Assertions.assertThat(process.hasExecutionListeners()).isFalse();
  }

  @Test
  void shouldAddProvidedListener() {
    // given
    final var process = new ExecutableProcess("process");

    // when
    final var listener = new FakeZeebeExecutionListener("start", "type", "3");
    new ExecutionListenerTransformer()
        .transform(process, List.of(listener), new FakeExpressionLanguage());

    // then
    Assertions.assertThat(process.hasExecutionListeners()).isTrue();
  }

  static final class FakeZeebeExecutionListener implements ZeebeExecutionListener {

    final String eventType;
    final String type;
    final String retries;

    FakeZeebeExecutionListener(final String eventType, final String type, final String retries) {
      this.eventType = eventType;
      this.type = type;
      this.retries = retries;
    }

    @Override
    public DomElement getDomElement() {
      return null;
    }

    @Override
    public ModelInstance getModelInstance() {
      return null;
    }

    @Override
    public ModelElementInstance getParentElement() {
      return null;
    }

    @Override
    public ModelElementType getElementType() {
      return null;
    }

    @Override
    public String getAttributeValue(final String s) {
      return "";
    }

    @Override
    public void setAttributeValue(final String s, final String s1) {}

    @Override
    public void setAttributeValue(final String s, final String s1, final boolean b) {}

    @Override
    public void setAttributeValue(
        final String s, final String s1, final boolean b, final boolean b1) {}

    @Override
    public void removeAttribute(final String s) {}

    @Override
    public String getAttributeValueNs(final String s, final String s1) {
      return "";
    }

    @Override
    public void setAttributeValueNs(final String s, final String s1, final String s2) {}

    @Override
    public void setAttributeValueNs(
        final String s, final String s1, final String s2, final boolean b) {}

    @Override
    public void setAttributeValueNs(
        final String s, final String s1, final String s2, final boolean b, final boolean b1) {}

    @Override
    public void removeAttributeNs(final String s, final String s1) {}

    @Override
    public String getTextContent() {
      return "";
    }

    @Override
    public String getRawTextContent() {
      return "";
    }

    @Override
    public void setTextContent(final String s) {}

    @Override
    public void replaceWithElement(final ModelElementInstance modelElementInstance) {}

    @Override
    public ModelElementInstance getUniqueChildElementByNameNs(final String s, final String s1) {
      return null;
    }

    @Override
    public ModelElementInstance getUniqueChildElementByType(
        final Class<? extends ModelElementInstance> aClass) {
      return null;
    }

    @Override
    public void setUniqueChildElementByNameNs(final ModelElementInstance modelElementInstance) {}

    @Override
    public void replaceChildElement(
        final ModelElementInstance modelElementInstance,
        final ModelElementInstance modelElementInstance1) {}

    @Override
    public void addChildElement(final ModelElementInstance modelElementInstance) {}

    @Override
    public boolean removeChildElement(final ModelElementInstance modelElementInstance) {
      return false;
    }

    @Override
    public Collection<ModelElementInstance> getChildElementsByType(
        final ModelElementType modelElementType) {
      return List.of();
    }

    @Override
    public <T extends ModelElementInstance> Collection<T> getChildElementsByType(
        final Class<T> aClass) {
      return List.of();
    }

    @Override
    public void insertElementAfter(
        final ModelElementInstance modelElementInstance,
        final ModelElementInstance modelElementInstance1) {}

    @Override
    public void updateAfterReplacement() {}

    @Override
    public AbstractBaseElementBuilder builder() {
      return null;
    }

    @Override
    public boolean isScope() {
      return false;
    }

    @Override
    public BpmnModelElementInstance getScope() {
      return null;
    }

    @Override
    public ZeebeExecutionListenerEventType getEventType() {
      return eventType == null ? null : ZeebeExecutionListenerEventType.valueOf(eventType);
    }

    @Override
    public void setEventType(final ZeebeExecutionListenerEventType eventType) {}

    @Override
    public String getType() {
      return type;
    }

    @Override
    public void setType(final String type) {}

    @Override
    public String getRetries() {
      return retries;
    }

    @Override
    public void setRetries(final String retries) {}
  }

  @Nested
  class ShouldIgnoreMisconfiguredListeners {

    @Test
    void forNullListener() {
      // given
      final var process = new ExecutableProcess("process");

      // when
      final var listeners = new ArrayList<ZeebeExecutionListener>();
      listeners.add(null);
      new ExecutionListenerTransformer()
          .transform(process, listeners, new FakeExpressionLanguage());

      // then
      Assertions.assertThat(process.hasExecutionListeners()).isFalse();
    }

    @Test
    void forMisconfiguredListenerWithoutEventType() {
      // given
      final var process = new ExecutableProcess("process");

      // when
      final var listeners = new ArrayList<ZeebeExecutionListener>();
      listeners.add(new FakeZeebeExecutionListener(null, "type", "3"));
      new ExecutionListenerTransformer()
          .transform(process, listeners, new FakeExpressionLanguage());

      // then
      Assertions.assertThat(process.hasExecutionListeners()).isFalse();
    }

    @Test
    void forMisconfiguredListenerWithoutType() {
      // given
      final var process = new ExecutableProcess("process");

      // when
      final var listeners = new ArrayList<ZeebeExecutionListener>();
      listeners.add(new FakeZeebeExecutionListener("start", null, "3"));
      new ExecutionListenerTransformer()
          .transform(process, listeners, new FakeExpressionLanguage());

      // then
      Assertions.assertThat(process.hasExecutionListeners()).isFalse();
    }

    @Test
    void forMisconfiguredListenerWithoutRetries() {
      // given
      final var process = new ExecutableProcess("process");

      // when
      final var listeners = new ArrayList<ZeebeExecutionListener>();
      listeners.add(new FakeZeebeExecutionListener("start", "type", null));
      new ExecutionListenerTransformer()
          .transform(process, listeners, new FakeExpressionLanguage());

      // then
      Assertions.assertThat(process.hasExecutionListeners()).isFalse();
    }
  }
}
