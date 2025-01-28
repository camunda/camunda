package org.camunda.community.migration.adapter.juel;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.VariableScope;
import org.camunda.bpm.engine.impl.el.JuelExpressionManager;
import org.camunda.bpm.impl.juel.jakarta.el.ELContext;
import org.camunda.bpm.impl.juel.jakarta.el.ExpressionFactory;
import org.camunda.bpm.impl.juel.jakarta.el.ValueExpression;
import org.camunda.community.migration.adapter.execution.SimpleVariableScope;
import org.springframework.stereotype.Component;

@Component
public class JuelExpressionResolver {

  private final JuelExpressionManager expressionManager;
  private final ExpressionFactory expressionFactory;
  private final ELContext elContext;

  public JuelExpressionResolver(
      JuelExpressionManager expressionManager,
      ExpressionFactory expressionFactory,
      ELContext elContext) {
    this.expressionManager = expressionManager;
    this.elContext = elContext;
    this.expressionFactory = expressionFactory;
  }

  public Object evaluate(String expressionString, DelegateExecution execution) {
    ValueExpression valueExpression =
        expressionFactory.createValueExpression(elContext, expressionString, Object.class);

    // required because (in C7) we can use juel like `${execution.xxx()}`
    VariableScope variableScope = new SimpleVariableScope(execution.getVariables());
    variableScope.setVariable("execution", execution);

    return new EnginelessJuelExpression(valueExpression, expressionManager, expressionString)
        .getValue(variableScope);
  }
}
