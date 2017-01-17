package org.camunda.optimize.service.security;

import org.camunda.optimize.service.exceptions.UnauthorizedUserException;
import org.glassfish.jersey.spi.Contract;

/**
 * @author Askar Akhmerov
 */
public interface AuthenticationProvider {

  void authenticate(String username, String password) throws UnauthorizedUserException;
}
