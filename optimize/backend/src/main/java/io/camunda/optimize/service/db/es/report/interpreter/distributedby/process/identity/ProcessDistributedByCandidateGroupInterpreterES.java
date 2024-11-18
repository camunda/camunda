/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.identity;

import static io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy.PROCESS_DISTRIBUTED_BY_CANDIDATE_GROUP;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUPS;

import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.report.interpreter.distributedby.process.identity.ProcessDistributedByIdentityInterpreterHelper;
import io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessDistributedByCandidateGroupInterpreterES
    extends AbstractProcessDistributedByIdentityInterpreterES {
  private final ProcessViewInterpreterFacadeES viewInterpreter;
  private final ConfigurationService configurationService;
  private final DefinitionService definitionService;
  private final ProcessDistributedByIdentityInterpreterHelper helper;

  public ProcessDistributedByCandidateGroupInterpreterES(
      final ProcessViewInterpreterFacadeES viewInterpreter,
      final ConfigurationService configurationService,
      final DefinitionService definitionService,
      final ProcessDistributedByIdentityInterpreterHelper helper) {
    super();
    this.viewInterpreter = viewInterpreter;
    this.configurationService = configurationService;
    this.definitionService = definitionService;
    this.helper = helper;
  }

  @Override
  protected ProcessViewInterpreterFacadeES getViewInterpreter() {
    return viewInterpreter;
  }

  @Override
  protected ConfigurationService getConfigurationService() {
    return configurationService;
  }

  @Override
  protected ProcessDistributedByIdentityInterpreterHelper getHelper() {
    return helper;
  }

  @Override
  protected DefinitionService getDefinitionService() {
    return definitionService;
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
  public Set<ProcessDistributedBy> getSupportedDistributedBys() {
    return Set.of(PROCESS_DISTRIBUTED_BY_CANDIDATE_GROUP);
  }
}
