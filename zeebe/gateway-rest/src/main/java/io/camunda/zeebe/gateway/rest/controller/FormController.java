/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.FormResult;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.FormServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequiresSecondaryStorage
@RequestMapping("/v2/forms")
public class FormController {

  private final FormServices formServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public FormController(
      final FormServices formServices, final CamundaAuthenticationProvider authenticationProvider) {
    this.formServices = formServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaGetMapping(path = "/{formKey}")
  public ResponseEntity<FormResult> getByKey(@PathVariable("formKey") final long formKey) {
    try {
      final var form =
          formServices.getByKey(formKey, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(SearchQueryResponseMapper.toFormItem(form));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
