package org.camunda.optimize.jetty;

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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class RedirectToLicensePageIT {

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void redirectFromLoginPageToLicensePage() {
    // when
    Response response =
      embeddedOptimizeRule.rootTarget("/login").request().get();

    // then
    assertThat(response.getLocation(), is(not(nullValue())));
    URI location = response.getLocation();
    assertThat(location.getPath().startsWith("/license"), is(true));
  }

  private String readFileToString(String filePath) throws IOException, URISyntaxException {
    return new String(Files.readAllBytes(Paths.get(getClass().getResource(filePath).toURI())), StandardCharsets.UTF_8);
  }

  private void addLicenseToOptimize() throws IOException, URISyntaxException {
    String license = readFileToString("/license/ValidTestLicense.txt");
    Entity<String> entity = Entity.entity(license, MediaType.TEXT_PLAIN);

    Response response =
        embeddedOptimizeRule.target("license/validate-and-store")
            .request()
            .post(entity);
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void noRedirectFromLoginPageToLicensePageWithValidLicense() throws IOException, URISyntaxException {
    // given
    addLicenseToOptimize();

    // when
    Response response =
      embeddedOptimizeRule.rootTarget("/login").request().get();

    // then
    assertThat(response.getStatus(), is(200));
    assertThat(response.getLocation(),is(nullValue()));
  }

  @Test
  public void redirectFromRootPageToLicensePage() {
    // when
    Response response =
      embeddedOptimizeRule.rootTarget("/").request().get();

    // then
    assertThat(response.getLocation(), is(not(nullValue())));
    URI location = response.getLocation();
    assertThat(location.getPath().startsWith("/license"), is(true));
  }

  @Test
  public void noRedirectFromRootPageToLicensePageWithValidLicense() throws IOException, URISyntaxException {
    // given
    addLicenseToOptimize();

    // when
    Response response =
      embeddedOptimizeRule.rootTarget("/").request().get();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void redirectFromErrorPageToLicensePage() {
    // when
    Response response =
      embeddedOptimizeRule
        .rootTarget("/process/leadQualification:2:7f0f82b8-5255-11e7-99a3-02421525a25c/none").request().get();

    // then first redirect request should be the root page
    assertThat(response.getStatus(), is(302));
    assertThat(response.getLocation().getPath(), is("/"))

    // when I now redirect to root page
    response =
      embeddedOptimizeRule
        .rootTarget(response.getLocation().getPath()).request().get();

    // then I get a redirect to the license page
    assertThat(response.getLocation(), is(not(nullValue())));
    URI location = response.getLocation();
    assertThat(location.getPath().startsWith("/license"), is(true));
  }

  @Test
  public void noRedirectFromErrorPageToLicensePageWithValidLicense() throws IOException, URISyntaxException {
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
        .rootTarget("/").request().get();

    // then I shouldn't get a redirect to the license page
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void redirectFromIndexHtmlPageToLicensePage() {
    // when
    Response response =
      embeddedOptimizeRule.rootTarget("/index.html").request().get();

    // then
    assertThat(response.getLocation(), is(not(nullValue())));
    URI location = response.getLocation();
    assertThat(location.getPath().startsWith("/license"), is(true));
  }

  @Test
  public void noRedirectFromIndexHtmlPageToLicensePageWithValidLicense() throws IOException, URISyntaxException {
    // given
    addLicenseToOptimize();

    // when
    Response response =
      embeddedOptimizeRule.rootTarget("/index.html").request().get();

    // then
    assertThat(response.getStatus(), is(200));
  }

}
