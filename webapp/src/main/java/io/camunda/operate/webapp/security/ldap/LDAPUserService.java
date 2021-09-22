/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security.ldap;

import io.camunda.operate.webapp.security.UserService;
import java.util.List;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.rest.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.ldap.userdetails.LdapUserDetails;
import org.springframework.stereotype.Component;

import static io.camunda.operate.util.CollectionUtil.map;
import static io.camunda.operate.webapp.security.OperateURIs.LDAP_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.Permission.READ;
import static io.camunda.operate.webapp.security.Permission.WRITE;

@Component
@Profile(LDAP_AUTH_PROFILE)
public class LDAPUserService implements UserService<Authentication> {

  private static final Logger logger = LoggerFactory.getLogger(LDAPUserService.class);

  @Autowired
  private LdapTemplate ldapTemplate;

  @Autowired
  private OperateProperties operateProperties;

  @Override
  public UserDto createUserDtoFrom(
      final Authentication authentication) {
    LdapUserDetails userDetails = (LdapUserDetails) authentication.getPrincipal();
    final String dn = userDetails.getDn();
    UserDto userDto = new UserDto();
    try {
      userDto = ldapTemplate
          .lookup(dn,
              new LdapUserAttributesMapper());
    } catch (Exception ex) {
      logger.warn("Exception occurred when loading current user data: " + ex.getMessage(), ex);
    }
    return userDto
        .setCanLogout(true)
        .setUsername(userDetails.getUsername())
        // for now can do all TODO: how to retrieve LDAP Roles/Permissions ?
        .setPermissions(List.of(READ, WRITE));
  }

  private class LdapUserAttributesMapper implements AttributesMapper<UserDto> {

    private LdapUserAttributesMapper() {
    }

    public UserDto mapFromAttributes(Attributes attrs) throws NamingException {
      final UserDto userDto = new UserDto();
      final Attribute firstNameAttr = attrs.get(operateProperties.getLdap().getFirstnameAttrName());
      if (firstNameAttr != null) {
        userDto
            .setFirstname((String) firstNameAttr.get());
      }
      final Attribute lastNameAttr = attrs.get(operateProperties.getLdap().getLastnameAttrName());
      if (lastNameAttr != null) {
        userDto.setLastname((String) lastNameAttr.get());
      }
      return userDto;
    }
  }
}
