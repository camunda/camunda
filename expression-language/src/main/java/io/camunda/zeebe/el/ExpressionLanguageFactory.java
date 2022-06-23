/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.el;

import io.camunda.zeebe.el.impl.FeelExpressionLanguage;
import io.camunda.zeebe.scheduler.clock.ActorClock;

/** The entry point to create the default {@link ExpressionLanguage}. */
public class ExpressionLanguageFactory {

  /**
   * @return a new instance of the {@link ExpressionLanguage}
   */
  public static ExpressionLanguage createExpressionLanguage() {
    return new FeelExpressionLanguage(ActorClock.current());
  }
}
