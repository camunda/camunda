/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionElementStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionInstanceStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionInstanceVersionStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionMessageSubscriptionStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionSearchQueryRequestStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;

/**
 * Service adapter for ProcessDefinition operations. Implements request mapping, service delegation,
 * and response construction.
 */
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public interface ProcessDefinitionServiceAdapter {

  ResponseEntity<Object> searchProcessDefinitions(
      GeneratedProcessDefinitionSearchQueryRequestStrictContract processDefinitionSearchQuery,
      CamundaAuthentication authentication);

  ResponseEntity<Object> getProcessDefinition(
      String processDefinitionKey, CamundaAuthentication authentication);

  ResponseEntity<Void> getProcessDefinitionXML(
      String processDefinitionKey, CamundaAuthentication authentication);

  ResponseEntity<Object> getStartProcessForm(
      String processDefinitionKey, CamundaAuthentication authentication);

  ResponseEntity<Object> getProcessDefinitionStatistics(
      String processDefinitionKey,
      GeneratedProcessDefinitionElementStatisticsQueryStrictContract
          processDefinitionElementStatisticsQuery,
      CamundaAuthentication authentication);

  ResponseEntity<Object> getProcessDefinitionMessageSubscriptionStatistics(
      GeneratedProcessDefinitionMessageSubscriptionStatisticsQueryStrictContract
          processDefinitionMessageSubscriptionStatisticsQuery,
      CamundaAuthentication authentication);

  ResponseEntity<Object> getProcessDefinitionInstanceStatistics(
      GeneratedProcessDefinitionInstanceStatisticsQueryStrictContract
          processDefinitionInstanceStatisticsQuery,
      CamundaAuthentication authentication);

  ResponseEntity<Object> getProcessDefinitionInstanceVersionStatistics(
      GeneratedProcessDefinitionInstanceVersionStatisticsQueryStrictContract
          processDefinitionInstanceVersionStatisticsQuery,
      CamundaAuthentication authentication);
}
