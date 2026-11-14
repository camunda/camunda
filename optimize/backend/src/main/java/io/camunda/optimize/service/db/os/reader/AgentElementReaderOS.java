/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import io.camunda.optimize.service.db.reader.AgentElementReader;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

// prototype: ES only
@Component
@Conditional(OpenSearchCondition.class)
public class AgentElementReaderOS implements AgentElementReader {

  @Override
  public List<String> getAgentFlowNodeIds(final Collection<String> processDefinitionKeys) {
    throw new UnsupportedOperationException("Agent elements are not supported on OpenSearch");
  }
}
