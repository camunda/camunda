/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

/** A representation of an element that publishes a message. For example, a message throw event. */
public interface ExectuablePublishMessage extends ExecutableFlowElement {

  PublishMessageProperties getPublishMessageProperties();

  void setPublishMessageProperties(PublishMessageProperties publishMessageProperties);
}
