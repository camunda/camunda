/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el;

import io.camunda.zeebe.el.impl.FeelExpressionLanguage;
import org.camunda.feel.FeelEngineClock;

/** The entry point to create the default {@link ExpressionLanguage}. */
public class ExpressionLanguageFactory {

  /**
   * @return a new instance of the {@link ExpressionLanguage}
   */
  public static ExpressionLanguage createExpressionLanguage(final FeelEngineClock feelEngineClock) {
    return new FeelExpressionLanguage(feelEngineClock);
  }
}
