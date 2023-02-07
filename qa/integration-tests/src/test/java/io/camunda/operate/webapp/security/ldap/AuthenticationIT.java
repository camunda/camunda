/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.ldap;

import io.camunda.operate.es.ElasticsearchTask;
import io.camunda.operate.es.RetryElasticsearchClient;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.OperateWebSessionIndex;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.rest.AuthenticationRestService;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.AuthenticationTestable;
import io.camunda.operate.webapp.security.ElasticsearchSessionRepository;
import io.camunda.operate.webapp.security.SameSiteCookieTomcatContextCustomizer;
import io.camunda.operate.webapp.security.oauth2.CCSaaSJwtAuthenticationTokenValidator;
import io.camunda.operate.webapp.security.oauth2.Jwt2AuthenticationTokenConverter;
import io.camunda.operate.webapp.security.oauth2.OAuth2WebConfigurer;
import io.camunda.operate.webapp.security.OperateProfileService;
import io.camunda.operate.webapp.security.OperateURIs;
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

import static org.assertj.core.api.Assertions.assertThat;

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
        ElasticsearchTask.class,
        ElasticsearchSessionRepository.class,
        OperateWebSessionIndex.class,
        OperateProfileService.class
    },
    properties = {
        "camunda.operate.ldap.baseDn=dc=planetexpress,dc=com",
        "camunda.operate.ldap.managerDn=cn=admin,dc=planetexpress,dc=com",
        "camunda.operate.ldap.managerPassword=GoodNewsEveryone",
        "camunda.operate.ldap.userSearchFilter=uid={0}"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles({"ldap-auth", "test"})
@ContextConfiguration(initializers = {AuthenticationIT.Initializer.class})
public class AuthenticationIT implements AuthenticationTestable {

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Autowired
  private OperateProperties operateProperties;

  @ClassRule
  public static GenericContainer<?> ldapServer =
      // https://github.com/rroemhild/docker-test-openldap
      new GenericContainer<>("rroemhild/test-openldap")
          .withExposedPorts(10389);

  @Override
  public TestRestTemplate getTestRestTemplate() {
    return testRestTemplate;
  }

  static class Initializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
      TestPropertyValues.of(
          "server.servlet.session.cookie.name = " + OperateURIs.COOKIE_JSESSIONID,
          String.format("camunda.operate.ldap.url=ldap://%s:%d/", ldapServer.getHost(), ldapServer.getFirstMappedPort())
      ).applyTo(configurableApplicationContext.getEnvironment());
    }
  }

  @Test
  public void testLoginSuccess() {
    ResponseEntity<?> response = login("fry", "fry");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThatCookiesAndSecurityHeadersAreSet(response);
  }

  @Test
  public void testLoginFailed() {
    ResponseEntity<?> response = login("amy", "dont-know");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void testLogout() {
    // Given
    ResponseEntity<?> response = login("fry", "fry");
    // When
    ResponseEntity<?> logoutResponse = logout(response);
    // Then
    assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThatCookiesAreDeleted(logoutResponse);
  }

  @Test
  public void shouldReturnCurrentUser() {
    //given authenticated user
    ResponseEntity<?> response = login("bender", "bender");
    // when
    UserDto userInfo = getCurrentUser(response);
    //then
    assertThat(userInfo.getUserId()).isEqualTo("bender");
    assertThat(userInfo.getDisplayName()).isEqualTo("Bender");
    assertThat(userInfo.isCanLogout()).isTrue();
  }
}
