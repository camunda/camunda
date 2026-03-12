/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.ActivatedJobResult;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedActivatedJobResultMapper {

  private GeneratedActivatedJobResultMapper() {}

  public static ActivatedJobResult toProtocol(final GeneratedActivatedJobStrictContract source) {
    return new ActivatedJobResult()
        .type(source.type())
        .processDefinitionId(source.processDefinitionId())
        .processDefinitionVersion(source.processDefinitionVersion())
        .elementId(source.elementId())
        .customHeaders(source.customHeaders())
        .worker(source.worker())
        .retries(source.retries())
        .deadline(source.deadline())
        .variables(source.variables())
        .tenantId(source.tenantId())
        .jobKey(source.jobKey())
        .processInstanceKey(source.processInstanceKey())
        .processDefinitionKey(source.processDefinitionKey())
        .elementInstanceKey(source.elementInstanceKey())
        .kind(source.kind())
        .listenerEventType(source.listenerEventType())
        .userTask(
            source.userTask() == null
                ? null
                : GeneratedUserTaskPropertiesMapper.toProtocol(source.userTask()))
        .tags(source.tags())
        .rootProcessInstanceKey(source.rootProcessInstanceKey());
  }
}
