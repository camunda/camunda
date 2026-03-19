/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMessageCorrelationRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMessagePublicationRequestStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;

/**
 * Service adapter for Message operations. Implements request mapping, service delegation, and
 * response construction.
 */
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public interface MessageServiceAdapter {

  ResponseEntity<Object> publishMessage(
      GeneratedMessagePublicationRequestStrictContract messagePublicationRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Object> correlateMessage(
      GeneratedMessageCorrelationRequestStrictContract messageCorrelationRequest,
      CamundaAuthentication authentication);
}
