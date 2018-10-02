package org.camunda.optimize.service.security;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_RESOURCES_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GLOBAL;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_REVOKE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.READ_HISTORY_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class SessionServiceIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void grantGlobalAccessForAllDefinitions() throws IOException {
    //given
    addKermitUserAndGrantAccessToOptimize();
    addGlobalAuthorizationForAllDefinitions();
    deploySimpleProcessDefinition("aprocess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    List<ProcessDefinitionOptimizeDto> definitions = retrieveProcessDefinitionsAsKermitUser();

    //then
    assertThat(definitions.size(), is(1));
  }

  @Test
  public void revokeAllDefinitionAuthorizationsForGroup() throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    addGlobalAuthorizationForAllDefinitions();
    revokeAllDefinitionAuthorizationsForKermitGroup();
    deploySimpleProcessDefinition("aprocess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ProcessDefinitionOptimizeDto> definitions = retrieveProcessDefinitionsAsKermitUser();

    // then
    assertThat(definitions.size(), is(0));
  }

  @Test
  public void grantAllDefinitionAuthorizationsForGroup() throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    grantAllDefinitionAuthorizationsForKermitGroup();
    String expectedDefinitionId = deploySimpleProcessDefinition("aprocess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ProcessDefinitionOptimizeDto> definitions = retrieveProcessDefinitionsAsKermitUser();

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(expectedDefinitionId));
  }

  @Test
  public void revokeSingleDefinitionAuthorizationForGroup() throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    grantAllDefinitionAuthorizationsForKermitGroup();
    revokeSingleDefinitionAuthorizationsForKermitGroup("aprocess");
    deploySimpleProcessDefinition("aprocess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ProcessDefinitionOptimizeDto> definitions = retrieveProcessDefinitionsAsKermitUser();

    // then
    assertThat(definitions.size(), is(0));
  }

  @Test
  public void grantSingleDefinitionAuthorizationsForGroup() throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    grantSingleDefinitionAuthorizationForKermitGroup("aprocess");
    String expectedDefinitionId = deploySimpleProcessDefinition("aprocess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ProcessDefinitionOptimizeDto> definitions = retrieveProcessDefinitionsAsKermitUser();

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(expectedDefinitionId));
  }

  @Test
  public void revokeAllDefinitionAuthorizationsForUser() throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    addGlobalAuthorizationForAllDefinitions();
    revokeAllDefinitionAuthorizationsForKermit();
    deploySimpleProcessDefinition("aprocess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ProcessDefinitionOptimizeDto> definitions = retrieveProcessDefinitionsAsKermitUser();

    // then
    assertThat(definitions.size(), is(0));
  }

  @Test
  public void grantAllDefinitionAuthorizationsForUser() throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    grantAllDefinitionAuthorizationsForKermit();
    String expectedDefinitionId = deploySimpleProcessDefinition("aprocess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ProcessDefinitionOptimizeDto> definitions = retrieveProcessDefinitionsAsKermitUser();

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(expectedDefinitionId));
  }

  @Test
  public void revokeSingleDefinitionAuthorizationForUser() throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    grantAllDefinitionAuthorizationsForKermit();
    revokeSingleDefinitionAuthorizationsForKermit("aprocess");
    deploySimpleProcessDefinition("aprocess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ProcessDefinitionOptimizeDto> definitions = retrieveProcessDefinitionsAsKermitUser();

    // then
    assertThat(definitions.size(), is(0));
  }

  @Test
  public void grantSingleDefinitionAuthorizationsForUser() throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    grantSingleDefinitionAuthorizationForKermit("aprocess");
    String expectedDefinitionId = deploySimpleProcessDefinition("aprocess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ProcessDefinitionOptimizeDto> definitions = retrieveProcessDefinitionsAsKermitUser();

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(expectedDefinitionId));
  }

  @Test
  public void grantAndRevokeSeveralTimes() throws IOException {
    //given
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    addGlobalAuthorizationForAllDefinitions();
    deploySimpleProcessDefinition("aprocess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    List<ProcessDefinitionOptimizeDto> definitions = retrieveProcessDefinitionsAsKermitUser();

    //then
    assertThat(definitions.size(), is(1));

    // when
    revokeAllDefinitionAuthorizationsForKermitGroup();
    definitions = retrieveProcessDefinitionsAsKermitUser();

    // then
    assertThat(definitions.size(), is(0));

    // when
    grantAllDefinitionAuthorizationsForKermitGroup();
    definitions = retrieveProcessDefinitionsAsKermitUser();

    // then
    assertThat(definitions.size(), is(1));

    // when
    revokeSingleDefinitionAuthorizationsForKermitGroup("aprocess");
    definitions = retrieveProcessDefinitionsAsKermitUser();

    // then
    assertThat(definitions.size(), is(0));

    // when
    grantSingleDefinitionAuthorizationForKermitGroup("aprocess");
    definitions = retrieveProcessDefinitionsAsKermitUser();

    // then
    assertThat(definitions.size(), is(1));

    // when
    revokeAllDefinitionAuthorizationsForKermit();
    definitions = retrieveProcessDefinitionsAsKermitUser();

    // then
    assertThat(definitions.size(), is(0));

    // when
    grantAllDefinitionAuthorizationsForKermit();
    definitions = retrieveProcessDefinitionsAsKermitUser();

    // then
    assertThat(definitions.size(), is(1));

    // when
    revokeSingleDefinitionAuthorizationsForKermit("aprocess");
    definitions = retrieveProcessDefinitionsAsKermitUser();

    // then
    assertThat(definitions.size(), is(0));

    // when
    grantSingleDefinitionAuthorizationForKermit("aprocess");
    definitions = retrieveProcessDefinitionsAsKermitUser();

    // then
    assertThat(definitions.size(), is(1));
  }


  @Test
  public void authorizationForOneGroupIsNotTransferredToOtherGroups() throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    createKermitGroupAndAddKermitToThatGroup();
    grantAllDefinitionAuthorizationsForKermitGroup();
    engineRule.addUser("genzo", "genzo");
    engineRule.grantUserOptimizeAccess("genzo");
    engineRule.createGroup("genzoGroup", "Group", "foo");
    engineRule.addUserToGroup("genzo", "genzoGroup");

    deploySimpleProcessDefinition("aprocess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ProcessDefinitionOptimizeDto> genzosDefinitions =
            embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetProcessDefinitionsRequest()
            .withUserAuthentication("genzo", "genzo")
            .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);

    // then
    assertThat(genzosDefinitions.size(), is(0));
  }

  @Test
  public void readAndReadHistoryPermissionsGrandDefinitionAccess() throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    grantAllDefinitionAuthorizationsForUserWithReadPermission("kermit");
    String expectedDefinitionId = deploySimpleProcessDefinition("aprocess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ProcessDefinitionOptimizeDto> definitions = retrieveProcessDefinitionsAsKermitUser();

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(expectedDefinitionId));
  }

  @Test
  public void grantAuthorizationToSingleDefinitionTransfersToAllVersions() throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    grantSingleDefinitionAuthorizationForKermit("aprocess");
    deploySimpleProcessDefinition("aprocess");
    deploySimpleProcessDefinition("aprocess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ProcessDefinitionOptimizeDto> definitions = retrieveProcessDefinitionsAsKermitUser();

    // then
    assertThat(definitions.size(), is(2));
  }

  private void addGlobalAuthorizationForAllDefinitions() {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GLOBAL);
    authorizationDto.setUserId("*");
    engineRule.createAuthorization(authorizationDto);
  }

  private void grantAllDefinitionAuthorizationsForKermitGroup() {
    grantAllDefinitionAuthorizationsForGroup("kermitGroup");
  }

  private void grantSingleDefinitionAuthorizationForKermitGroup(String definitionKey) {
    grantSingleDefinitionAuthorizationsForGroup("kermitGroup", definitionKey);
  }

  private void revokeAllDefinitionAuthorizationsForKermitGroup() {
    revokeAllDefinitionAuthorizationsForGroup("kermitGroup");
  }

  private void revokeSingleDefinitionAuthorizationsForKermitGroup(String definitionKey) {
    revokeSingleDefinitionAuthorizationsForGroup("kermitGroup", definitionKey);
  }

  private void grantAllDefinitionAuthorizationsForGroup(String groupId) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId(groupId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void grantSingleDefinitionAuthorizationsForGroup(String groupId, String definitionKey) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setGroupId(groupId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void revokeAllDefinitionAuthorizationsForGroup(String groupId) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setGroupId(groupId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void revokeSingleDefinitionAuthorizationsForGroup(String groupId, String definitionKey) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setGroupId(groupId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void grantAllDefinitionAuthorizationsForKermit() {
    grantAllDefinitionAuthorizationsForUser("kermit");
  }

  private void grantSingleDefinitionAuthorizationForKermit(String definitionKey) {
    grantSingleDefinitionAuthorizationsForUser("kermit", definitionKey);
  }

  private void revokeAllDefinitionAuthorizationsForKermit() {
    revokeAllDefinitionAuthorizationsForUser("kermit");
  }

  private void revokeSingleDefinitionAuthorizationsForKermit(String definitionKey) {
    revokeSingleDefinitionAuthorizationsForUser("kermit", definitionKey);
  }

  private void grantAllDefinitionAuthorizationsForUser(String userId) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(userId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void grantSingleDefinitionAuthorizationsForUser(String userId, String definitionKey) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(userId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void grantAllDefinitionAuthorizationsForUserWithReadPermission(String userId) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    List<String> permissions = new ArrayList<>();
    permissions.add(READ_HISTORY_PERMISSION);
    authorizationDto.setPermissions(permissions);
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(userId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void revokeAllDefinitionAuthorizationsForUser(String userId) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setUserId(userId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void revokeSingleDefinitionAuthorizationsForUser(String userId, String definitionKey) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_REVOKE);
    authorizationDto.setUserId(userId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void addKermitUserAndGrantAccessToOptimize() {
    engineRule.addUser("kermit", "kermit");
    engineRule.grantUserOptimizeAccess("kermit");
  }

  private List<ProcessDefinitionOptimizeDto> retrieveProcessDefinitionsAsKermitUser() {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetProcessDefinitionsRequest()
            .withUserAuthentication("kermit", "kermit")
            .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);
  }

  private String deploySimpleProcessDefinition(String processId) throws IOException {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(processId)
      .startEvent()
      .endEvent()
      .done();
    return engineRule.deployProcessAndGetId(modelInstance);
  }

  private void createKermitGroupAndAddKermitToThatGroup() {
    engineRule.createGroup("kermitGroup", "Group", "foo");
    engineRule.addUserToGroup("kermit", "kermitGroup");
  }
}

