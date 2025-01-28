package org.camunda.community.migration.adapter.execution.variable;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractVariableTypingRule implements VariableTypingRule {
  private final Logger LOG = LoggerFactory.getLogger(getClass());

  @Override
  public final void handle(VariableTypingContext context) {
    if (contextMatches(context)) {
      LOG.debug(
          "Converting variable {} of process {} from {} to {}",
          context.getVariableName(),
          context.getBpmnProcessId(),
          context.getVariableValue().getClass(),
          targetType(context));
      Object newVariableValue =
          objectMapper(context).convertValue(context.getVariableValue(), targetType(context));
      context.setVariableValue(newVariableValue);
    }
  }

  protected abstract boolean contextMatches(VariableTypingContext context);

  protected Class<?> targetType(VariableTypingContext context) {
    return targetType();
  }

  protected abstract Class<?> targetType();

  protected ObjectMapper objectMapper(VariableTypingContext context) {
    return objectMapper();
  }

  protected abstract ObjectMapper objectMapper();
}
