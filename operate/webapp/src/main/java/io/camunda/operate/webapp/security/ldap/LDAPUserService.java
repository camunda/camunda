/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.ldap;

import static io.camunda.operate.OperateProfileService.LDAP_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.Permission.READ;
import static io.camunda.operate.webapp.security.Permission.WRITE;

import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.rest.exception.UserNotFoundException;
import io.camunda.operate.webapp.security.AbstractUserService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.ldap.userdetails.LdapUserDetails;
import org.springframework.stereotype.Component;

@Component
@Profile(LDAP_AUTH_PROFILE)
public class LDAPUserService extends AbstractUserService<Authentication> {

  private static final Logger LOGGER = LoggerFactory.getLogger(LDAPUserService.class);

  @Autowired private LdapTemplate ldapTemplate;

  @Autowired private OperateProperties operateProperties;

  private Map<String, UserDto> ldapDnToUser = new ConcurrentHashMap<>();

  @Override
  public UserDto createUserDtoFrom(final Authentication authentication) {
    final LdapUserDetails userDetails = (LdapUserDetails) authentication.getPrincipal();
    final String dn = userDetails.getDn();
    if (!ldapDnToUser.containsKey(dn)) {
      LOGGER.info(String.format("Do a LDAP Lookup for user DN: %s)", dn));
      try {
        ldapDnToUser.put(dn, ldapTemplate.lookup(dn, new LdapUserAttributesMapper()));
      } catch (Exception ex) {
        throw new UserNotFoundException(String.format("Couldn't find user for dn %s", dn));
      }
    }
    return ldapDnToUser.get(dn);
  }

  public void cleanUp(Authentication authentication) {
    final LdapUserDetails userDetails = (LdapUserDetails) authentication.getPrincipal();
    final String dn = userDetails.getDn();
    ldapDnToUser.remove(dn);
  }

  @Override
  public String getUserToken(final Authentication authentication) {
    throw new UnsupportedOperationException("Get token is not supported for LDAP authentication");
  }

  private final class LdapUserAttributesMapper implements AttributesMapper<UserDto> {

    private LdapUserAttributesMapper() {}

    public UserDto mapFromAttributes(Attributes attrs) throws NamingException {
      final UserDto userDto = new UserDto().setCanLogout(true);
      final Attribute userIdAttr = attrs.get(operateProperties.getLdap().getUserIdAttrName());
      if (userIdAttr != null) {
        userDto.setUserId((String) userIdAttr.get());
      }
      final Attribute displayNameAttr =
          attrs.get(operateProperties.getLdap().getDisplayNameAttrName());
      if (displayNameAttr != null) {
        userDto.setDisplayName((String) displayNameAttr.get());
      }
      // for now can do all TODO: how to retrieve LDAP Roles/Permissions ?
      userDto.setPermissions(List.of(READ, WRITE));
      return userDto;
    }
  }
}
