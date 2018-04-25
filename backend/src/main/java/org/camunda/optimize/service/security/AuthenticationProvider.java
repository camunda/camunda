package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.optimize.query.user.CredentialsDto;

/**
 * @author Askar Akhmerov
 */
public interface AuthenticationProvider <T extends CredentialsDto> {

  boolean authenticate(T credentialsDto);

}
