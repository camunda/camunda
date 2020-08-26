/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.security.ldap;

import org.camunda.operate.webapp.rest.dto.UserDto;
import org.camunda.operate.webapp.security.AbstractUserService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.ldap.userdetails.LdapUserDetails;
import org.springframework.stereotype.Component;

@Component
@Profile(LDAPWebSecurityConfig.LDAP_AUTH_PROFILE)
public class LDAPUserService extends AbstractUserService {

  @Override
  public UserDto getCurrentUser() {
    SecurityContext context = SecurityContextHolder.getContext();
    LdapUserDetails userDetails = (LdapUserDetails) context.getAuthentication().getPrincipal();
    return buildUserDtoFrom(userDetails);
  }

  private UserDto buildUserDtoFrom(LdapUserDetails ldapUserDetails) {
    return new UserDto()
        .setFirstname("")
        .setLastname(ldapUserDetails.getUsername())
        .setCanLogout(true);
  }
}
