/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.LicenseInformationDto;
import org.camunda.optimize.service.license.LicenseManager;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.camunda.optimize.util.FileReaderUtil;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class LicenseCheckingRestServiceIT {

  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  public static ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule =
    new ElasticSearchIntegrationTestExtensionRule();
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  private static final String CUSTOMER_ID = "schrottis inn";
  private static OffsetDateTime VALID_UNTIL;

  private static DateTimeFormatter sdf = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  @Rule
  public RuleChain chain = RuleChain.outerRule(elasticSearchIntegrationTestExtensionRule)
    .around(engineIntegrationExtensionRule)
    .around(embeddedOptimizeExtensionRule);

  @BeforeClass
  public static void init() {
    VALID_UNTIL = OffsetDateTime.parse("9999-01-01T00:00:00.000+0100", sdf);
  }

  @Test
  public void validLegacyLicenseShouldBeAccepted() {
    // given
    String license = FileReaderUtil.readValidTestLicense();

    // when
    Response response =
      embeddedOptimizeExtensionRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute();

    // then
    assertThat(response.getStatus(), is(200));
    assertResult(response, CUSTOMER_ID, VALID_UNTIL, false);
  }

  @Test
  public void unsecuredLicenseEndpointsIgnoresInvalidAuthCookie() {
    // given
    String license = FileReaderUtil.readValidTestLegacyLicense();
    // when
    Response response = embeddedOptimizeExtensionRule.getRequestExecutor()
      .withoutAuthentication()
      .addSingleCookie(AuthCookieService.OPTIMIZE_AUTHORIZATION, "invalid")
      .buildValidateAndStoreLicenseRequest(license)
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
    assertResult(response, CUSTOMER_ID, VALID_UNTIL, false);
  }

  @Test
  public void unlimitedValidLegacyLicenseShouldBeAccepted() {
    // given
    String license = FileReaderUtil.readFile(
      "/license/TestLegacyLicense_Unlimited.txt");

    // when
    Response response =
      embeddedOptimizeExtensionRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute();

    // then
    assertThat(response.getStatus(), is(200));
    assertResult(response, CUSTOMER_ID, null, true);
  }

  @Test
  public void storedLicenseCanBeValidated() {
    // given
    String license = FileReaderUtil.readValidTestLegacyLicense();
    Response response =
      embeddedOptimizeExtensionRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute();
    assertThat(response.getStatus(), is(200));

    // when
    response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildValidateLicenseRequest()
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void invalidLegacyLicenseShouldThrowAnError() {
    // given
    String license = FileReaderUtil.readFile(
      "/license/TestLegacyLicense_Invalid.txt");

    // when
    String errorMessage =
      embeddedOptimizeExtensionRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute(String.class, 400);

    // then
    assertThat(errorMessage.contains("License Key has wrong format."), is(true));
  }

  @Test
  public void expiredLegacyLicenseShouldThrowAnError() {
    // given
    String license = FileReaderUtil.readFile(
      "/license/TestLegacyLicense_ExpiredDate.txt");

    // when
    String errorMessage =
      embeddedOptimizeExtensionRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute(String.class, 400);

    // then
    assertThat(errorMessage.contains("Your license has expired."), is(true));
  }

  @Test
  public void noLicenseAvailableShouldThrowAnError() {
    // to ensure license is refreshed from file and elasticsearch
    embeddedOptimizeExtensionRule.getApplicationContext().getBean(LicenseManager.class).init();
    // when
    String errorMessage = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildValidateLicenseRequest()
      .execute(String.class, 400);

    // then
    assertThat(
      errorMessage.contains("No license stored in Optimize. Please provide a valid Optimize license"),
      is(true)
    );
  }

  @Test
  public void notValidLicenseAndIWantToSeeRootPage() {
    // given
    String license = FileReaderUtil.readValidTestLegacyLicense();

    // when
    Response response =
      embeddedOptimizeExtensionRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void validUnlimitedUnifiedLicenseWithOptimizeShouldBeAccepted() {
    // given
    String license = FileReaderUtil.readFile(
      "/license/TestUnifiedLicense_UnlimitedWithOptimize.txt");

    // when
    Response response =
      embeddedOptimizeExtensionRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void validLimitedUnifiedLicenseWithOptimizeShouldBeAccepted() {
    // given
    String license = FileReaderUtil.readFile(
      "/license/TestUnifiedLicense_LimitedWithOptimize.txt");

    // when
    Response response =
      embeddedOptimizeExtensionRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void validUnlimitedUnifiedLicenseWithoutOptimizeShouldReturnError() {
    // given
    String license = FileReaderUtil.readFile(
      "/license/TestUnifiedLicense_UnlimitedWithoutOptimize.txt");

    // when
    String errorMessage =
      embeddedOptimizeExtensionRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute(String.class, 400);

    // then
    assertThat(errorMessage.contains("Your license is invalid."), is(true));
  }

  @Test
  public void validLimitedUnifiedLicenseWithoutOptimizeShouldReturnError() {
    // given
    String license = FileReaderUtil.readFile(
      "/license/TestUnifiedLicense_LimitedWithoutOptimize.txt");

    // when
    String errorMessage =
      embeddedOptimizeExtensionRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute(String.class, 400);

    // then
    assertThat(errorMessage.contains("Your license is invalid."), is(true));
  }

  @Test
  public void invalidUnifiedLicenseWithOptimizeShouldReturnError() {
    // given
    String license = FileReaderUtil.readFile(
      "/license/TestUnifiedLicense_InvalidSignatureWithOptimize.txt");

    // when
    String errorMessage =
      embeddedOptimizeExtensionRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute(String.class, 400);

    // then
    assertThat(errorMessage.contains("Your license is invalid."), is(true));
  }

  @Test
  public void invalidUnifiedLicenseWithoutOptimizeShouldReturnError() {
    // given
    String license = FileReaderUtil.readFile(
      "/license/TestUnifiedLicense_InvalidSignatureWithoutOptimize.txt");

    // when
    String errorMessage =
      embeddedOptimizeExtensionRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute(String.class, 400);

    // then
    assertThat(errorMessage.contains("Your license is invalid."), is(true));
  }

  @Test
  public void expiredUnifiedLicenseWithOptimizeShouldReturnError() {
    // given
    String license = FileReaderUtil.readFile(
      "/license/TestUnifiedLicense_ExpiredWithOptimize.txt");

    // when
    String errorMessage =
      embeddedOptimizeExtensionRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute(String.class, 400);

    // then
    assertThat(errorMessage.contains("Your license has expired."), is(true));
  }

  @Test
  public void expiredUnifiedLicenseWithoutOptimizeShouldReturnError() {
    // given
    String license = FileReaderUtil.readFile(
      "/license/TestUnifiedLicense_ExpiredWithoutOptimize.txt");

    // when
    String errorMessage =
      embeddedOptimizeExtensionRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute(String.class, 400);

    // then
    assertThat(errorMessage.contains("Your license has expired."), is(true));
  }

  private void assertResult(Response response, String customerId, OffsetDateTime validUntil, boolean isUnlimited) {
    LicenseInformationDto licenseInfo =
      response.readEntity(new GenericType<LicenseInformationDto>() {
      });
    assertThat(licenseInfo.getCustomerId(), is(customerId));
    assertThat(licenseInfo.getValidUntil(), is(validUntil));
    assertThat(licenseInfo.isUnlimited(), is(isUnlimited));
  }

}
