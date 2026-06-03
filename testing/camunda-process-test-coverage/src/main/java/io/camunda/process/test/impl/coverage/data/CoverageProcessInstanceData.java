/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.coverage.data;

import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.ProcessInstanceSequenceFlow;
import java.util.List;
import org.immutables.value.Value;

/**
 * Coverage input data for a single process instance execution.
 *
 * <p>Contains the process instance together with visited element instances and traversed sequence
 * flows.
 */
@Value.Immutable
public interface CoverageProcessInstanceData {

  /** Returns the process instance metadata. */
  ProcessInstance getProcessInstance();

  /** Returns element instances visited during execution of the process instance. */
  List<ElementInstance> getElementInstances();

  /** Returns sequence flows traversed during execution of the process instance. */
  List<ProcessInstanceSequenceFlow> getSequenceFlows();
}
