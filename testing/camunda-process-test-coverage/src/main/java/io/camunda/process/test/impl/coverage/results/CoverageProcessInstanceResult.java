/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.coverage.results;

import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.ProcessInstanceSequenceFlow;
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
public interface CoverageProcessInstanceResult {

  ProcessInstance getProcessInstance();

  List<ElementInstance> getElementInstances();

  List<ProcessInstanceSequenceFlow> getSequenceFlows();
}
