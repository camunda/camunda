/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.hub.fanout;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Receives whether Hub manages a physical tenant's policies. PoC: only logs; later this switches
 * the Admin UI and the identity write APIs of that physical tenant to read-only.
 */
@Component
@ConditionalOnProperty(prefix = "camunda.hub.fanout", name = "enabled", havingValue = "true")
public class ManagementStatusHandler implements FanoutEventHandler {

  public static final String TYPE = "io.camunda.hub.management-status.v1";

  private static final Logger LOGGER = LoggerFactory.getLogger(ManagementStatusHandler.class);

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public void handle(final String physicalTenantId, final JsonNode data) {
    final var managed = data.required("managed").asBoolean();
    LOGGER.info(
        "[pt={}] Hub management status: managed={} -> Admin UI {}",
        physicalTenantId,
        managed,
        managed ? "read-only" : "editable");
  }
}
