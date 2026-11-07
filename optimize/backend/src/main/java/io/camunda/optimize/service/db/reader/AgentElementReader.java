/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import java.util.Collection;
import java.util.List;

/** Reads the agent elements of processes from the agent-definition index. */
public interface AgentElementReader {

  /** The BPMN element IDs of every agent of the given processes, in any tenant. */
  List<String> getAgentFlowNodeIds(Collection<String> processDefinitionKeys);
}
