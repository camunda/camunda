package org.camunda.community.migration.adapter.execution;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.impl.core.variable.CoreVariableInstance;
import org.camunda.bpm.engine.impl.core.variable.scope.*;

/**
 * Simple VariableScope implementation that can be initialized with a Map and provides all variable
 * methods required for implementing a DelegateExecution.
 *
 * @author Falko Menge (Camunda)
 */
public class SimpleVariableScope extends AbstractVariableScope {

  private static final long serialVersionUID = 1L;

  protected VariableInstanceFactory<CoreVariableInstance> variableInstanceFactory =
      (name, value, isTransient) -> new SimpleVariableInstance(name, value);
  protected VariableStore<CoreVariableInstance> variableStore = new VariableStore<>();

  public SimpleVariableScope(Map<String, ?> variables) {
    super();
    setVariables(variables);
  }

  @Override
  protected VariableStore<CoreVariableInstance> getVariableStore() {
    return variableStore;
  }

  @Override
  protected VariableInstanceFactory<CoreVariableInstance> getVariableInstanceFactory() {
    return variableInstanceFactory;
  }

  @Override
  protected List<VariableInstanceLifecycleListener<CoreVariableInstance>>
      getVariableInstanceLifecycleListeners() {
    return Collections.emptyList();
  }

  @Override
  public AbstractVariableScope getParentVariableScope() {
    return null;
  }
}
