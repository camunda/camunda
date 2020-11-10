/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.decision;

import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.DistributedByPart;

import java.util.Optional;

public abstract class DecisionDistributedByPart extends DistributedByPart<DecisionReportDataDto> {

  @Override
  public Optional<Boolean> isKeyOfNumericType(final ExecutionContext<DecisionReportDataDto> context) {
    return Optional.of(false);
  }

}
