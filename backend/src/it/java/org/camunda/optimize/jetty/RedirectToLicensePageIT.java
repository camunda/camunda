/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.jetty;

import org.camunda.optimize.service.license.LicenseManager;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.util.FileReaderUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.net.URI;

import static org.camunda.optimize.jetty.OptimizeResourceConstants.INDEX_HTML_PAGE;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.INDEX_PAGE;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.LICENSE_PAGE;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.LOGIN_PAGE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;


public class RedirectToLicensePageIT {

  public EngineIntegrationRule engineIntegrationRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  private LicenseManager licenseManager;

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(engineIntegrationRule).around(elasticSearchRule).around(embeddedOptimizeRule);

  @Before
  public void setup() {
    licenseManager = embeddedOptimizeRule.getApplicationContext().getBean(LicenseManager.class);
  }

  @After
  public void resetBasePackage() {
    licenseManager.resetLicenseFromFile();
  }

  @Test
  public void redirectFromLoginPageToLicensePage() {
    // when
    Response response =
      embeddedOptimizeRule.rootTarget(LOGIN_PAGE).request().get();

    // then
    assertThat(response.getLocation(), is(not(nullValue())));
    URI location = response.getLocation();
    assertThat(location.getPath().startsWith("/license"), is(true));
  }

  private void addLicenseToOptimize() {
    String license = FileReaderUtil.readValidTestLicense();

    Response response =
      embeddedOptimizeRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute();
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void noRedirectFromLoginPageToLicensePageWithValidLicense() {
    // given
    addLicenseToOptimize();

    // when
    Response response =
      embeddedOptimizeRule.rootTarget(LOGIN_PAGE).request().get();

    // then
    assertThat(response.getStatus(), is(200));
    assertThat(response.getLocation(), is(nullValue()));
  }

  @Test
  public void redirectFromRootPageToLicensePage() {
    // when
    Response response =
      embeddedOptimizeRule.rootTarget(INDEX_PAGE).request().get();

    // then
    assertThat(response.getLocation(), is(not(nullValue())));
    URI location = response.getLocation();
    assertThat(location.getPath().startsWith("/license"), is(true));
  }

  @Test
  public void noRedirectFromRootPageToLicensePageWithValidLicense() {
    // given
    addLicenseToOptimize();

    // when
    Response response =
      embeddedOptimizeRule.rootTarget(INDEX_PAGE).request().get();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void redirectFromErrorPageToLicensePage() {
    // to ensure license is refreshed from file and elasticsearch
    embeddedOptimizeRule.getApplicationContext().getBean(LicenseManager.class).init();
    // when
    Response response =
      embeddedOptimizeRule
        .rootTarget("/process/leadQualification:2:7f0f82b8-5255-11e7-99a3-02421525a25c/none").request().get();

    // then first redirect request should be the license page
    assertThat(response.getLocation().getPath(), is(LICENSE_PAGE));

    // when I now redirect to root page
    response =
      embeddedOptimizeRule
        .rootTarget(INDEX_PAGE).request().get();

    // then I get a redirect to the license page
    assertThat(response.getLocation(), is(not(nullValue())));
    URI location = response.getLocation();
    assertThat(location.getPath().startsWith("/license"), is(true));
  }

  @Test
  public void noRedirectFromErrorPageToLicensePageWithValidLicense() {
    // given a license
    addLicenseToOptimize();

    // when I query a random path
    Response response =
      embeddedOptimizeRule
        .rootTarget("/process/leadQualification:2:7f0f82b8-5255-11e7-99a3-02421525a25c/none").request().get();

    // then first redirect request should be the root page
    assertThat(response.getStatus(), is(200));

    // when I now redirect to root page
    response =
      embeddedOptimizeRule
        .rootTarget(INDEX_PAGE).request().get();

    // then I shouldn't get a redirect to the license page
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void redirectFromIndexHtmlPageToLicensePage() {
    // when
    Response response =
      embeddedOptimizeRule.rootTarget(INDEX_HTML_PAGE).request().get();

    // then
    assertThat(response.getLocation(), is(not(nullValue())));
    URI location = response.getLocation();
    assertThat(location.getPath().startsWith("/license"), is(true));
  }

  @Test
  public void noRedirectFromIndexHtmlPageToLicensePageWithValidLicense() {
    // given
    addLicenseToOptimize();

    // when
    Response response =
      embeddedOptimizeRule.rootTarget(INDEX_HTML_PAGE).request().get();

    // then
    assertThat(response.getStatus(), is(200));
  }

}
