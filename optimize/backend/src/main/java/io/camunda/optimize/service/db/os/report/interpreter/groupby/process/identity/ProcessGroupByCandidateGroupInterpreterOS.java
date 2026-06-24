/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process.identity;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_CANDIDATE_GROUP;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUPS;

import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.report.interpreter.groupby.process.identity.ProcessGroupByIdentityInterpreterHelper;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessGroupByCandidateGroupInterpreterOS
    extends AbstractProcessGroupByIdentityInterpreterOS {
  private final ConfigurationService configurationService;
  private final DefinitionService definitionService;
  private final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;
  private final ProcessGroupByIdentityInterpreterHelper helper;

  public ProcessGroupByCandidateGroupInterpreterOS(
      final ConfigurationService configurationService,
      final DefinitionService definitionService,
      final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter,
      final ProcessViewInterpreterFacadeOS viewInterpreter,
      final ProcessGroupByIdentityInterpreterHelper helper) {
    this.configurationService = configurationService;
    this.definitionService = definitionService;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
    this.helper = helper;
  }

  @Override
  protected ConfigurationService getConfigurationService() {
    return configurationService;
  }

  @Override
  protected DefinitionService getDefinitionService() {
    return definitionService;
  }

  @Override
  protected ProcessGroupByIdentityInterpreterHelper getHelper() {
    return helper;
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
  protected ProcessDistributedByInterpreterFacadeOS getDistributedByInterpreter() {
    return distributedByInterpreter;
  }

  @Override
  protected ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return viewInterpreter;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_CANDIDATE_GROUP);
  }
}
