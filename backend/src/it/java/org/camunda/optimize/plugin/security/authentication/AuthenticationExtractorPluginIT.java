/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.plugin.security.authentication;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_AUTHORIZATION;
import static org.camunda.optimize.service.util.importing.EngineConstants.ALL_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class AuthenticationExtractorPluginIT extends AbstractIT {

  private static final String TEST_DEFINITION = "test-definition";
  private static final String KERMIT_USER = "kermit";

  private ConfigurationService configurationService;

  @BeforeEach
  public void setup() {
    configurationService = embeddedOptimizeExtension.getConfigurationService();
    createKermitUserAndGrantOptimizeAccess();
    configurationService.setPluginDirectory("target/testPluginsValid");
  }

  @Test
  public void automaticallySignInWhenCustomHeaderIsSet() {
    // given
    String basePackage = "org.camunda.optimize.testplugin.security.authentication.util1";
    addAuthenticationExtractorBasePackagesToConfiguration(basePackage);
    NewCookie newCookie = simulateSingleSignOnAuthHeaderRequestAndReturnCookies(KERMIT_USER);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetAllEntitiesRequest()
      .addSingleCookie(newCookie.getName(), newCookie.getValue())
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void signInWithCustomHeaderSetApiCall() {
    // given
    deployAndImportTestDefinition();
    String basePackage = "org.camunda.optimize.testplugin.security.authentication.util1";
    addAuthenticationExtractorBasePackagesToConfiguration(basePackage);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetAllEntitiesRequest()
      .addSingleHeader("user", KERMIT_USER)
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getCookies()).hasEntrySatisfying(OPTIMIZE_AUTHORIZATION, authCookie -> {
      assertThat(authCookie).isNotNull();
    });
  }

  @Test
  public void signInWithCustomHeaderSetApiCall_evenIfInvalidOptimizeAuthCookieIsPresent() {
    // given
    String basePackage = "org.camunda.optimize.testplugin.security.authentication.util1";
    addAuthenticationExtractorBasePackagesToConfiguration(basePackage);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetAllEntitiesRequest()
      .addSingleHeader("user", "demo")
      .addSingleCookie(OPTIMIZE_AUTHORIZATION, "invalid")
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getCookies()).hasEntrySatisfying(OPTIMIZE_AUTHORIZATION, authCookie -> {
      assertThat(authCookie).isNotNull();
    });
  }

  @Test
  public void withoutBasePackageThereIsNotCookieProvided() {
    // when simulate first user request with wrong header
    Response initialOptimizeResponse = embeddedOptimizeExtension
      .rootTarget("/").request().header("user", KERMIT_USER).get();
    NewCookie cookieThatWillBeSetInTheBrowser =
      initialOptimizeResponse.getCookies().get(OPTIMIZE_AUTHORIZATION);

    // then
    assertThat(cookieThatWillBeSetInTheBrowser).isNull();
  }

  @Test
  public void wrongCustomHeaderDoesNotProvideCookie() {
    // given
    String basePackage = "org.camunda.optimize.testplugin.security.authentication.util1";
    addAuthenticationExtractorBasePackagesToConfiguration(basePackage);

    // when simulate first user request with wrong header
    Response initialOptimizeResponse = embeddedOptimizeExtension
      .rootTarget("/").request().header("foo", "bar").get();
    NewCookie cookieThatWillBeSetInTheBrowser =
      initialOptimizeResponse.getCookies().get(OPTIMIZE_AUTHORIZATION);

    // then
    assertThat(cookieThatWillBeSetInTheBrowser).isNull();
  }

  @Test
  public void deleteCookieOnSignOut() {
    // given
    String basePackage = "org.camunda.optimize.testplugin.security.authentication.util1";
    addAuthenticationExtractorBasePackagesToConfiguration(basePackage);
    NewCookie newCookie = simulateSingleSignOnAuthHeaderRequestAndReturnCookies(KERMIT_USER);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildLogOutRequest()
      .addSingleCookie(newCookie.getName(), newCookie.getValue())
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    optimizeAuthCookieIsBeingDeleted(response);
  }

  @Test
  public void deleteCookieOn401Response() {
    // given
    String basePackage = "org.camunda.optimize.testplugin.security.authentication.util1";
    addAuthenticationExtractorBasePackagesToConfiguration(basePackage);

    NewCookie newCookie = simulateSingleSignOnAuthHeaderRequestAndReturnCookies(KERMIT_USER);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetAllEntitiesRequest()
      .withGivenAuthToken("wrong token")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
    optimizeAuthCookieIsBeingDeleted(response);
  }

  @Test
  public void onSessionTimeoutTheSessionIsRenewed() {
    // given
    deployAndImportTestDefinition();
    String basePackage = "org.camunda.optimize.testplugin.security.authentication.util1";
    addAuthenticationExtractorBasePackagesToConfiguration(basePackage);
    NewCookie newCookie = simulateSingleSignOnAuthHeaderRequestAndReturnCookies(KERMIT_USER);

    // when I fetch the process definitions
    List<ProcessDefinitionOptimizeDto> definitions = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetProcessDefinitionsRequest()
      .addSingleCookie(newCookie.getName(), newCookie.getValue())
      .withoutAuthentication()
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());

    // then there are not definitions since kermit is not authorized to see definitions
    assertThat(definitions.isEmpty()).isTrue();

    // when session is expired then the session is renewed and the authorizations updated
    grantSingleDefinitionAuthorizationsForKermit();
    moveTimeByOneDay();
    newCookie = simulateSingleSignOnAuthHeaderRequestAndReturnCookies(KERMIT_USER);

    // then kermit should have access to the authorized definition
    definitions = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetProcessDefinitionsRequest()
      .addSingleCookie(newCookie.getName(), newCookie.getValue())
      .withoutAuthentication()
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());

    assertThat(definitions).hasSize(1);
  }

  @Test
  public void cookieHasCorrectExpiryDate() {
    // given
    final String basePackage = "org.camunda.optimize.testplugin.security.authentication.util1";
    addAuthenticationExtractorBasePackagesToConfiguration(basePackage);
    final int expiryTime = embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .getTokenLifeTimeMinutes();

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    final NewCookie newCookie = simulateSingleSignOnAuthHeaderRequestAndReturnCookies(KERMIT_USER);
    final Duration durationUntilExpiry = Duration.between(
      now.toInstant().truncatedTo(ChronoUnit.SECONDS),
      newCookie.getExpiry().toInstant()
    );

    // then
    assertThat(durationUntilExpiry.toMinutes()).isEqualTo(expiryTime);
  }

  private void deployAndImportTestDefinition() {
    deploySimpleProcessDefinition();
    importAllEngineEntitiesFromScratch();
  }

  private void createKermitUserAndGrantOptimizeAccess() {
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
  }

  private void moveTimeByOneDay() {
    LocalDateUtil.setCurrentTime(OffsetDateTime.now().plusDays(1));
  }

  private void grantSingleDefinitionAuthorizationsForKermit() {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(AuthenticationExtractorPluginIT.TEST_DEFINITION);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(AuthenticationExtractorPluginIT.KERMIT_USER);
    engineIntegrationExtension.createAuthorization(authorizationDto);
  }

  private void deploySimpleProcessDefinition() {
    BpmnModelInstance modelInstance = getSimpleBpmnDiagram(AuthenticationExtractorPluginIT.TEST_DEFINITION);
    engineIntegrationExtension.deployProcessAndGetId(modelInstance);
  }

  private void optimizeAuthCookieIsBeingDeleted(Response response) {
    NewCookie deleteCookie = response.getCookies().get(OPTIMIZE_AUTHORIZATION);
    assertThat(deleteCookie).isNotNull();
    assertThat(deleteCookie.getValue()).isEmpty();
    assertThat(deleteCookie.getPath()).isEqualTo("/");
  }

  private NewCookie simulateSingleSignOnAuthHeaderRequestAndReturnCookies(String headerValue) {
    Response initialOptimizeResponse = embeddedOptimizeExtension
      .rootTarget("/").request().header("user", headerValue).get();

    NewCookie cookieThatWillBeSetInTheBrowser =
      initialOptimizeResponse.getCookies().get(OPTIMIZE_AUTHORIZATION);
    assertThat(cookieThatWillBeSetInTheBrowser).isNotNull();
    return cookieThatWillBeSetInTheBrowser;
  }

  private void addAuthenticationExtractorBasePackagesToConfiguration(String... basePackages) {
    List<String> basePackagesList = Arrays.asList(basePackages);
    configurationService.setAuthenticationExtractorPluginBasePackages(basePackagesList);
    embeddedOptimizeExtension.reloadConfiguration();
  }

}
