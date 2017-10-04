package org.camunda.optimize.service.util;

/**
 * Abstract interface for internal Optimize factories.
 *
 * @author Askar Akhmerov
 */
public interface ParametrizedFactory<INSTANCE_TYPE extends Object, PARAMETER_TYPE extends Object> {
  INSTANCE_TYPE getInstance(PARAMETER_TYPE parameter);
}
