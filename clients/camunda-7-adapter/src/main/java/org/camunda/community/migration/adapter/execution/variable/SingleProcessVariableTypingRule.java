package org.camunda.community.migration.adapter.execution.variable;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Set;

public abstract class SingleProcessVariableTypingRule extends MultiProcessVariableTypingRule {
  @Override
  protected Set<String> bpmnProcessIds() {
    return Collections.singleton(bpmnProcessId());
  }

  protected abstract String bpmnProcessId();

  public static class SimpleSingleProcessVariableTypingRule
      extends SingleProcessVariableTypingRule {
    private final String bpmnProcessId;
    private final String variableName;
    private final ObjectMapper objectMapper;
    private final Class<?> targetType;

    public SimpleSingleProcessVariableTypingRule(
        String bpmnProcessId, String variableName, ObjectMapper objectMapper, Class<?> targetType) {
      this.bpmnProcessId = bpmnProcessId;
      this.variableName = variableName;
      this.objectMapper = objectMapper;
      this.targetType = targetType;
    }

    @Override
    protected Class<?> targetType() {
      return targetType;
    }

    @Override
    protected ObjectMapper objectMapper() {
      return objectMapper;
    }

    @Override
    protected String variableName() {
      return variableName;
    }

    @Override
    protected String bpmnProcessId() {
      return bpmnProcessId;
    }
  }
}
