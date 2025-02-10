/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe;

import io.camunda.zeebe.model.bpmn.builder.AbstractBaseElementBuilder;
import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType;
import java.util.Collection;
import java.util.List;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;

/**
 * Simple fake implementation of {@link ZeebeExecutionListener} to be used in tests.
 *
 * <p>Feel free to extend this class to support more use cases as needed.
 */
public final class FakeZeebeExecutionListener implements ZeebeExecutionListener {

  private final String eventType;
  private final String type;
  private final String retries;

  public FakeZeebeExecutionListener(
      final String eventType, final String type, final String retries) {
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
