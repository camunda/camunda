/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.es.report.command.modules.distributed_by.decision;

import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.distributed_by.DistributedByPart;

public abstract class DecisionDistributedByPart extends DistributedByPart<DecisionReportDataDto> {

  @Override
  public boolean isKeyOfNumericType(final ExecutionContext<DecisionReportDataDto> context) {
    return false;
  }
}
