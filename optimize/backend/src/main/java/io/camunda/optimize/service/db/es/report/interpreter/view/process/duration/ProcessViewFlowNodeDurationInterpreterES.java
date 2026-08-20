/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process.duration;

import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_FLOW_NODE_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;

import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessViewFlowNodeDurationInterpreterES
    extends AbstractProcessViewDurationInterpreterES {
  @Override
  public Set<ProcessView> getSupportedViews() {
    return Set.of(PROCESS_VIEW_FLOW_NODE_DURATION);
  }

  @Override
  protected String getReferenceDateFieldName() {
    return FLOW_NODE_INSTANCES + "." + FLOW_NODE_START_DATE;
  }

  @Override
  protected String getDurationFieldName() {
    return FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION;
  }
}
