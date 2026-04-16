/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.protocol.model.AdHocSubProcessActivateActivitiesInstruction;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AdHocSubProcessActivityServices;
import io.camunda.zeebe.gateway.rest.controller.generated.AdHocSubProcessServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultAdHocSubProcessServiceAdapter implements AdHocSubProcessServiceAdapter {

  private final AdHocSubProcessActivityServices adHocSubProcessActivityServices;

  public DefaultAdHocSubProcessServiceAdapter(
      final AdHocSubProcessActivityServices adHocSubProcessActivityServices) {
    this.adHocSubProcessActivityServices = adHocSubProcessActivityServices;
  }

  @Override
  public ResponseEntity<Void> activateAdHocSubProcessActivities(
      final Long adHocSubProcessInstanceKey,
      final AdHocSubProcessActivateActivitiesInstruction requestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toAdHocSubProcessActivateActivitiesRequest(
            String.valueOf(adHocSubProcessInstanceKey), requestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mapped ->
                RequestExecutor.executeSync(
                    () ->
                        adHocSubProcessActivityServices.activateActivities(
                            mapped, authentication)));
  }
}
