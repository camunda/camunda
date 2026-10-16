/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.unboundid.scim2.common.messages.ListResponse;
import io.camunda.gateway.scim.ScimAttributeProperties.SourceProperties;
import io.camunda.gateway.scim.ScimJson;
import io.camunda.gateway.scim.ScimSources;
import io.camunda.gateway.scim.error.ScimExceptionHandler;
import io.camunda.gateway.scim.mapper.ScimGroupIdGenerator;
import io.camunda.gateway.scim.mapper.ScimGroupMapper;
import io.camunda.gateway.scim.mapper.ScimGroupPatchTranslator;
import io.camunda.gateway.scim.mapper.ScimPatchTranslator;
import io.camunda.gateway.scim.mapper.ScimUserMapper;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.query.GroupMemberQuery;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.service.GroupServices;
import io.camunda.service.GroupServices.GroupAttribute;
import io.camunda.service.UserServices;
import io.camunda.service.UserServices.UserAttribute;
import io.camunda.service.UserServices.UserDTO;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Source resolution, adoption, ownership errors and read filtering of the SCIM controllers. */
@ExtendWith(MockitoExtension.class)
class ScimSourcesControllerTest {

  private static final String SCIM_JSON = "application/scim+json";
  private static final String PATCH_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:PatchOp";
  private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_~.@-]+$");
  private static final CamundaAuthentication CLIENT_A =
      CamundaAuthentication.of(a -> a.clientId("client-a"));
  private static final CamundaAuthentication CLIENT_B =
      CamundaAuthentication.of(a -> a.clientId("client-b"));
  private static final CamundaAuthentication UNKNOWN =
      CamundaAuthentication.of(a -> a.clientId("intruder"));

  @Mock private ServiceRegistry serviceRegistry;
  @Mock private UserServices userServices;
  @Mock private GroupServices groupServices;
  @Mock private CamundaAuthenticationProvider authenticationProvider;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    lenient().when(serviceRegistry.userServices("default")).thenReturn(userServices);
    lenient().when(serviceRegistry.groupServices("default")).thenReturn(groupServices);
    // source "a" does not adopt unowned entities, source "b" does
    final var sources =
        new ScimSources(
            List.of(
                source("a", "client-a", "reg-a", false), source("b", "client-b", "reg-b", true)));
    final var userMapper = new ScimUserMapper();
    final var groupMapper = new ScimGroupMapper();
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new ScimUserController(
                    serviceRegistry,
                    authenticationProvider,
                    userMapper,
                    new ScimPatchTranslator(),
                    sources),
                new ScimGroupController(
                    serviceRegistry,
                    authenticationProvider,
                    groupMapper,
                    new ScimGroupPatchTranslator(groupMapper),
                    new ScimGroupIdGenerator(new IdentifierValidator(ID_PATTERN, ID_PATTERN)),
                    sources))
            .setControllerAdvice(new ScimExceptionHandler())
            .build();
  }

  @Test
  void shouldRejectUnknownClientWith403OnEveryEndpoint() throws Exception {
    // given
    as(UNKNOWN);

    // then
    assertThat(perform(get("/scim/v2/Users/alice")).getStatus()).isEqualTo(403);
    assertThat(perform(get("/scim/v2/Users")).getStatus()).isEqualTo(403);
    assertThat(perform(delete("/scim/v2/Users/alice")).getStatus()).isEqualTo(403);
    assertThat(perform(get("/scim/v2/Groups/eng")).getStatus()).isEqualTo(403);
    assertThat(
            perform(post("/scim/v2/Groups").contentType(SCIM_JSON).content(group("Eng")))
                .getStatus())
        .isEqualTo(403);
    verify(userServices, never()).createUser(any(), any());
    verify(groupServices, never()).createGroup(any(), any());
  }

  @Test
  void shouldSetSourceOfCallerOnCreateUser() throws Exception {
    // given
    as(CLIENT_A);
    when(userServices.createUser(any(), eq(CLIENT_A)))
        .thenReturn(CompletableFuture.completedFuture(new UserRecord().setUsername("alice")));

    // when
    final var response = perform(post("/scim/v2/Users").contentType(SCIM_JSON).content(user()));

    // then
    assertThat(response.getStatus()).isEqualTo(201);
    final var dto = ArgumentCaptor.forClass(UserDTO.class);
    verify(userServices).createUser(dto.capture(), eq(CLIENT_A));
    assertThat(dto.getValue().source()).isEqualTo("a");
  }

  @Test
  void shouldAdoptUnownedUserOnCreateWhenSourceAdopts() throws Exception {
    // given
    as(CLIENT_B);
    when(userServices.createUser(any(), eq(CLIENT_B)))
        .thenReturn(failed(Status.ALREADY_EXISTS, "exists"));
    when(userServices.getUser("alice", CLIENT_B)).thenReturn(userEntity("alice", null));
    when(userServices.updateUser(any(), any(), eq(CLIENT_B)))
        .thenReturn(CompletableFuture.completedFuture(new UserRecord().setUsername("alice")));

    // when
    final var response = perform(post("/scim/v2/Users").contentType(SCIM_JSON).content(user()));

    // then
    assertThat(response.getStatus()).isEqualTo(201);
    final var dto = ArgumentCaptor.forClass(UserDTO.class);
    final var mask = ArgumentCaptor.forClass(Set.class);
    verify(userServices).updateUser(dto.capture(), mask.capture(), eq(CLIENT_B));
    assertThat(dto.getValue().source()).isEqualTo("b");
    assertThat(mask.getValue()).contains(UserAttribute.SOURCE, UserAttribute.NAME);
  }

  @Test
  void shouldNotAdoptUserOwnedByAnotherSourceOnCreate() throws Exception {
    // given
    as(CLIENT_B);
    when(userServices.createUser(any(), eq(CLIENT_B)))
        .thenReturn(failed(Status.ALREADY_EXISTS, "exists"));
    when(userServices.getUser("alice", CLIENT_B)).thenReturn(userEntity("alice", "a"));

    // when
    final var response = perform(post("/scim/v2/Users").contentType(SCIM_JSON).content(user()));

    // then
    assertThat(response.getStatus()).isEqualTo(409);
    assertThat(response.getContentAsString()).contains("uniqueness");
    verify(userServices, never()).updateUser(any(), any(), any());
  }

  @Test
  void shouldNotAdoptUnownedUserOnCreateWhenSourceDoesNotAdopt() throws Exception {
    // given
    as(CLIENT_A);
    when(userServices.createUser(any(), eq(CLIENT_A)))
        .thenReturn(failed(Status.ALREADY_EXISTS, "exists"));

    // when
    final var response = perform(post("/scim/v2/Users").contentType(SCIM_JSON).content(user()));

    // then
    assertThat(response.getStatus()).isEqualTo(409);
    verify(userServices, never()).getUser(any(), any());
    verify(userServices, never()).updateUser(any(), any(), any());
  }

  @Test
  void shouldIncludeAdoptionInMaskOfReplaceAndPatchOnlyForAdoptingSource() throws Exception {
    // given
    when(userServices.updateUser(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new UserRecord().setUsername("alice")));

    // when
    as(CLIENT_A);
    perform(put("/scim/v2/Users/alice").contentType(SCIM_JSON).content(user()));
    as(CLIENT_B);
    perform(put("/scim/v2/Users/alice").contentType(SCIM_JSON).content(user()));
    perform(
        patch("/scim/v2/Users/alice")
            .contentType(SCIM_JSON)
            .content(
                """
                {"schemas":["%s"],"Operations":[{"op":"Replace","path":"active","value":false}]}
                """
                    .formatted(PATCH_SCHEMA)));

    // then
    final var mask = ArgumentCaptor.forClass(Set.class);
    final var dto = ArgumentCaptor.forClass(UserDTO.class);
    verify(userServices, times(3)).updateUser(dto.capture(), mask.capture(), any());
    assertThat(mask.getAllValues().get(0)).doesNotContain(UserAttribute.SOURCE);
    assertThat(mask.getAllValues().get(1)).contains(UserAttribute.SOURCE);
    assertThat(mask.getAllValues().get(2)).contains(UserAttribute.SOURCE, UserAttribute.ACTIVE);
    assertThat(dto.getAllValues()).extracting(UserDTO::source).containsExactly("a", "b", "b");
  }

  @Test
  void shouldMapUnownedRejectionOfUpdateToConflict() throws Exception {
    // given
    as(CLIENT_A);
    when(userServices.updateUser(any(), any(), eq(CLIENT_A)))
        .thenReturn(failed(Status.INVALID_STATE, "user 'alice' is not owned by source 'a'"));

    // when
    final var response =
        perform(put("/scim/v2/Users/alice").contentType(SCIM_JSON).content(user()));

    // then
    assertThat(response.getStatus()).isEqualTo(409);
    assertThat(response.getContentAsString()).contains("not owned by source");
  }

  @Test
  void shouldMapOtherSourceRejectionToNotFound() throws Exception {
    // given
    as(CLIENT_A);
    when(userServices.updateUser(any(), any(), eq(CLIENT_A)))
        .thenReturn(failed(Status.NOT_FOUND, "does not exist"));
    when(userServices.deleteUser("alice", "a", false, CLIENT_A))
        .thenReturn(failed(Status.NOT_FOUND, "does not exist"));

    // then
    assertThat(
            perform(put("/scim/v2/Users/alice").contentType(SCIM_JSON).content(user())).getStatus())
        .isEqualTo(404);
    assertThat(perform(delete("/scim/v2/Users/alice")).getStatus()).isEqualTo(404);
  }

  @Test
  void shouldDeleteWithSourceAndAdoption() throws Exception {
    // given
    as(CLIENT_B);
    when(userServices.deleteUser("alice", "b", true, CLIENT_B))
        .thenReturn(CompletableFuture.completedFuture(new UserRecord()));

    // then
    assertThat(perform(delete("/scim/v2/Users/alice")).getStatus()).isEqualTo(204);
  }

  @Test
  void shouldAnswer404ForUserOfAnotherSourceOnGetAndPatchWithoutChanges() throws Exception {
    // given
    as(CLIENT_A);
    when(userServices.getUser("bob", CLIENT_A)).thenReturn(userEntity("bob", "b"));

    // then
    assertThat(perform(get("/scim/v2/Users/bob")).getStatus()).isEqualTo(404);
    assertThat(
            perform(
                    patch("/scim/v2/Users/bob")
                        .contentType(SCIM_JSON)
                        .content(
                            """
                            {"schemas":["%s"],"Operations":[]}
                            """
                                .formatted(PATCH_SCHEMA)))
                .getStatus())
        .isEqualTo(404);
  }

  @Test
  void shouldShowOwnUsersAndUnownedOnlyToAdoptingSource() throws Exception {
    // given
    when(userServices.getUser("own", CLIENT_B)).thenReturn(userEntity("own", "b"));
    when(userServices.getUser("free", CLIENT_B)).thenReturn(userEntity("free", ""));
    when(userServices.getUser("free", CLIENT_A)).thenReturn(userEntity("free", null));

    // then
    as(CLIENT_B);
    assertThat(perform(get("/scim/v2/Users/own")).getStatus()).isEqualTo(200);
    assertThat(perform(get("/scim/v2/Users/free")).getStatus()).isEqualTo(200);
    as(CLIENT_A);
    assertThat(perform(get("/scim/v2/Users/free")).getStatus()).isEqualTo(404);
  }

  @Test
  void shouldFilterUserListBySource() throws Exception {
    // given
    as(CLIENT_A);
    when(userServices.search(any(UserQuery.class), eq(CLIENT_A)))
        .thenReturn(
            SearchQueryResult.of(
                userEntity("mine", "a"), userEntity("theirs", "b"), userEntity("free", null)));

    // when
    final var response = perform(get("/scim/v2/Users"));

    // then
    @SuppressWarnings("unchecked")
    final var list = ScimJson.read(response.getContentAsString(), ListResponse.class);
    assertThat(list.getResources()).hasSize(1);
    assertThat(list.getTotalResults()).isEqualTo(1);
    assertThat(response.getContentAsString()).contains("mine").doesNotContain("theirs");
  }

  @Test
  void shouldSetSourceOnCreateGroupAndPassItToMembership() throws Exception {
    // given
    as(CLIENT_A);
    when(groupServices.createGroup(any(), eq(CLIENT_A)))
        .thenReturn(CompletableFuture.completedFuture(new GroupRecord().setGroupId("eng")));
    when(groupServices.setMembers(any(), any(), anyCollection(), any(), anyBoolean(), any()))
        .thenReturn(CompletableFuture.completedFuture(new GroupRecord()));

    // when
    final var response =
        perform(
            post("/scim/v2/Groups")
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"schemas":["urn:ietf:params:scim:schemas:core:2.0:Group"],
                     "displayName":"eng","members":[{"value":"alice"}]}
                    """));

    // then
    assertThat(response.getStatus()).isEqualTo(201);
    final var dto = ArgumentCaptor.forClass(GroupServices.GroupDTO.class);
    verify(groupServices).createGroup(dto.capture(), eq(CLIENT_A));
    assertThat(dto.getValue().source()).isEqualTo("a");
    verify(groupServices)
        .setMembers(any(), eq(EntityType.USER), eq(List.of("alice")), eq("a"), eq(false), any());
  }

  @Test
  void shouldAdoptUnownedGroupOnCreateWhenSourceAdopts() throws Exception {
    // given
    as(CLIENT_B);
    when(groupServices.createGroup(any(), eq(CLIENT_B)))
        .thenReturn(failed(Status.ALREADY_EXISTS, "exists"));
    when(groupServices.getGroup("eng", CLIENT_B)).thenReturn(groupEntity("eng", null));
    when(groupServices.updateGroup(any(), any(), eq(CLIENT_B)))
        .thenReturn(CompletableFuture.completedFuture(new GroupRecord().setGroupId("eng")));

    // when
    final var response =
        perform(post("/scim/v2/Groups").contentType(SCIM_JSON).content(group("eng")));

    // then
    assertThat(response.getStatus()).isEqualTo(201);
    final var mask = ArgumentCaptor.forClass(Set.class);
    verify(groupServices).updateGroup(any(), mask.capture(), eq(CLIENT_B));
    assertThat(mask.getValue()).contains(GroupAttribute.SOURCE, GroupAttribute.NAME);
  }

  @Test
  void shouldReportGroupIdCollisionWithOtherSourceAsConflict() throws Exception {
    // given
    as(CLIENT_B);
    when(groupServices.createGroup(any(), eq(CLIENT_B)))
        .thenReturn(failed(Status.ALREADY_EXISTS, "exists"));
    when(groupServices.getGroup("eng", CLIENT_B)).thenReturn(groupEntity("eng", "a"));

    // when
    final var response =
        perform(post("/scim/v2/Groups").contentType(SCIM_JSON).content(group("eng")));

    // then
    assertThat(response.getStatus()).isEqualTo(409);
    assertThat(response.getContentAsString()).contains("uniqueness");
  }

  @Test
  void shouldMapForeignMemberToInvalidValue() throws Exception {
    // given
    as(CLIENT_A);
    when(groupServices.updateGroup(any(), any(), eq(CLIENT_A)))
        .thenReturn(CompletableFuture.completedFuture(new GroupRecord().setGroupId("eng")));
    when(groupServices.setMembers(any(), any(), anyCollection(), any(), anyBoolean(), any()))
        .thenReturn(failed(Status.INVALID_ARGUMENT, "user 'bob' is not owned by source 'a'"));

    // when
    final var response =
        perform(
            put("/scim/v2/Groups/eng")
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"schemas":["urn:ietf:params:scim:schemas:core:2.0:Group"],
                     "displayName":"eng","members":[{"value":"bob"}]}
                    """));

    // then
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getContentAsString()).contains("invalidValue");
  }

  @Test
  void shouldHideGroupOfAnotherSourceAndFilterGroupList() throws Exception {
    // given
    as(CLIENT_A);
    when(groupServices.getGroup("theirs", CLIENT_A)).thenReturn(groupEntity("theirs", "b"));
    when(groupServices.search(any(GroupQuery.class), eq(CLIENT_A)))
        .thenReturn(SearchQueryResult.of(groupEntity("mine", "a"), groupEntity("theirs", "b")));
    when(groupServices.searchMembers(any(GroupMemberQuery.class), eq(CLIENT_A)))
        .thenReturn(SearchQueryResult.of());

    // when
    final var list = perform(get("/scim/v2/Groups"));

    // then
    assertThat(perform(get("/scim/v2/Groups/theirs")).getStatus()).isEqualTo(404);
    assertThat(list.getContentAsString()).contains("mine").doesNotContain("theirs");
  }

  @Test
  void shouldDeleteGroupWithSourceAndAdoption() throws Exception {
    // given
    as(CLIENT_B);
    when(groupServices.deleteGroup("eng", "b", true, CLIENT_B))
        .thenReturn(CompletableFuture.completedFuture(new GroupRecord()));

    // then
    assertThat(perform(delete("/scim/v2/Groups/eng")).getStatus()).isEqualTo(204);
  }

  private void as(final CamundaAuthentication authentication) {
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(authentication);
  }

  private static <T> CompletableFuture<T> failed(final Status status, final String message) {
    return CompletableFuture.failedFuture(new ServiceException(message, status));
  }

  private static UserEntity userEntity(final String username, final String source) {
    return new UserEntity(1L, username, username, null, null, null, true, null, null, null, source);
  }

  private static GroupEntity groupEntity(final String groupId, final String source) {
    return new GroupEntity(1L, groupId, groupId, null, null, null, null, null, source);
  }

  private static String user() {
    return """
        {"schemas":["urn:ietf:params:scim:schemas:core:2.0:User"],
         "userName":"alice","displayName":"Alice"}
        """;
  }

  private static String group(final String name) {
    return """
        {"schemas":["urn:ietf:params:scim:schemas:core:2.0:Group"],"displayName":"%s"}
        """
        .formatted(name);
  }

  private static SourceProperties source(
      final String id, final String client, final String registration, final boolean adopt) {
    final var source = new SourceProperties();
    source.setId(id);
    source.setClientIds(List.of(client));
    source.setOidcRegistration(registration);
    source.setAdoptUnowned(adopt);
    return source;
  }

  private MockHttpServletResponse perform(final RequestBuilder request) throws Exception {
    final MvcResult result = mockMvc.perform(request).andReturn();
    if (result.getRequest().isAsyncStarted()) {
      return mockMvc.perform(asyncDispatch(result)).andReturn().getResponse();
    }
    return result.getResponse();
  }
}
