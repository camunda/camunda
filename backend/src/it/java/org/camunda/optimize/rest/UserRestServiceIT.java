package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.user.CredentialsDto;
import org.camunda.optimize.dto.optimize.query.user.OptimizeUserDto;
import org.camunda.optimize.dto.optimize.query.user.PermissionsDto;
import org.camunda.optimize.test.it.rule.ElasticsearchIntegrationRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public class UserRestServiceIT {

  public ElasticsearchIntegrationRule elasticSearchRule = new ElasticsearchIntegrationRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void createNewUserWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule.target("user")
        .request()
        .post(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewUser() {
    // given
    CredentialsDto dummyUser = createDummyUser();

    // when
    Response response =
      embeddedOptimizeRule.target("user")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(dummyUser));

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void updateUserPermissionsWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule.target("user/1/permission")
        .request()
        .put(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void updateUserPermissions() {
    //given
    String userId = addDummyUserToOptimize();

    // when
    PermissionsDto permissions = new PermissionsDto();
    Response response =
      embeddedOptimizeRule.target("user/" + userId + "/permission")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .put(Entity.json(permissions));

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }
  
  @Test
  public void updateUserPasswordWithoutAuthentication() {
    // given
    String userId = addDummyUserToOptimize();

    // when
    Response response =
      embeddedOptimizeRule.target("user/" + userId + "/password")
        .request()
        .put(Entity.text("newPassword"));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void updateUserPassword() {
    //given
    String userId = addDummyUserToOptimize();

    // when
    Response response =
      embeddedOptimizeRule.target("user/" + userId + "/password")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .put(Entity.text("newPassword"));

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void getStoredUsersWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule.target("user")
        .request()
        .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getStoredUsers() {
    //given
    String id = addDummyUserToOptimize();

    // when
    List<OptimizeUserDto> users = getAllUsers();

    // then
    assertThat(users.size(), is(2));
    assertThat(users.stream().anyMatch(u -> u.getId().equals(id)), is(true));
  }

  @Test
  public void getUserWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule.target("user/withId/fooId")
        .request()
        .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getUser() {
    //given
    String id = addDummyUserToOptimize();

    // when
    Response response =
      embeddedOptimizeRule.target("user/withId/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    OptimizeUserDto user =
      response.readEntity(OptimizeUserDto.class);
    assertThat(user, is(notNullValue()));
    assertThat(user.getId(), is(id));
  }

  @Test
  public void getCurrentUserWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule.target("user/currentUser")
        .request()
        .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getCurrentUser() {
    // when
    Response response =
      embeddedOptimizeRule.target("user/currentUser")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    OptimizeUserDto user =
      response.readEntity(OptimizeUserDto.class);
    assertThat(user, is(notNullValue()));
    assertThat(user.getId(), is(embeddedOptimizeRule.getConfigurationService().getDefaultUser()));
  }

  @Test
  public void deleteUserWithoutAuthentication() {
    // when
    Response response =
        embeddedOptimizeRule.target("user/withId/1124")
            .request()
            .delete();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void deleteNewUser() {
    // given
    String userId = embeddedOptimizeRule.getConfigurationService().getDefaultUser();

    // when
    Response response =
        embeddedOptimizeRule.target("user/withId/" + userId)
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .delete();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getAllUsers().size(), is(0));
  }

  private String addDummyUserToOptimize() {
    CredentialsDto dummyUser = createDummyUser();
    embeddedOptimizeRule.target("user")
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .post(Entity.json(dummyUser));

    return dummyUser.getId();
  }

  private CredentialsDto createDummyUser() {
    CredentialsDto credentialsDto = new CredentialsDto();
    credentialsDto.setId("fooId");
    credentialsDto.setPassword("fooPassword");
    return credentialsDto;
  }

  private List<OptimizeUserDto> getAllUsers() {
    Response response =
      embeddedOptimizeRule.target("user")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get();

    assertThat(response.getStatus(), is(200));
    return response.readEntity(new GenericType<List<OptimizeUserDto>>() {
    });
  }
}
