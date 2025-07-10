/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.TestTasklistPropertiesOverride.TestTasklistProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@EnableConfigurationProperties(TestTasklistProperties.class)
@PropertySource("classpath:tasklist-version.properties")
public class TestTasklistPropertiesOverride {

  @ConfigurationProperties(TasklistProperties.PREFIX)
  public static class TestTasklistProperties extends TasklistProperties {}
}
