/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.job;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.db.writer.ZeebeProcessInstanceWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;
import org.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;

public class ZeebeProcessInstanceDatabaseImportJob extends DatabaseImportJob<ProcessInstanceDto> {

  private final ZeebeProcessInstanceWriter zeebeProcessInstanceWriter;
  private final ConfigurationService configurationService;

  public ZeebeProcessInstanceDatabaseImportJob(final ZeebeProcessInstanceWriter zeebeProcessInstanceWriter,
                                               final ConfigurationService configurationService,
                                               final Runnable importCompleteCallback) {
    super(importCompleteCallback);
    this.zeebeProcessInstanceWriter = zeebeProcessInstanceWriter;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(List<ProcessInstanceDto> completedProcessInstances) {
    final List<ImportRequestDto> importRequests =
      zeebeProcessInstanceWriter.generateProcessInstanceImports(newOptimizeEntities);
    //todo handle it in the OPT-7228
    ElasticsearchWriterUtil.executeImportRequestsAsBulk(
      "Zeebe process instances",
      importRequests,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }

}
