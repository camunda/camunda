/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.adapter.execution.variable;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.camunda.community.migration.adapter.VariableDto;
import org.camunda.community.migration.adapter.execution.variable.GlobalVariableTypingRule.SimpleGlobalVariableTypingRule;
import org.camunda.community.migration.adapter.execution.variable.MultiProcessVariableTypingRule.SimpleMultiProcessVariableTypingRule;
import org.camunda.community.migration.adapter.execution.variable.SingleProcessVariableTypingRule.SimpleSingleProcessVariableTypingRule;
import org.junit.jupiter.api.Test;

public class VariableTypingRuleTest {

  private static Map<String, Object> testVariables(final String value) {
    final Map<String, Object> variables = new HashMap<>();
    variables.put("varName", rawVariableDto(value));
    variables.put("varName2", "123");
    return variables;
  }

  private static Map<String, Object> rawVariableDto(final String value) {
    final Map<String, Object> variableDto = new HashMap<>();
    variableDto.put("value", value);
    return variableDto;
  }

  @Test
  void shouldHandleGlobalVariableTyping() {
    final String value = "abc";
    final VariableTypingRule globalRule =
        new SimpleGlobalVariableTypingRule("varName", new ObjectMapper(), VariableDto.class);
    final VariableTyper variableTyper = new VariableTyper(Collections.singleton(globalRule));
    final Map<String, Object> typedVariables =
        variableTyper.typeVariables("any", testVariables(value));
    assertThat(typedVariables)
        .containsKey("varName")
        .extracting(v -> v.get("varName"))
        .isInstanceOf(VariableDto.class)
        .extracting("value")
        .isEqualTo(value);
  }

  @Test
  void shouldHandleMultiProcessVariableTyping() {
    final String value = "1d3";
    final Set<String> bpmnProcessIds = new HashSet<>();
    bpmnProcessIds.add("foo");
    bpmnProcessIds.add("bar");
    final VariableTypingRule multiProcessRule =
        new SimpleMultiProcessVariableTypingRule(
            bpmnProcessIds, "varName", new ObjectMapper(), VariableDto.class);
    final VariableTyper variableTyper = new VariableTyper(Collections.singleton(multiProcessRule));
    final Map<String, Object> anyTypedVariables =
        variableTyper.typeVariables("any", testVariables(value));
    assertThat(anyTypedVariables)
        .containsKey("varName")
        .extracting(v -> v.get("varName"))
        .isInstanceOf(Map.class);
    final Map<String, Object> fooTypedVariables =
        variableTyper.typeVariables("foo", testVariables(value));
    assertThat(fooTypedVariables)
        .containsKey("varName")
        .extracting(v -> v.get("varName"))
        .isInstanceOf(VariableDto.class);
  }

  @Test
  void shouldHandleSingleProcessVariableTyping() {
    final String value = "1d3";
    final VariableTypingRule multiProcessRule =
        new SimpleSingleProcessVariableTypingRule(
            "foo", "varName", new ObjectMapper(), VariableDto.class);
    final VariableTyper variableTyper = new VariableTyper(Collections.singleton(multiProcessRule));
    final Map<String, Object> anyTypedVariables =
        variableTyper.typeVariables("any", testVariables(value));
    assertThat(anyTypedVariables)
        .containsKey("varName")
        .extracting(v -> v.get("varName"))
        .isInstanceOf(Map.class);
    final Map<String, Object> fooTypedVariables =
        variableTyper.typeVariables("foo", testVariables(value));
    assertThat(fooTypedVariables)
        .containsKey("varName")
        .extracting(v -> v.get("varName"))
        .isInstanceOf(VariableDto.class);
  }
}
