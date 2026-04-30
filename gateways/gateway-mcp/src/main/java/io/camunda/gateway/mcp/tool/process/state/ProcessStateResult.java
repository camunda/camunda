/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.process.state;

import io.camunda.gateway.protocol.model.ElementInstanceResult;
import io.camunda.gateway.protocol.model.ProcessInstanceResult;
import io.camunda.gateway.protocol.model.VariableSearchResult;
import java.util.List;

/**
 * Combined result for {@link ProcessStateTools#getProcessState}. Aggregates the process instance
 * state, its current variables, and active element instances into a single response.
 */
public record ProcessStateResult(
    ProcessInstanceResult processInstance,
    List<VariableSearchResult> variables,
    List<ElementInstanceResult> activeElementInstances) {}
