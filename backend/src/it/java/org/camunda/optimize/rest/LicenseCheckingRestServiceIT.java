/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.LicenseInformationDto;
import org.camunda.optimize.service.license.LicenseManager;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class LicenseCheckingRestServiceIT {

  public EngineIntegrationRule engineIntegrationRule = new EngineIntegrationRule();
  public static ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private static final String CUSTOMER_ID = "schrottis inn";
  private static OffsetDateTime VALID_UNTIL;

  private static DateTimeFormatter sdf = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineIntegrationRule).around(embeddedOptimizeRule);

  @BeforeClass
  public static void init() {
    VALID_UNTIL = OffsetDateTime.parse("9999-01-01T00:00:00.000+0100", sdf);
  }

  @Test
  public void validLegacyLicenseShouldBeAccepted() throws IOException, URISyntaxException {
    // given
    String license = readFileToString("/license/ValidTestLicense.txt");

    // when
    Response response =
      embeddedOptimizeRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute();

    // then
    assertThat(response.getStatus(), is(200));
    assertResult(response, CUSTOMER_ID, VALID_UNTIL, false);
  }

  @Test
  public void unsecuredLicenseEndpointsIgnoresInvalidAuthCookie() throws IOException, URISyntaxException {
    // given
    String license = readFileToString("/license/TestLegacyLicense_Valid.txt");
    // when
    Response response = embeddedOptimizeRule.getRequestExecutor()
      .withoutAuthentication()
      .addSingleCookie(AuthCookieService.OPTIMIZE_AUTHORIZATION, "invalid")
      .buildValidateAndStoreLicenseRequest(license)
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
    assertResult(response, CUSTOMER_ID, VALID_UNTIL, false);
  }

  @Test
  public void unlimitedValidLegacyLicenseShouldBeAccepted() throws IOException, URISyntaxException {
    // given
    String license = readFileToString("/license/TestLegacyLicense_Unlimited.txt");

    // when
    Response response =
      embeddedOptimizeRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute();

    // then
    assertThat(response.getStatus(), is(200));
    assertResult(response, CUSTOMER_ID, null, true);
  }

  @Test
  public void storedLicenseCanBeValidated() throws IOException, URISyntaxException {
    // given
    String license = readFileToString("/license/TestLegacyLicense_Valid.txt");
    Response response =
      embeddedOptimizeRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute();
    assertThat(response.getStatus(), is(200));

    // when
    response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildValidateLicenseRequest()
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void invalidLegacyLicenseShouldThrowAnError() throws IOException, URISyntaxException {
    // given
    String license = readFileToString("/license/TestLegacyLicense_Invalid.txt");

    // when
    String errorMessage =
      embeddedOptimizeRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute(String.class, 400);

    // then
    assertThat(errorMessage.contains("License Key has wrong format."), is(true));
  }

  @Test
  public void expiredLegacyLicenseShouldThrowAnError() throws IOException, URISyntaxException {
    // given
    String license = readFileToString("/license/TestLegacyLicense_ExpiredDate.txt");

    // when
    String errorMessage =
      embeddedOptimizeRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute(String.class, 400);

    // then
    assertThat(errorMessage.contains("Your license has expired."), is(true));
  }

  @Test
  public void noLicenseAvailableShouldThrowAnError() {
    // to ensure license is refreshed from file and elasticsearch
    embeddedOptimizeRule.getApplicationContext().getBean(LicenseManager.class).init();
    // when
    String errorMessage = embeddedOptimizeRule
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
  public void notValidLicenseAndIWantToSeeRootPage() throws IOException, URISyntaxException {
    // given
    String license = readFileToString("/license/TestLegacyLicense_Valid.txt");

    // when
    Response response =
      embeddedOptimizeRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void validUnlimitedUnifiedLicenseWithOptimizeShouldBeAccepted() throws IOException, URISyntaxException {
    // given
    String license = readFileToString("/license/TestUnifiedLicense_UnlimitedWithOptimize.txt");

    // when
    Response response =
      embeddedOptimizeRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void validLimitedUnifiedLicenseWithOptimizeShouldBeAccepted() throws IOException, URISyntaxException {
    // given
    String license = readFileToString("/license/TestUnifiedLicense_LimitedWithOptimize.txt");

    // when
    Response response =
      embeddedOptimizeRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void validUnlimitedUnifiedLicenseWithoutOptimizeShouldReturnError() throws IOException, URISyntaxException {
    // given
    String license = readFileToString("/license/TestUnifiedLicense_UnlimitedWithoutOptimize.txt");

    // when
    String errorMessage =
      embeddedOptimizeRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute(String.class, 400);

    // then
    assertThat(errorMessage.contains("Your license is invalid."), is(true));
  }

  @Test
  public void validLimitedUnifiedLicenseWithoutOptimizeShouldReturnError() throws IOException, URISyntaxException {
    // given
    String license = readFileToString("/license/TestUnifiedLicense_LimitedWithoutOptimize.txt");

    // when
    String errorMessage =
      embeddedOptimizeRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute(String.class, 400);

    // then
    assertThat(errorMessage.contains("Your license is invalid."), is(true));
  }

  @Test
  public void invalidUnifiedLicenseWithOptimizeShouldReturnError() throws IOException, URISyntaxException {
    // given
    String license = readFileToString("/license/TestUnifiedLicense_InvalidSignatureWithOptimize.txt");

    // when
    String errorMessage =
      embeddedOptimizeRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute(String.class, 400);

    // then
    assertThat(errorMessage.contains("Your license is invalid."), is(true));
  }

  @Test
  public void invalidUnifiedLicenseWithoutOptimizeShouldReturnError() throws IOException, URISyntaxException {
    // given
    String license = readFileToString("/license/TestUnifiedLicense_InvalidSignatureWithoutOptimize.txt");

    // when
    String errorMessage =
      embeddedOptimizeRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute(String.class, 400);

    // then
    assertThat(errorMessage.contains("Your license is invalid."), is(true));
  }

  @Test
  public void expiredUnifiedLicenseWithOptimizeShouldReturnError() throws IOException, URISyntaxException {
    // given
    String license = readFileToString("/license/TestUnifiedLicense_ExpiredWithOptimize.txt");

    // when
    String errorMessage =
      embeddedOptimizeRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute(String.class, 400);

    // then
    assertThat(errorMessage.contains("Your license has expired."), is(true));
  }

  @Test
  public void expiredUnifiedLicenseWithoutOptimizeShouldReturnError() throws IOException, URISyntaxException {
    // given
    String license = readFileToString("/license/TestUnifiedLicense_ExpiredWithoutOptimize.txt");

    // when
    String errorMessage =
      embeddedOptimizeRule.getRequestExecutor()
        .buildValidateAndStoreLicenseRequest(license)
        .execute(String.class, 400);

    // then
    assertThat(errorMessage.contains("Your license has expired."), is(true));
  }

  private String readFileToString(String filePath) throws IOException, URISyntaxException {
    return new String(Files.readAllBytes(Paths.get(getClass().getResource(filePath).toURI())), StandardCharsets.UTF_8);
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
