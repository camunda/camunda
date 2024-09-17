/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.identity;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_ASSIGNEE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE;

import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.service.AssigneeCandidateGroupService;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class ProcessGroupByAssigneeInterpreterES
    extends AbstractProcessGroupByIdentityInterpreterES {
  @Getter final AssigneeCandidateGroupService assigneeCandidateGroupService;
  @Getter final ConfigurationService configurationService;
  @Getter final DefinitionService definitionService;
  @Getter final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  @Getter final ProcessViewInterpreterFacadeES viewInterpreter;
  @Getter final LocalizationService localizationService;

  @Override
  protected String getIdentityField() {
    return USER_TASK_ASSIGNEE;
  }

  @Override
  protected IdentityType getIdentityType() {
    return IdentityType.USER;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_ASSIGNEE);
  }
}
