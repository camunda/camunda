/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.service.db.reader.AgentRunEnrichmentReader;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

// prototype: ES only
@Component
@Conditional(OpenSearchCondition.class)
public class AgentRunEnrichmentReaderOS implements AgentRunEnrichmentReader {

  @Override
  public List<AgentRunDto> getRunsByIds(final Collection<String> agentInstanceIds) {
    throw new UnsupportedOperationException("Agent run enrichment is not supported on OpenSearch");
  }

  @Override
  public List<AgentRunDto> getRunsByProcessInstanceIds(
      final Collection<String> processInstanceIds) {
    throw new UnsupportedOperationException("Agent run enrichment is not supported on OpenSearch");
  }

  @Override
  public Map<String, ProcessInstanceDto> getProcessInstances(
      final Map<String, String> processDefinitionKeyByProcessInstanceId) {
    throw new UnsupportedOperationException("Agent run enrichment is not supported on OpenSearch");
  }
}
