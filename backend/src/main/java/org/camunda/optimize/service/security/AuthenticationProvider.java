package org.camunda.optimize.service.security;

/**
 * @author Askar Akhmerov
 */
public interface AuthenticationProvider {

  boolean authenticate(String username, String password);
}
