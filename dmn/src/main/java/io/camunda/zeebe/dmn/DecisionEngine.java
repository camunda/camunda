/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn;

import java.io.InputStream;

/**
 * Parses and evaluates DMN decisions. A decision can be parsed and stored as object. A parsed
 * decision needs to be used to evaluate the decision with a given variable context.
 */
public interface DecisionEngine {

  /**
   * Parses the given DMN resource into a parsed decision object.
   *
   * <p>If the DMN is not valid then it returns a result object that contains the failure message,
   * instead of throwing an exception.
   *
   * @param dmnResource the DMN resource as input stream
   * @return the parsed decision, or the failure message if the DMN is not valid
   */
  ParsedDecisionRequirementsGraph parse(InputStream dmnResource);
}
