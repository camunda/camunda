/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.community.migration.adapter.execution.variable.SingleProcessVariableTypingRule.SimpleSingleProcessVariableTypingRule;
import org.camunda.community.migration.adapter.execution.variable.VariableTypingRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VariableTypingRuleConfiguration {

  @Bean
  public VariableTypingRule variableDtoTypingRule() {
    return new SimpleSingleProcessVariableTypingRule(
        "test", "someVariable", new ObjectMapper(), VariableDto.class);
  }
}
