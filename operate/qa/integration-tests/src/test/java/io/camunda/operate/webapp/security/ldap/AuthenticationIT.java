/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.ldap;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.ElasticsearchConnector;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.store.elasticsearch.ElasticsearchTaskStore;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.rest.AuthenticationRestService;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.AuthenticationTestable;
import io.camunda.operate.webapp.security.OperateURIs;
import io.camunda.operate.webapp.security.SameSiteCookieTomcatContextCustomizer;
import io.camunda.operate.webapp.security.oauth2.CCSaaSJwtAuthenticationTokenValidator;
import io.camunda.operate.webapp.security.oauth2.Jwt2AuthenticationTokenConverter;
import io.camunda.operate.webapp.security.oauth2.OAuth2WebConfigurer;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.GenericContainer;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      SameSiteCookieTomcatContextCustomizer.class,
      OperateProperties.class,
      TestApplicationWithNoBeans.class,
      AuthenticationRestService.class,
      OAuth2WebConfigurer.class,
      Jwt2AuthenticationTokenConverter.class,
      CCSaaSJwtAuthenticationTokenValidator.class,
      LDAPConfig.class,
      LDAPWebSecurityConfig.class,
      LDAPUserService.class,
      RetryElasticsearchClient.class,
      ElasticsearchTaskStore.class,
      OperateProfileService.class,
      ElasticsearchConnector.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class
    },
    properties = {
      "camunda.operate.ldap.baseDn=dc=planetexpress,dc=com",
      "camunda.operate.ldap.managerDn=cn=admin,dc=planetexpress,dc=com",
      "camunda.operate.ldap.managerPassword=GoodNewsEveryone",
      "camunda.operate.ldap.userSearchFilter=uid={0}"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"ldap-auth", "test"})
@ContextConfiguration(initializers = {AuthenticationIT.Initializer.class})
public class AuthenticationIT implements AuthenticationTestable {

  @ClassRule
  public static GenericContainer<?> ldapServer =
      // https://github.com/rroemhild/docker-test-openldap
      new GenericContainer<>("rroemhild/test-openldap").withExposedPorts(10389);

  @Autowired private TestRestTemplate testRestTemplate;
  @Autowired private OperateProperties operateProperties;

  @Override
  public TestRestTemplate getTestRestTemplate() {
    return testRestTemplate;
  }

  @Test
  public void testLoginSuccess() {
    final ResponseEntity<?> response = login("fry", "fry");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThatCookiesAndSecurityHeadersAreSet(response);
  }

  @Test
  public void testLoginFailed() {
    final ResponseEntity<?> response = login("amy", "dont-know");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void testLogout() {
    // Given
    final ResponseEntity<?> response = login("fry", "fry");
    // When
    final ResponseEntity<?> logoutResponse = logout(response);
    // Then
    assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThatCookiesAreDeleted(logoutResponse);
  }

  @Test
  public void shouldReturnCurrentUser() {
    // given authenticated user
    final ResponseEntity<?> response = login("bender", "bender");
    // when
    final UserDto userInfo = getCurrentUser(response);
    // then
    assertThat(userInfo.getUserId()).isEqualTo("bender");
    assertThat(userInfo.getDisplayName()).isEqualTo("Bender");
    assertThat(userInfo.isCanLogout()).isTrue();
  }

  static class Initializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(final ConfigurableApplicationContext configurableApplicationContext) {
      TestPropertyValues.of(
              "server.servlet.session.cookie.name = " + OperateURIs.COOKIE_JSESSIONID,
              String.format(
                  "camunda.operate.ldap.url=ldap://%s:%d/",
                  ldapServer.getHost(), ldapServer.getFirstMappedPort()))
          .applyTo(configurableApplicationContext.getEnvironment());
    }
  }
}
