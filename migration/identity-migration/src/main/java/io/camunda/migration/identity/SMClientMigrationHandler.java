/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import io.camunda.migration.identity.console.ConsoleClient;
import io.camunda.migration.identity.dto.Client;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(ConsoleClient.class)
public class SMClientMigrationHandler extends ClientMigrationHandler {

  @Override
  protected List<Client> fetchBatch() {
    return List.of();
  }

  @Override
  protected void process(final List<Client> batch) {}
}
