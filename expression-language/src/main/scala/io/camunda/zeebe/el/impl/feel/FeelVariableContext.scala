/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.el.impl.feel

import io.camunda.zeebe.el.EvaluationContext
import org.camunda.feel.context.{CustomContext, VariableProvider}

class FeelVariableContext(context: EvaluationContext) extends CustomContext {

  override val variableProvider: VariableProvider = new EvaluationContextWrapper

  class EvaluationContextWrapper extends VariableProvider {

    override def getVariable(name: String): Option[Any] = {
      Option(context.getVariable(name))
        .filter(_.capacity > 0)
    }

    override def keys: Iterable[String] = List.empty
  }

}
