/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn;

/** A parsed DMN decision. A decision is contained in a decision requirements graph (DRG). */
public interface ParsedDecision {

  /** @return the name of the decision */
  String getName();

  /** @return the id of the decision */
  String getId();
}
