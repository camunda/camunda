/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface AgentDefinitionReader {

  /**
   * Returns the agent definitions of the given processes, each read only on the tenants given for
   * its process definition key. A {@code null} tenant id stands for definitions without a tenant.
   */
  List<AgentDefinitionDto> getAgentDefinitions(
      Map<String, Set<String>> tenantIdsByProcessDefinitionKey);
}
