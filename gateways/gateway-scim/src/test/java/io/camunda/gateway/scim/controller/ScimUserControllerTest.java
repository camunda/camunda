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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.unboundid.scim2.common.messages.ErrorResponse;
import com.unboundid.scim2.common.messages.ListResponse;
import com.unboundid.scim2.common.types.UserResource;
import io.camunda.gateway.scim.ScimJson;
import io.camunda.gateway.scim.ScimSources;
import io.camunda.gateway.scim.error.ScimExceptionHandler;
import io.camunda.gateway.scim.mapper.ScimPatchTranslator;
import io.camunda.gateway.scim.mapper.ScimUserMapper;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.service.UserServices;
import io.camunda.service.UserServices.UserAttribute;
import io.camunda.service.UserServices.UserDTO;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ScimUserControllerTest {

  private static final String SCIM_JSON = "application/scim+json";
  private static final String PATCH_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:PatchOp";
  private static final CamundaAuthentication AUTHENTICATION =
      CamundaAuthentication.of(a -> a.user("scim-client"));

  @Mock private ServiceRegistry serviceRegistry;
  @Mock private UserServices userServices;
  @Mock private CamundaAuthenticationProvider authenticationProvider;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    lenient().when(serviceRegistry.userServices("default")).thenReturn(userServices);
    lenient().when(authenticationProvider.getCamundaAuthentication()).thenReturn(AUTHENTICATION);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new ScimUserController(
                    serviceRegistry,
                    authenticationProvider,
                    new ScimUserMapper(),
                    new ScimPatchTranslator(),
                    new ScimSources(List.of())))
            .setControllerAdvice(new ScimExceptionHandler())
            .build();
  }

  @Test
  void shouldCreateUserAndRespondFromEngineRecord() throws Exception {
    // given
    when(userServices.createUser(any(), eq(AUTHENTICATION)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new UserRecord()
                    .setUsername("alice")
                    .setName("Alice")
                    .setEmail("alice@example.com")
                    .setExternalId("ext-1")
                    .setActive(true)));

    // when
    final var response =
        perform(
            post("/scim/v2/Users")
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"schemas":["urn:ietf:params:scim:schemas:core:2.0:User"],
                     "userName":"alice","externalId":"ext-1","displayName":"Alice",
                     "emails":[{"value":"alice@example.com","primary":true}]}
                    """));

    // then
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getContentType()).startsWith(SCIM_JSON);
    assertThat(response.getHeader(HttpHeaders.LOCATION))
        .isEqualTo("http://localhost/scim/v2/Users/alice");
    final var user = ScimJson.read(response.getContentAsString(), UserResource.class);
    assertThat(user.getId()).isEqualTo("alice");
    assertThat(user.getExternalId()).isEqualTo("ext-1");
    assertThat(user.getMeta().getResourceType()).isEqualTo("User");
    assertThat(user.getMeta().getLocation()).hasToString("http://localhost/scim/v2/Users/alice");

    final var dto = ArgumentCaptor.forClass(UserDTO.class);
    verify(userServices).createUser(dto.capture(), eq(AUTHENTICATION));
    assertThat(dto.getValue())
        .isEqualTo(
            new UserDTO(
                "alice", "Alice", "alice@example.com", "", "ext-1", true, Map.of(), "default"));
  }

  @Test
  void shouldCreateUserWhenEngineRespondsOnAnotherThread() throws Exception {
    // given
    when(userServices.createUser(any(), eq(AUTHENTICATION)))
        .thenReturn(
            CompletableFuture.supplyAsync(
                () -> new UserRecord().setUsername("alice"),
                CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS)));

    // when
    final var response =
        perform(
            post("/scim/v2/Users")
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"schemas":["urn:ietf:params:scim:schemas:core:2.0:User"],"userName":"alice"}
                    """));

    // then
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getHeader(HttpHeaders.LOCATION))
        .isEqualTo("http://localhost/scim/v2/Users/alice");
  }

  @Test
  void shouldAcceptPlainJsonContentType() throws Exception {
    // given
    when(userServices.createUser(any(), any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new UserRecord().setUsername("alice").setActive(true)));

    // when
    final var response =
        perform(
            post("/scim/v2/Users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userName\":\"alice\"}"));

    // then
    assertThat(response.getStatus()).isEqualTo(201);
  }

  @Test
  void shouldMapAlreadyExistsToConflictWithUniquenessType() throws Exception {
    // given
    when(userServices.createUser(any(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("user exists", Status.ALREADY_EXISTS)));

    // when
    final var response =
        perform(post("/scim/v2/Users").contentType(SCIM_JSON).content("{\"userName\":\"alice\"}"));

    // then
    assertError(response, 409, "uniqueness");
  }

  @Test
  void shouldMapMalformedJsonToInvalidSyntax() throws Exception {
    // when
    final var response =
        perform(post("/scim/v2/Users").contentType(SCIM_JSON).content("{\"userName\":"));

    // then
    assertError(response, 400, "invalidSyntax");
    verify(userServices, never()).createUser(any(), any());
  }

  @Test
  void shouldMapMissingBodyToInvalidSyntax() throws Exception {
    // when
    final var response = perform(post("/scim/v2/Users").contentType(SCIM_JSON));

    // then
    assertError(response, 400, "invalidSyntax");
  }

  @Test
  void shouldRejectCreateWithoutUserName() throws Exception {
    // when
    final var response =
        perform(post("/scim/v2/Users").contentType(SCIM_JSON).content("{\"active\":true}"));

    // then
    assertError(response, 400, "invalidValue");
  }

  @Test
  void shouldGetUserFromSecondaryStorage() throws Exception {
    // given
    when(userServices.getUser("alice", AUTHENTICATION))
        .thenReturn(new UserEntity(7L, "alice", "Alice", "alice@example.com", null, "ext", false));

    // when
    final var response = perform(get("/scim/v2/Users/alice"));

    // then
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentType()).startsWith(SCIM_JSON);
    final var user = ScimJson.read(response.getContentAsString(), UserResource.class);
    assertThat(user.getUserName()).isEqualTo("alice");
    assertThat(user.getActive()).isFalse();
    assertThat(user.getExternalId()).isEqualTo("ext");
  }

  @Test
  void shouldMapNotFoundToScimError() throws Exception {
    // given
    when(userServices.getUser("ghost", AUTHENTICATION))
        .thenThrow(new ServiceException("not found", Status.NOT_FOUND));

    // when
    final var response = perform(get("/scim/v2/Users/ghost"));

    // then
    assertError(response, 404, null);
  }

  @Test
  void shouldMapForbiddenAndUnauthorizedTo403() throws Exception {
    // given
    when(userServices.getUser("a", AUTHENTICATION))
        .thenThrow(new ServiceException("no", Status.FORBIDDEN));
    when(userServices.getUser("b", AUTHENTICATION))
        .thenThrow(new ServiceException("no", Status.UNAUTHORIZED));

    // when / then
    assertError(perform(get("/scim/v2/Users/a")), 403, null);
    assertError(perform(get("/scim/v2/Users/b")), 403, null);
  }

  @Test
  void shouldMapInvalidArgumentToInvalidValue() throws Exception {
    // given
    when(userServices.createUser(any(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(new ServiceException("bad", Status.INVALID_ARGUMENT)));

    // when
    final var response =
        perform(post("/scim/v2/Users").contentType(SCIM_JSON).content("{\"userName\":\"alice\"}"));

    // then
    assertError(response, 400, "invalidValue");
  }

  @Test
  void shouldMapUnexpectedFailureTo500WithoutLeakingDetails() throws Exception {
    // given
    when(userServices.getUser("alice", AUTHENTICATION)).thenThrow(new IllegalStateException("db"));

    // when
    final var response = perform(get("/scim/v2/Users/alice"));

    // then
    assertError(response, 500, null);
    assertThat(response.getContentAsString()).doesNotContain("db\"");
  }

  @Test
  void shouldSearchByUserNameWithPagination() throws Exception {
    // given
    when(userServices.search(any(UserQuery.class), eq(AUTHENTICATION)))
        .thenReturn(
            SearchQueryResult.of(new UserEntity(1L, "alice", "Alice", "a@x.io", null, null, null)));

    // when
    final var response =
        perform(
            get("/scim/v2/Users")
                .param("filter", "userName eq \"alice\"")
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

    final var query = ArgumentCaptor.forClass(UserQuery.class);
    verify(userServices).search(query.capture(), eq(AUTHENTICATION));
    assertThat(query.getValue().page().from()).isEqualTo(2);
    assertThat(query.getValue().page().size()).isEqualTo(10);
    assertThat(query.getValue().filter().usernameOperations()).hasSize(1);
  }

  @Test
  void shouldReturnEmptyListWhenNothingMatches() throws Exception {
    // given
    when(userServices.search(any(UserQuery.class), any())).thenReturn(SearchQueryResult.empty());

    // when
    final var response = perform(get("/scim/v2/Users").param("filter", "userName eq \"x\""));

    // then
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentAsString()).contains("\"totalResults\":0");
  }

  @Test
  void shouldRejectUnsupportedFilter() throws Exception {
    // when
    final var response = perform(get("/scim/v2/Users").param("filter", "active eq true"));

    // then
    assertError(response, 400, "invalidFilter");
    verify(userServices, never()).search(any(), any());
  }

  @Test
  void shouldReplaceUserWithMaskOfAllMutableAttributes() throws Exception {
    // given
    when(userServices.updateUser(any(), any(), eq(AUTHENTICATION)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new UserRecord().setUsername("alice").setName("Alice L.").setActive(true)));

    // when
    final var response =
        perform(
            put("/scim/v2/Users/alice")
                .contentType(SCIM_JSON)
                .content("{\"userName\":\"alice\",\"displayName\":\"Alice L.\"}"));

    // then
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentAsString()).contains("Alice L.");
    final var attributes = ArgumentCaptor.forClass(Set.class);
    verify(userServices).updateUser(any(), attributes.capture(), eq(AUTHENTICATION));
    assertThat(attributes.getValue())
        .containsExactlyInAnyOrder(
            UserAttribute.SOURCE,
            UserAttribute.NAME,
            UserAttribute.EMAIL,
            UserAttribute.EXTERNAL_ID,
            UserAttribute.ACTIVE);
  }

  @Test
  void shouldRejectUserNameChangeOnReplace() throws Exception {
    // when
    final var response =
        perform(
            put("/scim/v2/Users/alice").contentType(SCIM_JSON).content("{\"userName\":\"bob\"}"));

    // then
    assertError(response, 400, "mutability");
    verify(userServices, never()).updateUser(any(), any(), any());
  }

  @Test
  void shouldPatchUserAndRespondFromMergedEngineRecord() throws Exception {
    // given
    when(userServices.updateUser(any(), any(), eq(AUTHENTICATION)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new UserRecord()
                    .setUsername("alice")
                    .setName("Alice")
                    .setEmail("alice@example.com")
                    .setActive(false)));

    // when
    final var response =
        perform(
            patch("/scim/v2/Users/alice")
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"schemas":["%s"],
                     "Operations":[{"op":"Replace","path":"active","value":"False"}]}
                    """
                        .formatted(PATCH_SCHEMA)));

    // then
    assertThat(response.getStatus()).isEqualTo(200);
    final var user = ScimJson.read(response.getContentAsString(), UserResource.class);
    assertThat(user.getActive()).isFalse();
    assertThat(user.getDisplayName()).isEqualTo("Alice");

    final var dto = ArgumentCaptor.forClass(UserDTO.class);
    final var attributes = ArgumentCaptor.forClass(Set.class);
    verify(userServices).updateUser(dto.capture(), attributes.capture(), eq(AUTHENTICATION));
    assertThat(attributes.getValue())
        .containsExactlyInAnyOrder(UserAttribute.ACTIVE, UserAttribute.SOURCE);
    assertThat(dto.getValue().active()).isFalse();
    verify(userServices, never()).getUser(any(), any());
  }

  @Test
  void shouldReadUserInsteadOfSendingEmptyMaskWhenPatchChangesNothing() throws Exception {
    // given
    when(userServices.getUser("alice", AUTHENTICATION))
        .thenReturn(new UserEntity(1L, "alice", "Alice", null, null));

    // when
    final var response =
        perform(
            patch("/scim/v2/Users/alice")
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"schemas":["%s"],
                     "Operations":[{"op":"replace","path":"userName","value":"alice"}]}
                    """
                        .formatted(PATCH_SCHEMA)));

    // then
    assertThat(response.getStatus()).isEqualTo(200);
    verify(userServices, never()).updateUser(any(), any(), any());
  }

  @Test
  void shouldIgnorePatchOfUnconfiguredPath() throws Exception {
    // given
    when(userServices.getUser("alice", AUTHENTICATION))
        .thenReturn(new UserEntity(1L, "alice", "Alice", null, null));

    // when
    final var response =
        perform(
            patch("/scim/v2/Users/alice")
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"schemas":["%s"],
                     "Operations":[{"op":"replace","path":"title","value":"Dr"}]}
                    """
                        .formatted(PATCH_SCHEMA)));

    // then
    assertThat(response.getStatus()).isEqualTo(200);
    verify(userServices, never()).updateUser(any(), any(), any());
  }

  @Test
  void shouldRejectPatchOfUnsupportedSubPath() throws Exception {
    // when
    final var response =
        perform(
            patch("/scim/v2/Users/alice")
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"schemas":["%s"],
                     "Operations":[{"op":"replace","path":"name.middleName","value":"M"}]}
                    """
                        .formatted(PATCH_SCHEMA)));

    // then
    assertError(response, 400, "invalidPath");
  }

  @Test
  void shouldDeleteUser() throws Exception {
    // given
    when(userServices.deleteUser("alice", "default", true, AUTHENTICATION))
        .thenReturn(CompletableFuture.completedFuture(new UserRecord().setUsername("alice")));

    // when
    final var response = perform(delete("/scim/v2/Users/alice"));

    // then
    assertThat(response.getStatus()).isEqualTo(204);
    assertThat(response.getContentAsString()).isEmpty();
  }

  @Test
  void shouldMapNotFoundOnDelete() throws Exception {
    // given
    when(userServices.deleteUser("ghost", "default", true, AUTHENTICATION))
        .thenReturn(CompletableFuture.failedFuture(new ServiceException("nope", Status.NOT_FOUND)));

    // when
    final var response = perform(delete("/scim/v2/Users/ghost"));

    // then
    assertError(response, 404, null);
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
