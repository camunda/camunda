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
