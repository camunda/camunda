/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.jetty;

import org.camunda.optimize.service.license.LicenseManager;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
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

  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  @Rule
  public RuleChain chain = RuleChain.outerRule(engineIntegrationExtensionRule)
    .around(elasticSearchIntegrationTestExtensionRule).around(embeddedOptimizeExtensionRule);

  private LicenseManager licenseManager;

  @Before
  public void setup() {
    licenseManager = embeddedOptimizeExtensionRule.getApplicationContext().getBean(LicenseManager.class);
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
        embeddedOptimizeExtensionRule.rootTarget(staticResource).request().get();

      // then
      assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL), is(NO_STORE));
    }
  }

  @Test
  public void restApiCallResponseContainsNoCacheHeader() {
    // when
    Response response =
      embeddedOptimizeExtensionRule.getRequestExecutor().buildCheckImportStatusRequest().execute();

    // then
    assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL), is(NO_STORE));
  }

  private void addLicenseToOptimize() {
    String license = FileReaderUtil.readValidTestLicense();

    Response response =
      embeddedOptimizeExtensionRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute();
    assertThat(response.getStatus(), is(200));
  }
}
