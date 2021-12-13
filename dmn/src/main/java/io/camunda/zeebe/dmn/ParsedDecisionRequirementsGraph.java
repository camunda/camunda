/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn;

import java.util.Collection;

/** A parsed DMN decision requirements graph (DRG). */
public interface ParsedDecisionRequirementsGraph {

  /** @return the id of the DRG, or {@code null} if the DMN is not valid */
  String getId();

  /** @return the name of the DRG, or {@code null} if the DMN is not valid */
  String getName();

  /** @return the namespace of the DRG, or {@code null} if the DMN is not valid */
  String getNamespace();

  /**
   * @return the decisions that are contained in the DRG, or an empty collection if the DMN is not
   *     valid
   */
  Collection<ParsedDecision> getDecisions();

  /** @return {@code true} if the DMN is valid */
  boolean isValid();

  /**
   * Returns the reason why the DMN is not valid. Use {@link #isValid()} to check if the DMN is
   * valid or not.
   *
   * @return the failure message if the DMN is not valid, or {@code null} if the DMN is valid
   */
  String getFailureMessage();
}
