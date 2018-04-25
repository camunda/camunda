package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.LicenseInformationDto;
import org.camunda.optimize.test.it.rule.ElasticsearchIntegrationRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;



public class LicenseCheckingRestServiceIT {

  public static ElasticsearchIntegrationRule elasticSearchRule = new ElasticsearchIntegrationRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private static final String CUSTOMER_ID = "schrottis inn";
  private static OffsetDateTime VALID_UNTIL;

  private static DateTimeFormatter sdf = DateTimeFormatter.ofPattern(elasticSearchRule.getDateFormat());

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @BeforeClass
  public static void init() {
    VALID_UNTIL = OffsetDateTime.parse("9999-01-01T00:00:00.000+0100", sdf);
  }

  @Test
  public void validLicenseShouldBeAccepted() throws IOException, URISyntaxException {

    // given
    String license = readFileToString("/license/ValidTestLicense.txt");
    Entity<String> entity = Entity.entity(license, MediaType.TEXT_PLAIN);

    // when
    Response response =
        embeddedOptimizeRule.target("license/validate-and-store")
            .request()
            .post(entity);

    // then
    assertThat(response.getStatus(), is(200));
    assertResult(response, CUSTOMER_ID, VALID_UNTIL, false);
  }

  @Test
  public void unlimitedValidLicenseShouldBeAccepted() throws IOException, URISyntaxException {

    // given
    String license = readFileToString("/license/UnlimitedTestLicense.txt");
    Entity<String> entity = Entity.entity(license, MediaType.TEXT_PLAIN);

    // when
    Response response =
        embeddedOptimizeRule.target("license/validate-and-store")
            .request()
            .post(entity);

    // then
    assertThat(response.getStatus(), is(200));
    assertResult(response, CUSTOMER_ID, null, true);
  }

  @Test
  public void storedLicenseCanBeValidated() throws IOException, URISyntaxException {

    // given
    String license = readFileToString("/license/ValidTestLicense.txt");
    Entity<String> entity = Entity.entity(license, MediaType.TEXT_PLAIN);
    Response response =
        embeddedOptimizeRule.target("license/validate-and-store")
            .request()
            .post(entity);
    assertThat(response.getStatus(), is(200));

    // when
    response =
        embeddedOptimizeRule.target("license/validate")
            .request()
            .get();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void invalidLicenseShouldThrowAnError() throws IOException, URISyntaxException {
    // given
    String license = readFileToString("/license/InvalidTestLicense.txt");
    Entity<String> entity = Entity.entity(license, MediaType.TEXT_PLAIN);

    // when
    Response response =
        embeddedOptimizeRule.target("license/validate-and-store")
            .request()
            .post(entity);

    // then
    assertThat(response.getStatus(), is(500));
    String errorMessage = response.readEntity(String.class);
    assertThat(errorMessage.contains("Cannot verify signature"), is(true));
  }

  @Test
  public void expiredLicenseShouldThrowAnError() throws IOException, URISyntaxException {
    // given
    String license = readFileToString("/license/ExpiredDateTestLicense.txt");
    Entity<String> entity = Entity.entity(license, MediaType.TEXT_PLAIN);

    // when
    Response response =
        embeddedOptimizeRule.target("license/validate-and-store")
            .request()
            .post(entity);

    // then
    assertThat(response.getStatus(), is(500));
    String errorMessage = response.readEntity(String.class);
    assertThat(errorMessage.contains("Your license has expired."), is(true));
  }

  @Test
  public void noLicenseAvailableShouldThrowAnError() {
    // when
    Response response =
        embeddedOptimizeRule.target("license/validate")
            .request()
            .get();

    // then
    assertThat(response.getStatus(), is(500));
    String errorMessage = response.readEntity(String.class);
    assertThat(errorMessage.contains("No license stored in Optimize. Please provide a valid Optimize license"), is(true));
  }

  @Test
  public void notValidLicenseAndIWantToSeeRootPage() throws IOException, URISyntaxException {

    // given
    String license = readFileToString("/license/ValidTestLicense.txt");
    Entity<String> entity = Entity.entity(license, MediaType.TEXT_PLAIN);

    // when
    Response response =
        embeddedOptimizeRule.target("license/validate-and-store")
            .request()
            .post(entity);

    // then
    assertThat(response.getStatus(), is(200));
  }

  private String readFileToString(String filePath) throws IOException, URISyntaxException {
    return new String(Files.readAllBytes(Paths.get(getClass().getResource(filePath).toURI())), StandardCharsets.UTF_8);
  }

  private void assertResult(Response response, String customerId, OffsetDateTime validUntil, boolean isUnlimited) {
    LicenseInformationDto licenseInfo =
      response.readEntity(new GenericType<LicenseInformationDto>() { });
    assertThat(licenseInfo.getCustomerId(), is(customerId));
    assertThat(licenseInfo.getValidUntil(), is(validUntil));
    assertThat(licenseInfo.isUnlimited(), is(isUnlimited));
  }

}
