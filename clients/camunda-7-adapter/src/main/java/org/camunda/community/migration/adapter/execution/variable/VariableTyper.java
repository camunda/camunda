package org.camunda.community.migration.adapter.execution.variable;

import static java.util.Optional.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.camunda.community.migration.adapter.execution.variable.VariableTypingRule.VariableTypingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VariableTyper {
  private static final Logger LOG = LoggerFactory.getLogger(VariableTyper.class);
  private final Set<VariableTypingRule> rules;

  @Autowired
  public VariableTyper(Set<VariableTypingRule> rules) {
    this.rules = rules;
  }

  public Map<String, Object> typeVariables(String bpmnProcessId, Map<String, Object> variables) {
    Map<String, Object> result = new HashMap<>();
    variables.forEach(
        (variableName, variableValue) -> {
          LOG.debug("Handling variable {} of process {}", variableName, bpmnProcessId);
          DefaultVariableTypingContext context =
              new DefaultVariableTypingContext(bpmnProcessId, variableName, variableValue);
          rules.forEach(rule -> rule.handle(context));
          result.put(
              context.getVariableName(),
              ofNullable(context.typedVariableValue).orElse(context.variableValue));
        });
    return result;
  }

  public static class DefaultVariableTypingContext implements VariableTypingContext {
    private final String bpmnProcessId;
    private final String variableName;
    private final Object variableValue;
    private Object typedVariableValue;

    public DefaultVariableTypingContext(
        String bpmnProcessId, String variableName, Object variableValue) {
      this.bpmnProcessId = bpmnProcessId;
      this.variableName = variableName;
      this.variableValue = variableValue;
    }

    @Override
    public String getBpmnProcessId() {
      return bpmnProcessId;
    }

    @Override
    public String getVariableName() {
      return variableName;
    }

    @Override
    public Object getVariableValue() {
      return variableValue;
    }

    @Override
    public void setVariableValue(Object variableValue) {
      if (typedVariableValue == null) {
        typedVariableValue = variableValue;
      } else {
        throw new IllegalStateException(
            String.format(
                "Multiple typings detected for variable %s on process %s",
                variableName, bpmnProcessId));
      }
    }
  }
}
