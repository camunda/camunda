/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.jetty;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.service.license.LicenseManager;
import org.camunda.optimize.util.FileReaderUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import static org.camunda.optimize.jetty.NoCachingFilter.NO_STORE;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.NO_CACHE_RESOURCES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class NoCachingIT extends AbstractIT {

  private LicenseManager licenseManager;

  @BeforeEach
  public void setup() {
    licenseManager = embeddedOptimizeExtension.getApplicationContext().getBean(LicenseManager.class);
    addLicenseToOptimize();
  }

  @AfterEach
  public void resetBasePackage() {
    licenseManager.resetLicenseFromFile();
  }

  @Test
  public void loadingOfStaticResourcesContainsNoCacheHeader() {
    // given
    for (String staticResource : NO_CACHE_RESOURCES) {

      // when
      Response response = embeddedOptimizeExtension.rootTarget(staticResource).request().get();

      // then
      assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL), is(NO_STORE));
    }
  }

  @Test
  public void restApiCallResponseContainsNoCacheHeader() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor().buildCheckImportStatusRequest().execute();

    // then
    assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL), is(NO_STORE));
  }

  private void addLicenseToOptimize() {
    String license = FileReaderUtil.readValidTestLicense();

    Response response =
      embeddedOptimizeExtension.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute();
    assertThat(response.getStatus(), is(200));
  }
}
