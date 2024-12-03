/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.util;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentUtil {

  @Autowired private Environment environment;

  public boolean isTestProfileEnabled() {
    return Arrays.asList(environment.getActiveProfiles()).stream()
        .anyMatch(profile -> profile.equals("test") || profile.equals("e2e-test"));
  }
}
