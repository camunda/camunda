/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.common;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCallActivity;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import org.agrona.DirectBuffer;

public interface CallActivityIndexProvider {

  /**
   * Provides the flow element identified by the element id in a process definition, identified by
   * the process definition key.
   *
   * @param processDefinitionKey the process definition where the element belongs to
   * @param tenantId the tenant in use
   * @param elementId the element id of the element
   * @param elementType the class of the element
   * @return the executable flow element
   * @param <T> the type that corresponds to the flow element
   */
  <T extends ExecutableFlowElement> T getFlowElement(
      long processDefinitionKey, String tenantId, DirectBuffer elementId, Class<T> elementType);

  /**
   * For given call activity, identified by element id, in corresponding process definition,
   * identified by process definition key, return the lexicographical index in that process
   * definition.
   *
   * <p>The tenant is used to make sure we have the right permission to see the definition.
   *
   * @param processDefinitionKey the process definition where the call activity belongs to
   * @param tenant the tenant in use
   * @param elementIdBuffer the element id of a call activity
   * @return the lexicographical index of a call activity in a process definition, null if not found
   */
  default Integer getLexicographicIndex(
      final long processDefinitionKey, final String tenant, final DirectBuffer elementIdBuffer) {
    final ExecutableCallActivity callActivityFlowElement =
        getCallActivityFlowElement(processDefinitionKey, tenant, elementIdBuffer);
    if (callActivityFlowElement != null) {
      return callActivityFlowElement.getLexicographicIndex();
    }
    return null;
  }

  default ExecutableCallActivity getCallActivityFlowElement(
      final long processDefinitionKey, final String tenantId, final DirectBuffer elementId) {
    return getFlowElement(processDefinitionKey, tenantId, elementId, ExecutableCallActivity.class);
  }
}
