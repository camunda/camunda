/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.process.identity;

import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.CandidateGroupDistributedByDto;
import org.camunda.optimize.service.AssigneeCandidateGroupService;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUPS;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDistributedByCandidateGroup extends ProcessDistributedByIdentity {

  public ProcessDistributedByCandidateGroup(final ConfigurationService configurationService,
                                            final LocalizationService localizationService,
                                            final DefinitionService definitionService,
                                            final AssigneeCandidateGroupService assigneeCandidateGroupService) {
    super(configurationService, localizationService, definitionService, assigneeCandidateGroupService);
  }

  @Override
  protected String getIdentityField() {
    return USER_TASK_CANDIDATE_GROUPS;
  }

  @Override
  protected IdentityType getIdentityType() {
    return IdentityType.GROUP;
  }

  @Override
  protected void addAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setDistributedBy(new CandidateGroupDistributedByDto());
  }
}
