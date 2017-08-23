package org.camunda.optimize.service.util;

/**
 * Abstract interface for internal Optimize factories.
 *
 * @author Askar Akhmerov
 */
public interface Factory <T extends Object, P extends Object> {
  T getInstance(P parameter);
}
