/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.distributedby.process.date;

import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.DateDistributedByValueDto;
import java.util.Optional;

public interface ProcessDistributedByInstanceDateInterpreter {
  default AggregateByDateUnit getDistributedByDateUnit(
      final ProcessReportDataDto processReportData) {
    return Optional.ofNullable(
            ((DateDistributedByValueDto) processReportData.getDistributedBy().getValue()))
        .map(DateDistributedByValueDto::getUnit)
        .orElse(AggregateByDateUnit.AUTOMATIC);
  }
}
