/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import io.camunda.optimize.dto.optimize.rest.agentic.AgentInventoryDto.AgentInventoryEntry;
import io.camunda.optimize.service.db.reader.AgentInventoryReader;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

// prototype: ES only
@Component
@Conditional(OpenSearchCondition.class)
public class AgentInventoryReaderOS implements AgentInventoryReader {

  @Override
  public List<AgentInventoryEntry> getAgentInventory(final OffsetDateTime endedFrom) {
    throw new UnsupportedOperationException("The agent inventory is not supported on OpenSearch");
  }
}
