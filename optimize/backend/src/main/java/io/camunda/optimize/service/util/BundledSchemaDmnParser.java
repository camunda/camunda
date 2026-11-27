/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import java.io.InputStream;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.impl.DmnParser;

final class BundledSchemaDmnParser extends DmnParser {

  private static final BundledSchemaDmnParser INSTANCE = new BundledSchemaDmnParser();

  private BundledSchemaDmnParser() {}

  static DmnModelInstance readModelFromStream(final InputStream stream) {
    return INSTANCE.parseModelFromStream(stream);
  }
}
