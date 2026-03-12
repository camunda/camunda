/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.protocol.model.CamundaUserResult;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedCamundaUserResultMapper {

  private GeneratedCamundaUserResultMapper() {}

  public static CamundaUserResult toProtocol(final GeneratedCamundaUserStrictContract source) {
    return new CamundaUserResult()
        .username(source.username())
        .displayName(source.displayName())
        .email(source.email())
        .authorizedComponents(source.authorizedComponents())
        .tenants(
            source.tenants() == null
                ? null
                : source.tenants().stream().map(GeneratedTenantResultMapper::toProtocol).toList())
        .groups(source.groups())
        .roles(source.roles())
        .salesPlanType(source.salesPlanType())
        .c8Links(source.c8Links())
        .canLogout(source.canLogout());
  }
}
