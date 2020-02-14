/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.process.identity;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedBy;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDistributedByAssignee extends ProcessDistributedByIdentity {


  public ProcessDistributedByAssignee(final ConfigurationService configurationService,
                                      final ProcessDefinitionReader processDefinitionReader,
                                      final LocalizationService localizationService) {
    super(configurationService, processDefinitionReader, localizationService);
  }

  @Override
  protected String getIdentityField() {
    return USER_TASK_ASSIGNEE;
  }

  @Override
  protected void addAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.getConfiguration().setDistributedBy(DistributedBy.ASSIGNEE);
  }
}
