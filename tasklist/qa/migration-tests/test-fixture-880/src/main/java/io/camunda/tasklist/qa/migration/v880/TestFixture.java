/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.migration.v880;

import io.camunda.tasklist.qa.util.migration.AbstractTestFixture;
import org.springframework.stereotype.Component;

@Component
public class TestFixture extends AbstractTestFixture {

  public static final String VERSION = "SNAPSHOT";

  @Override
  public String getVersion() {
    return VERSION;
  }
}
