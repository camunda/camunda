package org.camunda.optimize.rest;

import camundafeel.de.odysseus.el.tree.impl.Cache;
import org.camunda.optimize.dto.optimize.query.sharing.SharingDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class SharingRestServiceIT {

  public static final String BEARER = "Bearer ";
  public static final String SHARE = "share";
  public static final String REPORT_ID = "fake";

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void createNewShareWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule.target(SHARE)
        .request()
        .post(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewShare() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    // when
    Response response =
      embeddedOptimizeRule.target(SHARE)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(createShare()));

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    String id =
        response.readEntity(String.class);
    assertThat(id, is(notNullValue()));
  }

  @Test
  public void shareIsNotCreatedForSameResourceTwice() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    // when
    SharingDto share = createShare();
    Response response =
      embeddedOptimizeRule.target(SHARE)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(share));

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    String id =
        response.readEntity(String.class);
    assertThat(id, is(notNullValue()));

    response =
      embeddedOptimizeRule.target(SHARE)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(share));

    assertThat(id, is(response.readEntity(String.class)));
  }

  @Test
  public void deleteShareWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule.target(SHARE + "/1124")
        .request()
        .delete();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void deleteShare() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String id = addShareForFakeReport(token);

    // when
    Response response =
      embeddedOptimizeRule.target(SHARE + "/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .delete();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getShareForReport(token, REPORT_ID), is(nullValue()));
  }

  @Test
  public void findShareForeReport() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String id = addShareForFakeReport(token);

    //when
    SharingDto fake = getShareForReport(token, REPORT_ID);

    //then
    assertThat(fake, is(notNullValue()));
    assertThat(fake.getId(), is(id));
  }

  @Test
  public void findShareForeReportWithoutAuthentication() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    addShareForFakeReport(token);

    Response response = findShareForReport(null, REPORT_ID);

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  private SharingDto getShareForReport(String token, String reportId) {
    Response response = findShareForReport(token, reportId);
    return response.readEntity(SharingDto.class);
  }

  private Response findShareForReport(String token, String reportId) {
    return embeddedOptimizeRule.target(SHARE + "/report/" + reportId)
      .request()
      .header(HttpHeaders.AUTHORIZATION, BEARER + token)
      .get();
  }

  private String addShareForFakeReport(String token) {
    SharingDto share = createShare();
    Response response =
      embeddedOptimizeRule.target(SHARE)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(share));

    return response.readEntity(String.class);
  }

  private SharingDto createShare() {
    SharingDto sharingDto = new SharingDto();
    sharingDto.setResourceId(REPORT_ID);
    return sharingDto;
  }


}
