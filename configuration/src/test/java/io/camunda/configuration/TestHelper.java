/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableConfigurationProperties(Camunda.class)
@Import(UnifiedConfiguration.class)
public class TestHelper {

  public static final String PROPERTIES_MUST_BE_REMOVED_SIGNATURE =
      "The following legacy properties must be removed";

  public static final String LEGACY_PROPERTIES_SAME_VALUE_SIGNATURE =
      "The legacy configuration properties must have the same value";

  public static final String LEGACY_NOT_ALLOWED_SIGNATURE =
      "The following legacy properties are no longer supported";

  public static final String PROPERTY_MUST_BE_SET_SIGNATURE = "must be set.";
}
