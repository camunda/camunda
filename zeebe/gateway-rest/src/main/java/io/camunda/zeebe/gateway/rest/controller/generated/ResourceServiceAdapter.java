/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDeleteResourceRequestStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import jakarta.annotation.Generated;
import jakarta.servlet.http.Part;
import java.util.List;
import org.springframework.http.ResponseEntity;

/**
 * Service adapter for Resource operations. Implements request mapping, service delegation, and
 * response construction.
 */
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public interface ResourceServiceAdapter {

  ResponseEntity<Object> createDeployment(
      List<Part> resources, String tenantId, CamundaAuthentication authentication);

  ResponseEntity<Object> getResource(Long resourceKey, CamundaAuthentication authentication);

  ResponseEntity<Void> getResourceContent(Long resourceKey, CamundaAuthentication authentication);

  ResponseEntity<Object> deleteResource(
      Long resourceKey,
      GeneratedDeleteResourceRequestStrictContract deleteResourceRequest,
      CamundaAuthentication authentication);
}
