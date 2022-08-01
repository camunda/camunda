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
 * An evaluated input of a decision table. It contains details of the input and the value of the
 * evaluated input expression.
 */
public interface EvaluatedInput {

  /**
   * @return the id of the evaluated input
   */
  String inputId();

  /**
   * Returns the name of the evaluated input. Note that it uses the label of the input in absence of
   * the name. The label is usually the one that is displayed in the decision table.
   *
   * <p>If a label is defined, it is favored as the output name. Otherwise, the expression is used.
   *
   * @return the name of the evaluated input
   */
  String inputName();

  /**
   * Returns the value of the evaluated input expression encoded as MessagePack.
   *
   * @return the value of the evaluated input expression
   */
  DirectBuffer inputValue();
}
