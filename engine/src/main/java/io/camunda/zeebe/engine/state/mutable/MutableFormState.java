/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.FormState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;

public interface MutableFormState extends FormState {

  /**
   * Put the given form in the state. Update the latest version of the form if it is newer.
   *
   * @param record the record of the form
   */
  void storeFormRecord(FormRecord record);
}
