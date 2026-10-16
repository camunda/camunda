/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.unboundid.scim2.common.messages.ErrorResponse;
import com.unboundid.scim2.common.messages.ListResponse;
import com.unboundid.scim2.common.types.GroupResource;
import io.camunda.gateway.scim.ScimJson;
import io.camunda.gateway.scim.ScimSources;
import io.camunda.gateway.scim.error.ScimExceptionHandler;
import io.camunda.gateway.scim.mapper.ScimGroupIdGenerator;
import io.camunda.gateway.scim.mapper.ScimGroupMapper;
import io.camunda.gateway.scim.mapper.ScimGroupPatchTranslator;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.query.GroupMemberQuery;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.service.GroupServices;
import io.camunda.service.GroupServices.GroupAttribute;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.service.GroupServices.GroupMemberDTO;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ScimGroupControllerTest {

  private static final String SCIM_JSON = "application/scim+json";
  private static final String PATCH_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:PatchOp";
  private static final CamundaAuthentication AUTHENTICATION =
      CamundaAuthentication.of(a -> a.user("scim-client"));
  private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_~@.+-]+$");

  @Mock private ServiceRegistry serviceRegistry;
  @Mock private GroupServices groupServices;
  @Mock private CamundaAuthenticationProvider authenticationProvider;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    lenient().when(serviceRegistry.groupServices("default")).thenReturn(groupServices);
    lenient().when(authenticationProvider.getCamundaAuthentication()).thenReturn(AUTHENTICATION);
    final var mapper = new ScimGroupMapper();
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new ScimGroupController(
                    serviceRegistry,
                    authenticationProvider,
                    mapper,
                    new ScimGroupPatchTranslator(mapper),
                    new ScimGroupIdGenerator(new IdentifierValidator(ID_PATTERN, ID_PATTERN)),
                    new ScimSources(List.of())))
            .setControllerAdvice(new ScimExceptionHandler())
            .build();
  }

  @Test
  void shouldCreateGroupWithServerAssignedIdAndRespondWithAppliedMembers() throws Exception {
    // given
    when(groupServices.createGroup(any(), eq(AUTHENTICATION)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new GroupRecord()
                    .setGroupId("engineering-ops")
                    .setName("Engineering & Ops")
                    .setExternalId("ext-1")));
    when(groupServices.setMembers(
            any(), any(), anyCollection(), eq("default"), eq(true), eq(AUTHENTICATION)))
        .thenReturn(CompletableFuture.completedFuture(new GroupRecord()));

    // when
    final var response =
        perform(
            post("/scim/v2/Groups")
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"schemas":["urn:ietf:params:scim:schemas:core:2.0:Group"],
                     "displayName":"Engineering & Ops","externalId":"ext-1",
                     "members":[{"value":"alice"},{"value":"bob@x.io"}]}
                    """));

    // then
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getContentType()).startsWith(SCIM_JSON);
    assertThat(response.getHeader(HttpHeaders.LOCATION))
        .isEqualTo("http://localhost/scim/v2/Groups/engineering-ops");
    final var group = ScimJson.read(response.getContentAsString(), GroupResource.class);
    assertThat(group.getId()).isEqualTo("engineering-ops");
    assertThat(group.getDisplayName()).isEqualTo("Engineering & Ops");
    assertThat(group.getExternalId()).isEqualTo("ext-1");
    assertThat(group.getMeta().getResourceType()).isEqualTo("Group");
    assertThat(group.getMembers())
        .extracting(m -> m.getValue(), m -> m.getRef().toString())
        .containsExactly(
            tuple("alice", "http://localhost/scim/v2/Users/alice"),
            tuple("bob@x.io", "http://localhost/scim/v2/Users/bob@x.io"));

    final var dto = ArgumentCaptor.forClass(GroupDTO.class);
    final var order = inOrder(groupServices);
    order.verify(groupServices).createGroup(dto.capture(), eq(AUTHENTICATION));
    order
        .verify(groupServices)
        .setMembers(
            "engineering-ops",
            EntityType.USER,
            List.of("alice", "bob@x.io"),
            "default",
            true,
            AUTHENTICATION);
    assertThat(dto.getValue())
        .isEqualTo(
            new GroupDTO("engineering-ops", "Engineering & Ops", "", "ext-1", Map.of(), "default"));
  }

  @Test
  void shouldNotSetMembersWhenCreatedWithoutMembers() throws Exception {
    // given
    when(groupServices.createGroup(any(), eq(AUTHENTICATION)))
        .thenReturn(
            CompletableFuture.completedFuture(new GroupRecord().setGroupId("eng").setName("Eng")));

    // when
    final var response =
        perform(
            post("/scim/v2/Groups").contentType(SCIM_JSON).content("{\"displayName\":\"Eng\"}"));

    // then
    assertThat(response.getStatus()).isEqualTo(201);
    verify(groupServices, never())
        .setMembers(any(), any(), anyCollection(), any(), anyBoolean(), any());
  }

  @Test
  void shouldCreateGroupWhenEngineRespondsOnAnotherThread() throws Exception {
    // given
    when(groupServices.createGroup(any(), eq(AUTHENTICATION)))
        .thenReturn(
            CompletableFuture.supplyAsync(
                () -> new GroupRecord().setGroupId("eng").setName("Eng"),
                CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS)));
    when(groupServices.setMembers(
            any(), any(), anyCollection(), eq("default"), eq(true), eq(AUTHENTICATION)))
        .thenReturn(
            CompletableFuture.supplyAsync(
                GroupRecord::new, CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS)));

    // when
    final var response =
        perform(
            post("/scim/v2/Groups")
                .contentType(SCIM_JSON)
                .content("{\"displayName\":\"Eng\",\"members\":[{\"value\":\"alice\"}]}"));

    // then
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getHeader(HttpHeaders.LOCATION))
        .isEqualTo("http://localhost/scim/v2/Groups/eng");
    final var group = ScimJson.read(response.getContentAsString(), GroupResource.class);
    assertThat(group.getMembers().getFirst().getRef())
        .hasToString("http://localhost/scim/v2/Users/alice");
  }

  @Test
  void shouldFallBackToExternalIdThenUuidWhenDisplayNameHasNoUsableCharacters() throws Exception {
    // given
    when(groupServices.createGroup(any(), eq(AUTHENTICATION)))
        .thenReturn(CompletableFuture.completedFuture(new GroupRecord().setName("???")));

    // when
    perform(
        post("/scim/v2/Groups")
            .contentType(SCIM_JSON)
            .content("{\"displayName\":\"???\",\"externalId\":\"Ext 7\"}"));
    perform(post("/scim/v2/Groups").contentType(SCIM_JSON).content("{\"displayName\":\"???\"}"));

    // then
    final var dto = ArgumentCaptor.forClass(GroupDTO.class);
    verify(groupServices, times(2)).createGroup(dto.capture(), any());
    assertThat(dto.getAllValues().get(0).groupId()).isEqualTo("ext-7");
    assertThat(dto.getAllValues().get(1).groupId())
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  }

  @Test
  void shouldMapIdCollisionToConflictWithUniquenessType() throws Exception {
    // given
    when(groupServices.createGroup(any(), eq(AUTHENTICATION)))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("group exists", Status.ALREADY_EXISTS)));

    // when
    final var response =
        perform(
            post("/scim/v2/Groups").contentType(SCIM_JSON).content("{\"displayName\":\"Eng\"}"));

    // then
    assertError(response, 409, "uniqueness");
  }

  @Test
  void shouldReportSetMembersFailureOfCreateWithoutRollback() throws Exception {
    // given
    when(groupServices.createGroup(any(), eq(AUTHENTICATION)))
        .thenReturn(
            CompletableFuture.completedFuture(new GroupRecord().setGroupId("eng").setName("Eng")));
    when(groupServices.setMembers(
            any(), any(), anyCollection(), eq("default"), eq(true), eq(AUTHENTICATION)))
        .thenReturn(
            CompletableFuture.failedFuture(new ServiceException("no such user", Status.NOT_FOUND)));

    // when
    final var response =
        perform(
            post("/scim/v2/Groups")
                .contentType(SCIM_JSON)
                .content("{\"displayName\":\"Eng\",\"members\":[{\"value\":\"ghost\"}]}"));

    // then
    assertError(response, 404, null);
    verify(groupServices, never()).deleteGroup(any(), any(), anyBoolean(), any());
  }

  @Test
  void shouldRejectCreateWithoutDisplayName() throws Exception {
    // when
    final var response =
        perform(post("/scim/v2/Groups").contentType(SCIM_JSON).content("{\"externalId\":\"x\"}"));

    // then
    assertError(response, 400, "invalidValue");
    verify(groupServices, never()).createGroup(any(), any());
  }

  @Test
  void shouldRejectNestedGroupMemberBeforeCallingTheEngine() throws Exception {
    // when
    final var response =
        perform(
            post("/scim/v2/Groups")
                .contentType(SCIM_JSON)
                .content(
                    "{\"displayName\":\"Eng\",\"members\":[{\"value\":\"g\",\"type\":\"Group\"}]}"));

    // then
    assertError(response, 400, "invalidValue");
    verify(groupServices, never()).createGroup(any(), any());
  }

  @Test
  void shouldGetGroupWithMembersFromSecondaryStorage() throws Exception {
    // given
    when(groupServices.getGroup("eng", AUTHENTICATION))
        .thenReturn(new GroupEntity(1L, "eng", "Engineering", "a description", "ext-1"));
    when(groupServices.searchMembers(any(GroupMemberQuery.class), eq(AUTHENTICATION)))
        .thenReturn(
            SearchQueryResult.of(
                new GroupMemberEntity("alice", EntityType.USER),
                new GroupMemberEntity("bob", EntityType.USER)));

    // when
    final var response = perform(get("/scim/v2/Groups/eng"));

    // then
    assertThat(response.getStatus()).isEqualTo(200);
    final var group = ScimJson.read(response.getContentAsString(), GroupResource.class);
    assertThat(group.getId()).isEqualTo("eng");
    assertThat(group.getExternalId()).isEqualTo("ext-1");
    assertThat(group.getMembers()).extracting(m -> m.getValue()).containsExactly("alice", "bob");
    assertThat(group.getMeta().getLocation()).hasToString("http://localhost/scim/v2/Groups/eng");
    final var query = ArgumentCaptor.forClass(GroupMemberQuery.class);
    verify(groupServices).searchMembers(query.capture(), eq(AUTHENTICATION));
    assertThat(query.getValue().filter().groupId()).isEqualTo("eng");
    assertThat(query.getValue().filter().memberType()).isEqualTo(EntityType.USER);
  }

  @Test
  void shouldOmitMembersAndSkipTheMemberReadWhenExcluded() throws Exception {
    // given
    when(groupServices.getGroup("eng", AUTHENTICATION))
        .thenReturn(new GroupEntity(1L, "eng", "Engineering", null, ""));

    // when
    final var response = perform(get("/scim/v2/Groups/eng").param("excludedAttributes", "members"));

    // then
    assertThat(response.getStatus()).isEqualTo(200);
    final var group = ScimJson.read(response.getContentAsString(), GroupResource.class);
    assertThat(group.getMembers()).isNull();
    assertThat(group.getExternalId()).isNull();
    verify(groupServices, never()).searchMembers(any(), any());
  }

  @Test
  void shouldAcceptAttributesParameterAndStillReturnTheFullResource() throws Exception {
    // given
    when(groupServices.getGroup("eng", AUTHENTICATION))
        .thenReturn(new GroupEntity(1L, "eng", "Engineering", null, null));
    when(groupServices.searchMembers(any(GroupMemberQuery.class), eq(AUTHENTICATION)))
        .thenReturn(SearchQueryResult.empty());

    // when
    final var response = perform(get("/scim/v2/Groups/eng").param("attributes", "displayName"));

    // then
    assertThat(response.getStatus()).isEqualTo(200);
    final var group = ScimJson.read(response.getContentAsString(), GroupResource.class);
    assertThat(group.getId()).isEqualTo("eng");
    assertThat(group.getSchemaUrns()).containsExactly(ScimGroupMapper.GROUP_SCHEMA);
    assertThat(group.getMeta()).isNotNull();
  }

  @Test
  void shouldMapNotFoundOnGet() throws Exception {
    // given
    when(groupServices.getGroup("ghost", AUTHENTICATION))
        .thenThrow(new ServiceException("nope", Status.NOT_FOUND));

    // when
    final var response = perform(get("/scim/v2/Groups/ghost"));

    // then
    assertError(response, 404, null);
  }

  @Test
  void shouldSearchByDisplayNameWithPagination() throws Exception {
    // given
    when(groupServices.search(any(GroupQuery.class), eq(AUTHENTICATION)))
        .thenReturn(SearchQueryResult.of(new GroupEntity(1L, "eng", "Engineering", null, "ext-1")));
    when(groupServices.searchMembers(any(GroupMemberQuery.class), eq(AUTHENTICATION)))
        .thenReturn(SearchQueryResult.of(new GroupMemberEntity("alice", EntityType.USER)));

    // when
    final var response =
        perform(
            get("/scim/v2/Groups")
                .param("filter", "displayName eq \"Engineering\"")
                .param("startIndex", "3")
                .param("count", "10"));

    // then
    assertThat(response.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    final var list = ScimJson.read(response.getContentAsString(), ListResponse.class);
    assertThat(list.getTotalResults()).isEqualTo(1);
    assertThat(list.getStartIndex()).isEqualTo(3);
    assertThat(list.getItemsPerPage()).isEqualTo(1);
    assertThat(list.getResources()).hasSize(1);

    final var query = ArgumentCaptor.forClass(GroupQuery.class);
    verify(groupServices).search(query.capture(), eq(AUTHENTICATION));
    assertThat(query.getValue().page().from()).isEqualTo(2);
    assertThat(query.getValue().page().size()).isEqualTo(10);
    assertThat(query.getValue().filter().nameOperations()).hasSize(1);
  }

  @Test
  void shouldListWithoutMembersWhenExcluded() throws Exception {
    // given
    when(groupServices.search(any(GroupQuery.class), eq(AUTHENTICATION)))
        .thenReturn(SearchQueryResult.of(new GroupEntity(1L, "eng", "Engineering", null, null)));

    // when
    final var response = perform(get("/scim/v2/Groups").param("excludedAttributes", "members"));

    // then
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentAsString()).doesNotContain("\"members\"");
    verify(groupServices, never()).searchMembers(any(), any());
  }

  @Test
  void shouldRejectUnsupportedFilter() throws Exception {
    // when
    final var response = perform(get("/scim/v2/Groups").param("filter", "externalId eq \"x\""));

    // then
    assertError(response, 400, "invalidFilter");
  }

  @Test
  void shouldReplaceGroupWithKeycloakPluginPayloadUsingMaskAndSetMembers() throws Exception {
    // given
    when(groupServices.updateGroup(any(), any(), eq(AUTHENTICATION)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new GroupRecord()
                    .setGroupId("eng")
                    .setName("Engineering")
                    .setDescription("kept")
                    .setExternalId("kc-1")));
    when(groupServices.setMembers(
            any(), any(), anyCollection(), eq("default"), eq(true), eq(AUTHENTICATION)))
        .thenReturn(CompletableFuture.completedFuture(new GroupRecord()));

    // when
    final var response =
        perform(
            put("/scim/v2/Groups/eng")
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"schemas":["urn:ietf:params:scim:schemas:core:2.0:Group"],
                     "id":"eng","displayName":"Engineering","externalId":"kc-1",
                     "members":[{"value":"alice","display":"Alice"},{"value":"bob"}]}
                    """));

    // then
    assertThat(response.getStatus()).isEqualTo(200);
    final var group = ScimJson.read(response.getContentAsString(), GroupResource.class);
    assertThat(group.getMembers()).extracting(m -> m.getValue()).containsExactly("alice", "bob");

    final var dto = ArgumentCaptor.forClass(GroupDTO.class);
    final var mask = ArgumentCaptor.forClass(Set.class);
    final var order = inOrder(groupServices);
    order.verify(groupServices).updateGroup(dto.capture(), mask.capture(), eq(AUTHENTICATION));
    order
        .verify(groupServices)
        .setMembers(
            "eng", EntityType.USER, List.of("alice", "bob"), "default", true, AUTHENTICATION);
    assertThat(dto.getValue())
        .isEqualTo(new GroupDTO("eng", "Engineering", "", "kc-1", Map.of(), "default"));
    assertThat(mask.getValue())
        .containsExactlyInAnyOrder(
            GroupAttribute.NAME, GroupAttribute.EXTERNAL_ID, GroupAttribute.SOURCE);
  }

  @Test
  void shouldReplaceGroupWithoutMembersByEmptyingIt() throws Exception {
    // given
    when(groupServices.updateGroup(any(), any(), eq(AUTHENTICATION)))
        .thenReturn(
            CompletableFuture.completedFuture(new GroupRecord().setGroupId("eng").setName("E")));
    when(groupServices.setMembers(
            any(), any(), anyCollection(), eq("default"), eq(true), eq(AUTHENTICATION)))
        .thenReturn(CompletableFuture.completedFuture(new GroupRecord()));

    // when
    final var response =
        perform(
            put("/scim/v2/Groups/eng").contentType(SCIM_JSON).content("{\"displayName\":\"E\"}"));

    // then
    assertThat(response.getStatus()).isEqualTo(200);
    verify(groupServices)
        .setMembers("eng", EntityType.USER, List.of(), "default", true, AUTHENTICATION);
  }

  @Test
  void shouldMapNotFoundOnReplaceWithoutSettingMembers() throws Exception {
    // given
    when(groupServices.updateGroup(any(), any(), eq(AUTHENTICATION)))
        .thenReturn(CompletableFuture.failedFuture(new ServiceException("nope", Status.NOT_FOUND)));

    // when
    final var response =
        perform(
            put("/scim/v2/Groups/ghost").contentType(SCIM_JSON).content("{\"displayName\":\"E\"}"));

    // then
    assertError(response, 404, null);
    verify(groupServices, never())
        .setMembers(any(), any(), anyCollection(), any(), anyBoolean(), any());
  }

  @Test
  void shouldPatchKeycloakPluginReplaceOfMembersDisplayNameAndExternalId() throws Exception {
    // given
    stubUpdateAndSet();

    // when
    final var response =
        perform(
            patch("/scim/v2/Groups/eng")
                .contentType(SCIM_JSON)
                .content(
                    patchBody(
                        """
                        {"op":"replace","path":"displayName","value":"Renamed"},
                        {"op":"replace","path":"externalId","value":"kc-1"},
                        {"op":"replace","path":"members","value":[{"value":"alice"}]}
                        """)));

    // then
    assertThat(response.getStatus()).isEqualTo(204);
    assertThat(response.getContentAsString()).isEmpty();
    final var mask = ArgumentCaptor.forClass(Set.class);
    final var order = inOrder(groupServices);
    order
        .verify(groupServices)
        .updateGroup(
            eq(new GroupDTO("eng", "Renamed", "", "kc-1", Map.of(), "default")),
            mask.capture(),
            eq(AUTHENTICATION));
    order
        .verify(groupServices)
        .setMembers("eng", EntityType.USER, List.of("alice"), "default", true, AUTHENTICATION);
    assertThat(mask.getValue())
        .containsExactlyInAnyOrder(
            GroupAttribute.NAME, GroupAttribute.EXTERNAL_ID, GroupAttribute.SOURCE);
  }

  @Test
  void shouldPatchKeycloakPluginRemoveMembersWithNullValueByEmptyingTheGroup() throws Exception {
    // given
    stubUpdateAndSet();

    // when
    final var response =
        perform(
            patch("/scim/v2/Groups/eng")
                .contentType(SCIM_JSON)
                .content(patchBody("{\"op\":\"remove\",\"path\":\"members\",\"value\":null}")));

    // then
    assertThat(response.getStatus()).isEqualTo(204);
    verify(groupServices)
        .setMembers("eng", EntityType.USER, List.of(), "default", true, AUTHENTICATION);
    verify(groupServices, never()).updateGroup(any(), any(), any());
  }

  @Test
  void shouldPatchEntraStyleAddRemoveAndRenameInOrder() throws Exception {
    // given
    stubUpdateAndSet();
    when(groupServices.assignMember(any(), eq("default"), eq(true), eq(AUTHENTICATION)))
        .thenReturn(CompletableFuture.completedFuture(new GroupRecord()));
    when(groupServices.removeMember(any(), eq("default"), eq(true), eq(AUTHENTICATION)))
        .thenReturn(CompletableFuture.completedFuture(new GroupRecord()));

    // when
    final var response =
        perform(
            patch("/scim/v2/Groups/eng")
                .contentType(SCIM_JSON)
                .content(
                    patchBody(
                        """
                        {"op":"Add","path":"members","value":[{"value":"alice"}]},
                        {"op":"Remove","path":"members[value eq \\"bob\\"]"},
                        {"op":"Replace","path":"displayName","value":"Eng"}
                        """)));

    // then
    assertThat(response.getStatus()).isEqualTo(204);
    final var order = inOrder(groupServices);
    order
        .verify(groupServices)
        .updateGroup(
            eq(new GroupDTO("eng", "Eng", "", "", Map.of(), "default")),
            eq(Set.of(GroupAttribute.NAME, GroupAttribute.SOURCE)),
            eq(AUTHENTICATION));
    order
        .verify(groupServices)
        .assignMember(
            new GroupMemberDTO("eng", "alice", EntityType.USER), "default", true, AUTHENTICATION);
    order
        .verify(groupServices)
        .removeMember(
            new GroupMemberDTO("eng", "bob", EntityType.USER), "default", true, AUTHENTICATION);
  }

  @Test
  void shouldTreatAlreadyAssignedMemberAsSuccess() throws Exception {
    // given
    when(groupServices.assignMember(any(), eq("default"), eq(true), eq(AUTHENTICATION)))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("assigned", Status.ALREADY_EXISTS)));

    // when
    final var response =
        perform(
            patch("/scim/v2/Groups/eng")
                .contentType(SCIM_JSON)
                .content(
                    patchBody(
                        "{\"op\":\"add\",\"path\":\"members\",\"value\":[{\"value\":\"alice\"}]}")));

    // then
    assertThat(response.getStatus()).isEqualTo(204);
  }

  @Test
  void shouldTreatMissingMemberOnRemovalAsSuccess() throws Exception {
    // given
    when(groupServices.removeMember(any(), eq("default"), eq(true), eq(AUTHENTICATION)))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException(
                    "Expected to remove entity with ID 'bob' from group with ID 'eng', but the entity is not assigned to this group.",
                    Status.NOT_FOUND)));

    // when
    final var response =
        perform(
            patch("/scim/v2/Groups/eng")
                .contentType(SCIM_JSON)
                .content(
                    patchBody("{\"op\":\"remove\",\"path\":\"members[value eq \\\"bob\\\"]\"}")));

    // then
    assertThat(response.getStatus()).isEqualTo(204);
  }

  @Test
  void shouldAnswerNotFoundWhenTheGroupIsMissingOnMemberRemoval() throws Exception {
    // given
    when(groupServices.removeMember(any(), eq("default"), eq(true), eq(AUTHENTICATION)))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException(
                    "Expected to update group with ID 'eng', but a group with this ID does not exist.",
                    Status.NOT_FOUND)));

    // when
    final var response =
        perform(
            patch("/scim/v2/Groups/eng")
                .contentType(SCIM_JSON)
                .content(
                    patchBody("{\"op\":\"remove\",\"path\":\"members[value eq \\\"bob\\\"]\"}")));

    // then
    assertError(response, 404, null);
  }

  @Test
  void shouldPatchWhenEngineRespondsOnAnotherThread() throws Exception {
    // given
    when(groupServices.assignMember(any(), eq("default"), eq(true), eq(AUTHENTICATION)))
        .thenReturn(
            CompletableFuture.supplyAsync(
                GroupRecord::new, CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS)));

    // when
    final var response =
        perform(
            patch("/scim/v2/Groups/eng")
                .contentType(SCIM_JSON)
                .content(
                    patchBody(
                        "{\"op\":\"add\",\"path\":\"members\",\"value\":[{\"value\":\"alice\"}]}")));

    // then
    assertThat(response.getStatus()).isEqualTo(204);
  }

  @Test
  void shouldRejectNestedGroupInPatchBeforeCallingTheEngine() throws Exception {
    // when
    final var response =
        perform(
            patch("/scim/v2/Groups/eng")
                .contentType(SCIM_JSON)
                .content(
                    patchBody(
                        "{\"op\":\"add\",\"path\":\"members\",\"value\":[{\"value\":\"g\",\"type\":\"Group\"}]}")));

    // then
    assertError(response, 400, "invalidValue");
    verifyNoMoreInteractions(groupServices);
  }

  @Test
  void shouldRejectPatchOfUnsupportedSubPath() throws Exception {
    // when
    final var response =
        perform(
            patch("/scim/v2/Groups/eng")
                .contentType(SCIM_JSON)
                .content(
                    patchBody("{\"op\":\"replace\",\"path\":\"members.value\",\"value\":\"x\"}")));

    // then
    assertError(response, 400, "invalidPath");
  }

  @Test
  void shouldDeleteGroup() throws Exception {
    // given
    when(groupServices.deleteGroup("eng", "default", true, AUTHENTICATION))
        .thenReturn(CompletableFuture.completedFuture(new GroupRecord()));

    // when
    final var response = perform(delete("/scim/v2/Groups/eng"));

    // then
    assertThat(response.getStatus()).isEqualTo(204);
    assertThat(response.getContentAsString()).isEmpty();
  }

  @Test
  void shouldMapNotFoundOnDelete() throws Exception {
    // given
    when(groupServices.deleteGroup("ghost", "default", true, AUTHENTICATION))
        .thenReturn(CompletableFuture.failedFuture(new ServiceException("nope", Status.NOT_FOUND)));

    // when
    final var response = perform(delete("/scim/v2/Groups/ghost"));

    // then
    assertError(response, 404, null);
  }

  private void stubUpdateAndSet() {
    lenient()
        .when(groupServices.updateGroup(any(), any(), eq(AUTHENTICATION)))
        .thenReturn(CompletableFuture.completedFuture(new GroupRecord().setGroupId("eng")));
    lenient()
        .when(
            groupServices.setMembers(
                any(), any(), anyCollection(), eq("default"), eq(true), eq(AUTHENTICATION)))
        .thenReturn(CompletableFuture.completedFuture(new GroupRecord()));
  }

  private static String patchBody(final String operations) {
    return "{\"schemas\":[\"" + PATCH_SCHEMA + "\"],\"Operations\":[" + operations + "]}";
  }

  private MockHttpServletResponse perform(final RequestBuilder request) throws Exception {
    final MvcResult result = mockMvc.perform(request).andReturn();
    if (result.getRequest().isAsyncStarted()) {
      return mockMvc.perform(asyncDispatch(result)).andReturn().getResponse();
    }
    return result.getResponse();
  }

  private static void assertError(
      final MockHttpServletResponse response, final int status, final String scimType)
      throws Exception {
    assertThat(response.getStatus()).isEqualTo(status);
    assertThat(response.getContentType()).startsWith(SCIM_JSON);
    final var error = ScimJson.read(response.getContentAsString(), ErrorResponse.class);
    assertThat(error.getStatus()).isEqualTo(status);
    assertThat(error.getScimType()).isEqualTo(scimType);
    assertThat(error.getSchemaUrns())
        .containsExactly("urn:ietf:params:scim:api:messages:2.0:Error");
  }
}
