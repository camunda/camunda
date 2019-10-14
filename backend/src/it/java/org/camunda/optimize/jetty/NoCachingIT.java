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

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import static org.camunda.optimize.jetty.NoCachingFilter.NO_STORE;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.NO_CACHE_RESOURCES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


public class NoCachingIT {

  public EngineIntegrationRule engineIntegrationRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain.outerRule(engineIntegrationRule)
    .around(elasticSearchRule).around(embeddedOptimizeRule);

  private LicenseManager licenseManager;

  @Before
  public void setup() {
    licenseManager = embeddedOptimizeRule.getApplicationContext().getBean(LicenseManager.class);
    addLicenseToOptimize();
  }

  @After
  public void resetBasePackage() {
    licenseManager.resetLicenseFromFile();
  }

  @Test
  public void loadingOfStaticResourcesContainsNoCacheHeader() {
    // given
    for (String staticResource : NO_CACHE_RESOURCES) {

      // when
      Response response =
        embeddedOptimizeRule.rootTarget(staticResource).request().get();

      // then
      assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL), is(NO_STORE));
    }
  }

  @Test
  public void restApiCallResponseContainsNoCacheHeader() {
    // when
    Response response =
      embeddedOptimizeRule.getRequestExecutor().buildCheckImportStatusRequest().execute();

    // then
    assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL), is(NO_STORE));
  }

  private void addLicenseToOptimize() {
    String license = FileReaderUtil.readValidTestLicense();

    Response response =
      embeddedOptimizeRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute();
    assertThat(response.getStatus(), is(200));
  }
}
