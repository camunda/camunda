/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.JobSearchResult;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedJobSearchResultMapper {

  private GeneratedJobSearchResultMapper() {}

  public static JobSearchResult toProtocol(final GeneratedJobSearchStrictContract source) {
    return new JobSearchResult()
        .customHeaders(source.customHeaders())
        .deadline(source.deadline())
        .deniedReason(source.deniedReason())
        .elementId(source.elementId())
        .elementInstanceKey(source.elementInstanceKey())
        .endTime(source.endTime())
        .errorCode(source.errorCode())
        .errorMessage(source.errorMessage())
        .hasFailedWithRetriesLeft(source.hasFailedWithRetriesLeft())
        .isDenied(source.isDenied())
        .jobKey(source.jobKey())
        .kind(source.kind())
        .listenerEventType(source.listenerEventType())
        .processDefinitionId(source.processDefinitionId())
        .processDefinitionKey(source.processDefinitionKey())
        .processInstanceKey(source.processInstanceKey())
        .rootProcessInstanceKey(source.rootProcessInstanceKey())
        .retries(source.retries())
        .state(source.state())
        .tenantId(source.tenantId())
        .type(source.type())
        .worker(source.worker())
        .creationTime(source.creationTime())
        .lastUpdateTime(source.lastUpdateTime());
  }
}
