/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.ldap;

import static io.camunda.operate.webapp.security.OperateURIs.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;

import io.camunda.operate.OperateProfileService;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.rest.AuthenticationRestService;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.AuthenticationTestable;
import io.camunda.operate.webapp.security.OperateURIs;
import io.camunda.operate.webapp.security.auth.RolePermissionService;
import io.camunda.operate.webapp.security.oauth2.CCSaaSJwtAuthenticationTokenValidator;
import io.camunda.operate.webapp.security.oauth2.Jwt2AuthenticationTokenConverter;
import io.camunda.operate.webapp.security.oauth2.OAuth2WebConfigurer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration;
import org.springframework.boot.autoconfigure.ldap.embedded.EmbeddedLdapAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      EmbeddedLdapAutoConfiguration.class,
      LdapAutoConfiguration.class,
      OperateProperties.class,
      TestApplicationWithNoBeans.class,
      AuthenticationRestService.class,
      RolePermissionService.class,
      LDAPWebSecurityConfig.class,
      OAuth2WebConfigurer.class,
      Jwt2AuthenticationTokenConverter.class,
      CCSaaSJwtAuthenticationTokenValidator.class,
      LDAPUserService.class,
      OperateProfileService.class
    },
    properties = {
      "spring.ldap.embedded.base-dn=dc=springframework,dc=org",
      "spring.ldap.embedded.credential.username=uid=admin",
      "spring.ldap.embedded.credential.password=secret",
      "spring.ldap.embedded.ldif=classpath:config/ldap-test-server.ldif",
      "spring.ldap.embedded.port=8389",
      "camunda.operate.ldap.url=ldap://localhost:8389/",
      "camunda.operate.ldap.baseDn=dc=springframework,dc=org",
      "camunda.operate.ldap.managerDn=uid=admin",
      "camunda.operate.ldap.managerPassword=secret",
      "camunda.operate.ldap.userSearchFilter=uid={0}",
      // Custom session id
      "server.servlet.session.cookie.name = " + OperateURIs.COOKIE_JSESSIONID,
      // WRONG ATTR NAMES
      "camunda.operate.ldap.firstnameAttrName=wrongValue",
      "camunda.operate.ldap.lastnameAttrName="
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"ldap-auth", "test"})
public class AuthenticationWrongParametersIT implements AuthenticationTestable {

  @Autowired private TestRestTemplate testRestTemplate;

  @Test
  public void shouldReturnCurrentUser() {
    // given authenticated user
    final ResponseEntity<?> response = loginAs("bob", "bobspassword");
    // when
    final UserDto userInfo = getCurrentUser(response);
    // then
    assertThat(userInfo.getUserId()).isEqualTo("bob");
  }

  protected ResponseEntity<?> loginAs(String user, String password) {
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_FORM_URLENCODED);

    final MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("username", user);
    body.add("password", password);

    return testRestTemplate.postForEntity(
        LOGIN_RESOURCE, new HttpEntity<>(body, headers), Void.class);
  }

  @Override
  public TestRestTemplate getTestRestTemplate() {
    return testRestTemplate;
  }
}
