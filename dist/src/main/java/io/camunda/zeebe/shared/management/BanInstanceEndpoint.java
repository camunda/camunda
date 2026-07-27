/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.zeebe.gateway.Loggers;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.Selector.Match;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
@WebEndpoint(id = "banning")
public final class BanInstanceEndpoint {
  private static final Logger LOG = Loggers.GATEWAY_LOGGER;
  final BanInstanceService banInstanceService;

  @Autowired
  public BanInstanceEndpoint(final BanInstanceService banInstanceService) {
    this.banInstanceService = banInstanceService;
  }

  /**
   * Bans the process instance whose key is given. Without a {@code physicalTenant} query parameter,
   * the request targets the default physical tenant: the given key encodes a bare partition id
   * which aliases across partition groups. With the parameter, the request targets the given
   * physical tenant's partition group instead.
   */
  @WriteOperation
  public WebEndpointResponse<?> post(
      @Selector(match = Match.SINGLE) final long processInstanceKey,
      @Nullable final String physicalTenant) {
    final var physicalTenantId =
        physicalTenant != null && !physicalTenant.isBlank()
            ? physicalTenant
            : DEFAULT_PHYSICAL_TENANT_ID;
    try {
      LOG.info(
          "Send AdminRequest to ban instance with key {} on physical tenant {}",
          processInstanceKey,
          physicalTenantId);
      banInstanceService.banInstance(processInstanceKey, physicalTenantId);
      return new WebEndpointResponse<>(WebEndpointResponse.STATUS_NO_CONTENT);
    } catch (final CompletionException e) {
      return new WebEndpointResponse<>(
          e.getCause(), WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    } catch (final Exception e) {
      return new WebEndpointResponse<>(e, WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    }
  }
}
