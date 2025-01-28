package org.camunda.community.migration.adapter.execution.variable;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MultiProcessVariableTypingRule extends GlobalVariableTypingRule {
  private static final Logger LOG = LoggerFactory.getLogger(MultiProcessVariableTypingRule.class);

  @Override
  protected boolean contextMatches(VariableTypingContext context) {
    return super.contextMatches(context) && bpmnProcessIdMatches(context);
  }

  private boolean bpmnProcessIdMatches(VariableTypingContext context) {
    return bpmnProcessIds().contains(context.getBpmnProcessId());
  }

  protected abstract Set<String> bpmnProcessIds();

  public static class SimpleMultiProcessVariableTypingRule extends MultiProcessVariableTypingRule {
    private final Set<String> bpmnProcessIds;
    private final String variableName;
    private final ObjectMapper objectMapper;
    private final Class<?> targetType;

    public SimpleMultiProcessVariableTypingRule(
        Set<String> bpmnProcessIds,
        String variableName,
        ObjectMapper objectMapper,
        Class<?> targetType) {
      this.bpmnProcessIds = bpmnProcessIds;
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
    protected Set<String> bpmnProcessIds() {
      return bpmnProcessIds;
    }
  }
}
