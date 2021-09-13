/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.distributed;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.ProcessDistributedByValueDto;

public class ProcessDistributedByDto extends ProcessReportDistributedByDto<ProcessDistributedByValueDto> {

  public ProcessDistributedByDto() {
    this.type = DistributedByType.PROCESS;
  }

}
