package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.optimize.CredentialsDto;

/**
 * @author Askar Akhmerov
 */
public interface AuthenticationProvider {

  boolean authenticate(CredentialsDto credentialsDto);
}
