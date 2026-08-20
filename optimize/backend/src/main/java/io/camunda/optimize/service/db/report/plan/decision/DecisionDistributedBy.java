/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.plan.decision;

import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessReportDistributedByDto;

public enum DecisionDistributedBy {
  DECISION_DISTRIBUTED_BY_NONE(new NoneDistributedByDto());

  private final ProcessReportDistributedByDto<?> dto;

  private DecisionDistributedBy(final ProcessReportDistributedByDto<?> dto) {
    this.dto = dto;
  }

  public ProcessReportDistributedByDto<?> getDto() {
    return this.dto;
  }
}
