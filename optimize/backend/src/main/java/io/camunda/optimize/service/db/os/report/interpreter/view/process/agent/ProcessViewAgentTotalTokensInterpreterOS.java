/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.process.agent;

import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_TOTAL_TOKENS;

import io.camunda.optimize.service.db.os.writer.OpenSearchWriterUtil;
import io.camunda.optimize.service.db.report.interpreter.view.process.agent.AgentMetricScripts;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Set;
import org.opensearch.client.opensearch._types.Script;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessViewAgentTotalTokensInterpreterOS
    extends AbstractProcessViewAgentMetricInterpreterOS {

  @Override
  public Set<ProcessView> getSupportedViews() {
    return Set.of(PROCESS_VIEW_AGENT_TOTAL_TOKENS);
  }

  @Override
  protected Script getAggregationScript() {
    return OpenSearchWriterUtil.createDefaultScript(AgentMetricScripts.TOTAL_TOKENS);
  }
}
