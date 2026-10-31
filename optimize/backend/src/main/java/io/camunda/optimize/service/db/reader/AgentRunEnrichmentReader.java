/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Reads what agent run enrichment needs: the runs and their process instances. */
public interface AgentRunEnrichmentReader {

  /** Real-time read, so runs written by the same import job are visible. */
  List<AgentRunDto> getRunsByIds(Collection<String> agentInstanceIds);

  /** Search-based, so runs written within the last refresh interval may be missing. */
  List<AgentRunDto> getRunsByProcessInstanceIds(Collection<String> processInstanceIds);

  /**
   * Real-time read of process instances by ID, given each instance's process definition key.
   * Instances that don't exist yet are left out.
   */
  Map<String, ProcessInstanceDto> getProcessInstances(
      Map<String, String> processDefinitionKeyByProcessInstanceId);

  /**
   * Search-based lookup of the process definition key of each given process instance, for root
   * process instances whose definition isn't known. Instances not found are left out.
   */
  Map<String, String> getProcessDefinitionKeys(Collection<String> processInstanceIds);
}
