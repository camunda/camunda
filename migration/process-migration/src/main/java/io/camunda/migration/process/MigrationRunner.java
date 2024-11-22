/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.process;

import io.camunda.migration.api.Migrator;
import org.springframework.stereotype.Component;

@Component("process-migrator")
public class MigrationRunner implements Migrator {

  @Override
  public void run() {}
}
