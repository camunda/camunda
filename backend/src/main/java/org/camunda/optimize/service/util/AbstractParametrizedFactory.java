package org.camunda.optimize.service.util;

import java.util.HashMap;
import java.util.Map;

/**
 * HashMap based implementation of parametrized factory with single instance
 * created per parameter value.
 *
 * @author Askar Akhmerov
 */
public abstract class AbstractParametrizedFactory<INSTANCE_TYPE extends Object, PARAMETER_TYPE extends Object>
    implements ParametrizedFactory<INSTANCE_TYPE, PARAMETER_TYPE> {

  protected Map<PARAMETER_TYPE, INSTANCE_TYPE> instances = new HashMap<>();

  @Override
  public INSTANCE_TYPE getInstance(PARAMETER_TYPE parameterValue) {
    if (!instances.containsKey(parameterValue)) {
      instances.put(parameterValue, newInstance(parameterValue));
    }
    return instances.get(parameterValue);
  }

  protected abstract INSTANCE_TYPE newInstance(PARAMETER_TYPE parameterValue);

  public Map<PARAMETER_TYPE, INSTANCE_TYPE> getInstances() {
    return  this.instances;
  }
}
