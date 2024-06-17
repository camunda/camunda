/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.distributed_by.process.identity;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUPS;

import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.CandidateGroupDistributedByDto;
import io.camunda.optimize.service.AssigneeCandidateGroupService;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDistributedByCandidateGroup extends ProcessDistributedByIdentity {

  public ProcessDistributedByCandidateGroup(
      final ConfigurationService configurationService,
      final LocalizationService localizationService,
      final DefinitionService definitionService,
      final AssigneeCandidateGroupService assigneeCandidateGroupService) {
    super(
        configurationService,
        localizationService,
        definitionService,
        assigneeCandidateGroupService);
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
  protected void addAdjustmentsForCommandKeyGeneration(
      final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setDistributedBy(new CandidateGroupDistributedByDto());
  }
}
