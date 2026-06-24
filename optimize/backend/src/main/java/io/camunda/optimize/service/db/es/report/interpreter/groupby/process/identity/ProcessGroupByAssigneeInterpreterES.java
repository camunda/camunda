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
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.report.interpreter.groupby.process.identity.ProcessGroupByIdentityInterpreterHelper;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessGroupByAssigneeInterpreterES
    extends AbstractProcessGroupByIdentityInterpreterES {
  private final ConfigurationService configurationService;
  private final DefinitionService definitionService;
  private final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  private final ProcessViewInterpreterFacadeES viewInterpreter;
  private final ProcessGroupByIdentityInterpreterHelper helper;

  public ProcessGroupByAssigneeInterpreterES(
      final ConfigurationService configurationService,
      final DefinitionService definitionService,
      final ProcessDistributedByInterpreterFacadeES distributedByInterpreter,
      final ProcessViewInterpreterFacadeES viewInterpreter,
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
    return USER_TASK_ASSIGNEE;
  }

  @Override
  protected IdentityType getIdentityType() {
    return IdentityType.USER;
  }

  @Override
  protected ProcessDistributedByInterpreterFacadeES getDistributedByInterpreter() {
    return distributedByInterpreter;
  }

  @Override
  protected ProcessViewInterpreterFacadeES getViewInterpreter() {
    return viewInterpreter;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_ASSIGNEE);
  }
}
