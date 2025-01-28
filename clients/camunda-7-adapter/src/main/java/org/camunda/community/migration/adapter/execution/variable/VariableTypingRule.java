package org.camunda.community.migration.adapter.execution.variable;

public interface VariableTypingRule {
  void handle(VariableTypingContext context);

  interface VariableTypingContext {
    String getBpmnProcessId();

    String getVariableName();

    Object getVariableValue();

    void setVariableValue(Object variableValue);
  }
}
