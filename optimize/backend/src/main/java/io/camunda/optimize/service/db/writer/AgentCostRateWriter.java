/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.AgentCostRateConfigDto;

public interface AgentCostRateWriter {

  /** Replaces the single cost-rate config document with the given config. */
  void upsertConfig(final AgentCostRateConfigDto config);
}
