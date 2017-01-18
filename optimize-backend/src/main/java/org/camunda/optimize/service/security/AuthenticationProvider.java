package org.camunda.optimize.service.security;

import org.camunda.optimize.service.exceptions.UnauthorizedUserException;

/**
 * @author Askar Akhmerov
 */
public interface AuthenticationProvider {

  void authenticate(String username, String password) throws UnauthorizedUserException;
}
