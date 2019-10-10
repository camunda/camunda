/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.testplugin.security.authentication;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.plugin.AuthenticationExtractorProvider;
import org.camunda.optimize.service.license.LicenseManager;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.security.AuthCookieService.OPTIMIZE_AUTHORIZATION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public class AuthenticationExtractorPluginIT {

  private static final String TEST_DEFINITION = "test-definition";
  private static final String KERMIT_USER = "kermit";
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);
  private ConfigurationService configurationService;
  private AuthenticationExtractorProvider pluginProvider;
  private LicenseManager licenseManager;
  private String license = readLicense();

  @Before
  public void setup() {
    licenseManager = embeddedOptimizeRule.getApplicationContext().getBean(LicenseManager.class);
    licenseManager.setOptimizeLicense(license);
    configurationService = embeddedOptimizeRule.getConfigurationService();
    createKermitUserAndGrantOptimizeAccess();
    configurationService.setPluginDirectory("target/testPluginsValid");
  }

  @After
  public void resetBasePackage() {
    licenseManager.resetLicenseFromFile();
  }

  private String readLicense() {
    try {
      return new String(
        Files.readAllBytes(Paths.get(getClass().getResource("/license/ValidTestLicense.txt").toURI())),
        StandardCharsets.UTF_8
      );
    } catch (IOException | URISyntaxException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  @Test
  public void automaticallySignInWhenCustomHeaderIsSet() {
    // given
    String basePackage = "org.camunda.optimize.testplugin.security.authentication.util1";
    addAuthenticationExtractorBasePackagesToConfiguration(basePackage);
    NewCookie newCookie = simulateSingleSignOnAuthHeaderRequestAndReturnCookies(KERMIT_USER);

    // when
    Response response = embeddedOptimizeRule.getRequestExecutor()
      .buildGetAllAlertsRequest()
      .addSingleCookie(newCookie.getName(), newCookie.getValue())
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }


  @Test
  public void signInWithCustomHeaderSetApiCall() {
    // given
    deployAndImportTestDefinition();
    String basePackage = "org.camunda.optimize.testplugin.security.authentication.util1";
    addAuthenticationExtractorBasePackagesToConfiguration(basePackage);

    // when
    Response response = embeddedOptimizeRule.getRequestExecutor()
      .buildGetAllAlertsRequest()
      .addSingleHeader("user", KERMIT_USER)
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
    assertThat(response.getCookies().get(OPTIMIZE_AUTHORIZATION), is(notNullValue()));
  }

  @Test
  public void withoutBasePackageThereIsNotCookieProvided() {
    // when simulate first user request with wrong header
    Response initialOptimizeResponse = embeddedOptimizeRule
      .rootTarget("/").request().header("user", KERMIT_USER).get();
    NewCookie cookieThatWillBeSetInTheBrowser =
      initialOptimizeResponse.getCookies().get(OPTIMIZE_AUTHORIZATION);

    // then
    assertThat(cookieThatWillBeSetInTheBrowser, nullValue());
  }

  @Test
  public void wrongCustomHeaderDoesNotProvideCookie() {
    // given
    String basePackage = "org.camunda.optimize.testplugin.security.authentication.util1";
    addAuthenticationExtractorBasePackagesToConfiguration(basePackage);

    // when simulate first user request with wrong header
    Response initialOptimizeResponse = embeddedOptimizeRule
      .rootTarget("/").request().header("foo", "bar").get();
    NewCookie cookieThatWillBeSetInTheBrowser =
      initialOptimizeResponse.getCookies().get(OPTIMIZE_AUTHORIZATION);

    // then
    assertThat(cookieThatWillBeSetInTheBrowser, nullValue());
  }

  @Test
  public void deleteCookieOnSignOut() {
    // given
    String basePackage = "org.camunda.optimize.testplugin.security.authentication.util1";
    addAuthenticationExtractorBasePackagesToConfiguration(basePackage);
    NewCookie newCookie = simulateSingleSignOnAuthHeaderRequestAndReturnCookies(KERMIT_USER);

    // when
    Response response = embeddedOptimizeRule.getRequestExecutor()
      .buildLogOutRequest()
      .addSingleCookie(newCookie.getName(), newCookie.getValue())
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
    optimizeAuthCookieIsBeingDeleted(response);
  }

  @Test
  public void deleteCookieOn401Response() {
    // given
    String basePackage = "org.camunda.optimize.testplugin.security.authentication.util1";
    addAuthenticationExtractorBasePackagesToConfiguration(basePackage);

    NewCookie newCookie = simulateSingleSignOnAuthHeaderRequestAndReturnCookies(KERMIT_USER);

    // when
    Response response = embeddedOptimizeRule.getRequestExecutor()
      .buildGetAllAlertsRequest()
      .withGivenAuthToken("wrong token")
      .execute();

    // then
    assertThat(response.getStatus(), is(401));
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
    List<ProcessDefinitionOptimizeDto> definitions = embeddedOptimizeRule.getRequestExecutor()
      .buildGetProcessDefinitionsRequest()
      .addSingleCookie(newCookie.getName(), newCookie.getValue())
      .withoutAuthentication()
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);

    // then there are not definitions since kermit is not authorized to see definitions
    assertThat(definitions.isEmpty(), is(true));

    // when session is expired then the session is renewed and the authorizations updated
    grantSingleDefinitionAuthorizationsForKermit();
    moveTimeByOneDay();
    newCookie = simulateSingleSignOnAuthHeaderRequestAndReturnCookies(KERMIT_USER);

    // then kermit should have access to the authorized definition
    definitions = embeddedOptimizeRule.getRequestExecutor()
      .buildGetProcessDefinitionsRequest()
      .addSingleCookie(newCookie.getName(), newCookie.getValue())
      .withoutAuthentication()
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);

    assertThat(definitions.size(), is(1));
  }

  private void deployAndImportTestDefinition() {
    deploySimpleProcessDefinition();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
  }

  private void createKermitUserAndGrantOptimizeAccess() {
    engineRule.addUser(KERMIT_USER, KERMIT_USER);
    engineRule.grantUserOptimizeAccess(KERMIT_USER);
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
    engineRule.createAuthorization(authorizationDto);
  }

  private void deploySimpleProcessDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(AuthenticationExtractorPluginIT.TEST_DEFINITION)
      .startEvent()
      .endEvent()
      .done();
    engineRule.deployProcessAndGetId(modelInstance);
  }

  private void optimizeAuthCookieIsBeingDeleted(Response response) {
    NewCookie deleteCookie = response.getCookies().get(OPTIMIZE_AUTHORIZATION);
    assertThat(deleteCookie, notNullValue());
    assertThat(deleteCookie.getValue(), is(""));
    assertThat(deleteCookie.getPath(), is("/"));
  }

  private NewCookie simulateSingleSignOnAuthHeaderRequestAndReturnCookies(String headerValue) {
    Response initialOptimizeResponse = embeddedOptimizeRule
      .rootTarget("/").request().header("user", headerValue).get();

    NewCookie cookieThatWillBeSetInTheBrowser =
      initialOptimizeResponse.getCookies().get(OPTIMIZE_AUTHORIZATION);
    assertThat(cookieThatWillBeSetInTheBrowser, notNullValue());
    return cookieThatWillBeSetInTheBrowser;
  }

  private void addAuthenticationExtractorBasePackagesToConfiguration(String... basePackages) {
    List<String> basePackagesList = Arrays.asList(basePackages);
    configurationService.setAuthenticationExtractorPluginBasePackages(basePackagesList);
    embeddedOptimizeRule.reloadConfiguration();
  }

}
