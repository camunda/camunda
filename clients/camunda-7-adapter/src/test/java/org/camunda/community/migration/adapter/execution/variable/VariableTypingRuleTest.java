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

  private static Map<String, Object> testVariables(String value) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("varName", rawVariableDto(value));
    variables.put("varName2", "123");
    return variables;
  }

  private static Map<String, Object> rawVariableDto(String value) {
    Map<String, Object> variableDto = new HashMap<>();
    variableDto.put("value", value);
    return variableDto;
  }

  @Test
  void shouldHandleGlobalVariableTyping() {
    String value = "abc";
    VariableTypingRule globalRule =
        new SimpleGlobalVariableTypingRule("varName", new ObjectMapper(), VariableDto.class);
    VariableTyper variableTyper = new VariableTyper(Collections.singleton(globalRule));
    Map<String, Object> typedVariables = variableTyper.typeVariables("any", testVariables(value));
    assertThat(typedVariables)
        .containsKey("varName")
        .extracting(v -> v.get("varName"))
        .isInstanceOf(VariableDto.class)
        .extracting("value")
        .isEqualTo(value);
  }

  @Test
  void shouldHandleMultiProcessVariableTyping() {
    String value = "1d3";
    Set<String> bpmnProcessIds = new HashSet<>();
    bpmnProcessIds.add("foo");
    bpmnProcessIds.add("bar");
    VariableTypingRule multiProcessRule =
        new SimpleMultiProcessVariableTypingRule(
            bpmnProcessIds, "varName", new ObjectMapper(), VariableDto.class);
    VariableTyper variableTyper = new VariableTyper(Collections.singleton(multiProcessRule));
    Map<String, Object> anyTypedVariables =
        variableTyper.typeVariables("any", testVariables(value));
    assertThat(anyTypedVariables)
        .containsKey("varName")
        .extracting(v -> v.get("varName"))
        .isInstanceOf(Map.class);
    Map<String, Object> fooTypedVariables =
        variableTyper.typeVariables("foo", testVariables(value));
    assertThat(fooTypedVariables)
        .containsKey("varName")
        .extracting(v -> v.get("varName"))
        .isInstanceOf(VariableDto.class);
  }

  @Test
  void shouldHandleSingleProcessVariableTyping() {
    String value = "1d3";
    VariableTypingRule multiProcessRule =
        new SimpleSingleProcessVariableTypingRule(
            "foo", "varName", new ObjectMapper(), VariableDto.class);
    VariableTyper variableTyper = new VariableTyper(Collections.singleton(multiProcessRule));
    Map<String, Object> anyTypedVariables =
        variableTyper.typeVariables("any", testVariables(value));
    assertThat(anyTypedVariables)
        .containsKey("varName")
        .extracting(v -> v.get("varName"))
        .isInstanceOf(Map.class);
    Map<String, Object> fooTypedVariables =
        variableTyper.typeVariables("foo", testVariables(value));
    assertThat(fooTypedVariables)
        .containsKey("varName")
        .extracting(v -> v.get("varName"))
        .isInstanceOf(VariableDto.class);
  }
}
