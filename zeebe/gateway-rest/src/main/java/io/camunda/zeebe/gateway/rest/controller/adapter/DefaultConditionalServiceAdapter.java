/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.protocol.model.ConditionalEvaluationInstruction;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ConditionalServices;
import io.camunda.zeebe.gateway.rest.controller.generated.ConditionalServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultConditionalServiceAdapter implements ConditionalServiceAdapter {

  private final ConditionalServices conditionalServices;
  private final MultiTenancyConfiguration multiTenancyCfg;

  public DefaultConditionalServiceAdapter(
      final ConditionalServices conditionalServices,
      final MultiTenancyConfiguration multiTenancyCfg) {
    this.conditionalServices = conditionalServices;
    this.multiTenancyCfg = multiTenancyCfg;
  }

  @Override
  public ResponseEntity<Object> evaluateConditionals(
      final ConditionalEvaluationInstruction requestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toEvaluateConditionalRequest(
            requestStrict, multiTenancyCfg.isChecksEnabled())
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mapped ->
                RequestExecutor.executeSync(
                    () -> conditionalServices.evaluateConditional(mapped, authentication),
                    ResponseMapper::toConditionalEvaluationResponse,
                    HttpStatus.OK));
  }
}
