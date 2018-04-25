package org.camunda.optimize.service.es.retrieval;

import org.camunda.optimize.dto.optimize.query.user.CredentialsDto;
import org.camunda.optimize.dto.optimize.query.user.OptimizeUserDto;
import org.camunda.optimize.dto.optimize.query.user.PermissionsDto;
import org.camunda.optimize.dto.optimize.query.user.ProcessDefinitionPermissionsDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.it.rule.ElasticsearchIntegrationRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public class UserHandlingIT {

  private static final String PASSWORD = "fooPassword";
  public ElasticsearchIntegrationRule elasticSearchRule = new ElasticsearchIntegrationRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @After
  public void cleanUp() {
    LocalDateUtil.reset();
  }

  @Test
  public void createUserAndHasCorrectDefaultData() {
    // given
    addUserToOptimize("kermit");

    // when
    OptimizeUserDto userDto = getUser("kermit");

    // then
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime tenSecondsAgo = OffsetDateTime.now().minusSeconds(10L);
    assertThat(userDto.getId(), is("kermit"));
    assertThat(userDto.getLastLoggedIn(), nullValue());
    assertIsBetweenRange(userDto.getCreatedAt(), tenSecondsAgo, now);
    assertIsBetweenRange(userDto.getLastModified(), tenSecondsAgo, now);
    assertThat(userDto.getCreatedBy(), is(getDefaultUser()));
    assertThat(userDto.getLastModifier(), is(getDefaultUser()));
    PermissionsDto permissions = userDto.getPermissions();
    assertThat(permissions.isReadOnly(), is(false));
    assertThat(permissions.isHasAdminRights(), is(false));
    assertThat(permissions.isCanSharePublicly(), is(true));
    assertThat(permissions.getProcessDefinitions().isUseWhiteList(), is(false));
    assertThat(permissions.getProcessDefinitions().getIdList(), notNullValue());
    assertThat(permissions.getProcessDefinitions().getIdList().isEmpty(), is(true));
  }

  private void assertIsBetweenRange(OffsetDateTime actual, OffsetDateTime rangeStart, OffsetDateTime rangeEnd) {
    assertThat(actual.isAfter(rangeStart), is(true));
    assertThat(actual.isBefore(rangeEnd), is(true));
  }

  @Test
  public void createAlreadyExistingUser() {
    // given
    addUserToOptimize("kermit");

    // when
    CredentialsDto user = createUser("kermit");
    Response response = embeddedOptimizeRule.target("user")
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .post(Entity.json(user));

    // then
    assertThat(response.getStatus(), is(500));
    assertThat(response.readEntity(String.class).contains("User already exists"), is(true));
  }

  @Test
  public void gettingCurrentUserHasCorrectDefaultData() {
    // given
    addUserToOptimize("kermit");

    // when
    OptimizeUserDto userDto = getCurrentUser();

    // then
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime tenSecondsAgo = OffsetDateTime.now().minusSeconds(10L);
    assertThat(userDto.getId(), is(getDefaultUser()));
    assertThat(userDto.getLastLoggedIn(), nullValue());
    assertIsBetweenRange(userDto.getCreatedAt(), tenSecondsAgo, now);
    assertIsBetweenRange(userDto.getLastModified(), tenSecondsAgo, now);
    assertThat(userDto.getCreatedBy(), is(getDefaultUser()));
    assertThat(userDto.getLastModifier(), is(getDefaultUser()));
    PermissionsDto permissions = userDto.getPermissions();
    assertThat(permissions.isReadOnly(), is(false));
    assertThat(permissions.isHasAdminRights(), is(false));
    assertThat(permissions.isCanSharePublicly(), is(true));
    assertThat(permissions.getProcessDefinitions().isUseWhiteList(), is(false));
    assertThat(permissions.getProcessDefinitions().getIdList(), notNullValue());
    assertThat(permissions.getProcessDefinitions().getIdList().isEmpty(), is(true));
  }

  @Test
  public void updatePermissions() {
    // given
    addUserToOptimize("john");
    PermissionsDto expectedPermissions = new PermissionsDto();
    expectedPermissions.setReadOnly(false);
    expectedPermissions.setCanSharePublicly(false);
    expectedPermissions.setHasAdminRights(true);
    ProcessDefinitionPermissionsDto definitionPermission = new ProcessDefinitionPermissionsDto();
    definitionPermission.setUseWhiteList(true);
    definitionPermission.setIdList(Collections.singletonList("foo"));

    // when
    updateUserPermissions("john", expectedPermissions);
    OptimizeUserDto userDto = getUser("john");

    // then
    PermissionsDto actualPermissions = userDto.getPermissions();
    assertThat(actualPermissions, is(expectedPermissions));
  }

  @Test
  public void updatePermissionsForNonExistingUser() {
    // given
    PermissionsDto permissions = new PermissionsDto();
    permissions.setReadOnly(false);
    permissions.setCanSharePublicly(false);
    permissions.setHasAdminRights(true);
    ProcessDefinitionPermissionsDto definitionPermission = new ProcessDefinitionPermissionsDto();
    definitionPermission.setUseWhiteList(true);
    definitionPermission.setIdList(Collections.singletonList("foo"));

    // when
    Response response =
      embeddedOptimizeRule.target("user/john/permission")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .put(Entity.json(permissions));

    // then
    assertThat(response.getStatus(), is(500));
    assertThat(response.readEntity(String.class).contains("Was not able to update permissions"), is(true));
  }

  @Test
  public void updateUserPassword() {
    //given
    addUserToOptimize("john");

    // when
    Response response =
      embeddedOptimizeRule.target("user/john/password")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .put(Entity.text("newPassword"));

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
     Response authenticationResponse =
      embeddedOptimizeRule.authenticateUserRequest("john", "newPassword");
    assertThat(authenticationResponse.getStatus(),is(200));
  }

  @Test
  public void updateUserPasswordForNonExistingUser() {
    // when
    Response response =
      embeddedOptimizeRule.target("user/john/password")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .put(Entity.text("newPassword"));

    // then the status code is okay
    assertThat(response.getStatus(), is(500));
    assertThat(response.readEntity(String.class).contains("Was not able to update password"), is(true));
  }

  @Test
  public void retrieveAllUsers() {
    //given
    addUserToOptimize("john");
    addUserToOptimize("Doe");

    // when
    List<OptimizeUserDto> users = getAllUsers();

    // then
    assertThat(users.size(), is(3));
    String defaultUser = getDefaultUser();
    assertThat(users.stream().anyMatch(u -> u.getId().equals("john")), is(true));
    assertThat(users.stream().anyMatch(u -> u.getId().equals("Doe")), is(true));
    assertThat(users.stream().anyMatch(u -> u.getId().equals(defaultUser)), is(true));
  }
  
  @Test
  public void userListIsSortedById() {
    // given
    deleteUser(getDefaultUser());
    addUserToOptimize("B");
    addUserToOptimize("G");
    addUserToOptimize("A");

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("orderBy", "id");
    List<OptimizeUserDto> users = getAllUsersWithQueryParam(queryParam);

    // then
    assertThat(users.size(), is(3));
    assertThat(users.get(0).getId(), is("A"));
    assertThat(users.get(1).getId(), is("B"));
    assertThat(users.get(2).getId(), is("G"));
  }

  @Test
  public void userListIsReversed() {
    // given
    deleteUser(getDefaultUser());
    addUserToOptimize("B");
    addUserToOptimize("G");
    addUserToOptimize("A");

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("orderBy", "id");
    queryParam.put("sortOrder", "desc");
    List<OptimizeUserDto> users = getAllUsersWithQueryParam(queryParam);

    // then
    assertThat(users.size(), is(3));
    assertThat(users.get(2).getId(), is("G"));
    assertThat(users.get(1).getId(), is("B"));
    assertThat(users.get(0).getId(), is("A"));
  }

  @Test
  public void userListIsCutByAnOffset() {
    // given
    deleteUser(getDefaultUser());
    addUserToOptimize("B");
    addUserToOptimize("G");
    addUserToOptimize("A");

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("orderBy", "id");
    queryParam.put("resultOffset", 1);
    List<OptimizeUserDto> users = getAllUsersWithQueryParam(queryParam);

    // then
    assertThat(users.size(), is(2));
    assertThat(users.get(0).getId(), is("B"));
    assertThat(users.get(1).getId(), is("G"));
  }

  @Test
  public void userListIsCutByMaxResults() {
    // given
    deleteUser(getDefaultUser());
    addUserToOptimize("B");
    addUserToOptimize("G");
    addUserToOptimize("A");

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("orderBy", "id");
    queryParam.put("numResults", 2);
    List<OptimizeUserDto> users = getAllUsersWithQueryParam(queryParam);

    // then
    assertThat(users.size(), is(2));
    assertThat(users.get(0).getId(), is("A"));
    assertThat(users.get(1).getId(), is("B"));
  }

  @Test
  public void deleteUser() {
    // given
    addUserToOptimize("john");
    addUserToOptimize("Doe");

    // when
    deleteUser("john");
    deleteUser("Doe");
    deleteUser(getDefaultUser());

    // then
    List<OptimizeUserDto> users = getAllUsers();
    assertThat(users.size(), is(0));
  }

  @Test
  public void deleteUserNonExistingUser() {
    // when
    Response response =
        embeddedOptimizeRule.target("user/withId/foo")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .delete();

    // then
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void getUserForNonExistingIdThrowsError() {
    // when
    Response response =
      embeddedOptimizeRule.target("user/withId/FooId")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(500));
    assertThat(response.readEntity(String.class).contains("User does not exist."), is(true));
  }

  @Test
  public void updatePasswordWithEmptyPassword() {
     //given
    addUserToOptimize("john");

    // when
    Response response =
      embeddedOptimizeRule.target("user/john/password")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .put(Entity.text(""));

    // then the status code is okay
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void createUserWithEmptyPassword() {
    // given
    CredentialsDto user = createUser("john");
    user.setPassword("");

    // when
    Response response = embeddedOptimizeRule.target("user")
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .post(Entity.json(user));

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void createUserWithEmptyUserName() {
    // given
    CredentialsDto user = createUser("");

    // when
    Response response = embeddedOptimizeRule.target("user")
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .post(Entity.json(user));

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void updatePermissionsWithNullDefinitionPermission() {
    // given
    addUserToOptimize("john");
    PermissionsDto permissions = new PermissionsDto();
    permissions.setProcessDefinitions(null);

    // when
    Response response =
      embeddedOptimizeRule.target("user/john/permission")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .put(Entity.json(permissions));

    // then the status code is okay
    assertThat(response.getStatus(), is(500));
  }

  private String getDefaultUser() {
    return embeddedOptimizeRule.getConfigurationService().getDefaultUser();
  }

  private void addUserToOptimize(String userId) {
    CredentialsDto user = createUser(userId);
    Response response = embeddedOptimizeRule.target("user")
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .post(Entity.json(user));

    assertThat(response.getStatus(), is(204));
  }

  private CredentialsDto createUser(String userId) {
    CredentialsDto credentialsDto = new CredentialsDto();
    credentialsDto.setId(userId);
    credentialsDto.setPassword(PASSWORD);
    return credentialsDto;
  }

  private OptimizeUserDto getUser(String userId) {
    Response response =
      embeddedOptimizeRule.target("user/withId/" + userId)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get();

    assertThat(response.getStatus(), is(200));
    return response.readEntity(OptimizeUserDto.class);
  }

  private void deleteUser(String userId) {
     Response response =
        embeddedOptimizeRule.target("user/withId/" + userId)
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .delete();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  private void updateUserPermissions(String userId, PermissionsDto permissions) {
    Response response =
      embeddedOptimizeRule.target("user/" + userId + "/permission")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .put(Entity.json(permissions));

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  private OptimizeUserDto getCurrentUser() {
    Response response =
      embeddedOptimizeRule.target("user/currentUser")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get();

    assertThat(response.getStatus(), is(200));
    return response.readEntity(OptimizeUserDto.class);
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

  private List<OptimizeUserDto> getAllUsersWithQueryParam(Map<String, Object> queryParams) {
    WebTarget webTarget = embeddedOptimizeRule.target("user");
    for (Map.Entry<String, Object> queryParam : queryParams.entrySet()) {
      webTarget = webTarget.queryParam(queryParam.getKey(), queryParam.getValue());
    }

    Response response =
      webTarget
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get();

    assertThat(response.getStatus(), is(200));
    return response.readEntity(new GenericType<List<OptimizeUserDto>>() {
    });
  }
}
