/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn.impl;

import io.camunda.zeebe.dmn.EvaluatedOutput;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.camunda.dmn.Audit;
import org.camunda.feel.syntaxtree.Val;

public record EvaluatedDmnScalaOutput(String outputId, String outputName, DirectBuffer outputValue)
    implements EvaluatedOutput {

  public static EvaluatedDmnScalaOutput of(
      final Audit.EvaluatedOutput evaluatedOutput, final Function<Val, DirectBuffer> converter) {
    final var output = evaluatedOutput.output();
    final var outputValue = evaluatedOutput.value();
    // Just like the Modeler, we favor the label over the name
    final var outputName = output.label() != null ? output.label() : output.name();
    return new EvaluatedDmnScalaOutput(output.id(), outputName, converter.apply(outputValue));
  }
}
