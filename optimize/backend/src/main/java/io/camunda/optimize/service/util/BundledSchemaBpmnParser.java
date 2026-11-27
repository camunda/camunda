/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import java.io.InputStream;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.impl.BpmnParser;

public final class BundledSchemaBpmnParser extends BpmnParser {

  private static final BundledSchemaBpmnParser INSTANCE = new BundledSchemaBpmnParser();

  private BundledSchemaBpmnParser() {}

  public static BpmnModelInstance readModelFromStream(final InputStream stream) {
    return INSTANCE.parseModelFromStream(stream);
  }
}
