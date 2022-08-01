/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn;

import org.agrona.DirectBuffer;

/**
 * An evaluated output of a decision table that belongs to a {@link MatchedRule matched rule}. It
 * contains details of the output and the value of the evaluated output expression.
 */
public interface EvaluatedOutput {

  /**
   * @return the id of the evaluated output
   */
  String outputId();

  /**
   * Returns the name of the evaluated output. Note that the name of the output can be different
   * from the label. The label is usually the one that is displayed in the decision table. But the
   * name is used in the decision output if the decision table has more than one output.
   *
   * <p>If a label is defined, it is favored as the output name. Otherwise, the name is used.
   *
   * @return the name of the evaluated output
   */
  String outputName();

  /**
   * Returns the value of the evaluated output expression encoded as MessagePack.
   *
   * @return the value of the evaluated output expression
   */
  DirectBuffer outputValue();
}
