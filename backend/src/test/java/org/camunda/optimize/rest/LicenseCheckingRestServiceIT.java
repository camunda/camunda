package org.camunda.optimize.rest;

import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class LicenseCheckingRestServiceIT {

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

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
  public void noLicenseAvailableShouldThrowAnError() throws IOException, URISyntaxException {
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
    return new String(Files.readAllBytes(Paths.get(getClass().getResource(filePath).toURI())));
  }

}
