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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.unboundid.scim2.common.messages.ErrorResponse;
import io.camunda.gateway.scim.ScimAttributeProperties.Attribute;
import io.camunda.gateway.scim.ScimJson;
import io.camunda.gateway.scim.error.ScimExceptionHandler;
import io.camunda.gateway.scim.mapper.ScimAttributeMapping;
import io.camunda.gateway.scim.mapper.ScimGroupIdGenerator;
import io.camunda.gateway.scim.mapper.ScimGroupMapper;
import io.camunda.gateway.scim.mapper.ScimGroupPatchTranslator;
import io.camunda.gateway.scim.mapper.ScimPatchTranslator;
import io.camunda.gateway.scim.mapper.ScimUserMapper;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.service.GroupServices;
import io.camunda.service.GroupServices.GroupAttribute;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.service.UserServices;
import io.camunda.service.UserServices.UserAttribute;
import io.camunda.service.UserServices.UserDTO;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import java.util.List;
import java.util.Map;
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
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class ScimAttributesControllerTest {

  private static final String SCIM_JSON = "application/scim+json";
  private static final String PATCH_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:PatchOp";
  private static final String ENTERPRISE =
      "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";
  private static final String ACME = "urn:ietf:params:scim:schemas:extension:acme:2.0:User";
  private static final String GROUP_ACME = "urn:ietf:params:scim:schemas:extension:acme:2.0:Group";
  private static final CamundaAuthentication AUTHENTICATION =
      CamundaAuthentication.of(a -> a.user("scim-client"));
  private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_~@.+-]+$");

  @Mock private ServiceRegistry serviceRegistry;
  @Mock private UserServices userServices;
  @Mock private GroupServices groupServices;
  @Mock private CamundaAuthenticationProvider authenticationProvider;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    lenient().when(serviceRegistry.userServices("default")).thenReturn(userServices);
    lenient().when(serviceRegistry.groupServices("default")).thenReturn(groupServices);
    lenient().when(authenticationProvider.getCamundaAuthentication()).thenReturn(AUTHENTICATION);
    final var userMapper =
        new ScimUserMapper(
            ScimAttributeMapping.of(
                "camunda.scim.user-attributes",
                List.of(
                    new Attribute("department", ENTERPRISE + ":department"),
                    new Attribute("clearance", ACME + ":clearance"),
                    new Attribute("title", "title"),
                    new Attribute("addresses", "addresses")),
                ScimUserMapper.USER_SCHEMA));
    final var groupMapper =
        new ScimGroupMapper(
            ScimAttributeMapping.of(
                "camunda.scim.group-attributes",
                List.of(new Attribute("costCenter", GROUP_ACME + ":costCenter")),
                ScimGroupMapper.GROUP_SCHEMA));
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new ScimUserController(
                    serviceRegistry,
                    authenticationProvider,
                    userMapper,
                    new ScimPatchTranslator(userMapper.attributes())),
                new ScimGroupController(
                    serviceRegistry,
                    authenticationProvider,
                    groupMapper,
                    new ScimGroupPatchTranslator(groupMapper),
                    new ScimGroupIdGenerator(new IdentifierValidator(ID_PATTERN, ID_PATTERN))))
            .setControllerAdvice(new ScimExceptionHandler())
            .build();
  }

  @Test
  void shouldStoreEntraEnterpriseAndCustomExtensionOnCreateAndEchoThem() throws Exception {
    // given
    when(userServices.createUser(any(), eq(AUTHENTICATION)))
        .thenAnswer(
            invocation -> {
              final UserDTO dto = invocation.getArgument(0);
              return CompletableFuture.completedFuture(
                  new UserRecord()
                      .setUsername(dto.username())
                      .setActive(true)
                      .setAttributes(dto.attributes()));
            });

    // when
    final var response =
        perform(
            post("/scim/v2/Users")
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"schemas":["urn:ietf:params:scim:schemas:core:2.0:User",
                                "%s","%s"],
                     "userName":"alice","displayName":"Alice",
                     "%s":{"department":"R&D","costCenter":"not configured"},
                     "%s":{"clearance":3}}
                    """
                        .formatted(ENTERPRISE, ACME, ENTERPRISE, ACME)));

    // then
    assertThat(response.getStatus()).isEqualTo(201);
    final var dto = ArgumentCaptor.forClass(UserDTO.class);
    verify(userServices).createUser(dto.capture(), eq(AUTHENTICATION));
    assertThat(dto.getValue().attributes()).isEqualTo(Map.of("department", "R&D", "clearance", 3));

    final var body = ScimJson.read(response.getContentAsString(), ObjectNode.class);
    assertThat(body.get(ENTERPRISE).get("department").asString()).isEqualTo("R&D");
    assertThat(body.get(ENTERPRISE).has("costCenter")).isFalse();
    assertThat(body.get(ACME).get("clearance").asInt()).isEqualTo(3);
    assertThat(body.get("schemas").valueStream().map(n -> n.asString()))
        .containsExactly("urn:ietf:params:scim:schemas:core:2.0:User", ENTERPRISE, ACME);
  }

  @Test
  void shouldReplaceAttributesOnKeycloakShapedPut() throws Exception {
    // given
    when(userServices.updateUser(any(), any(), eq(AUTHENTICATION)))
        .thenAnswer(
            invocation -> {
              final UserDTO dto = invocation.getArgument(0);
              return CompletableFuture.completedFuture(
                  new UserRecord()
                      .setUsername("alice")
                      .setActive(true)
                      .setAttributes(dto.attributes()));
            });

    // when
    final var response =
        perform(
            put("/scim/v2/Users/alice")
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"schemas":["urn:ietf:params:scim:schemas:core:2.0:User","%s"],
                     "userName":"alice","title":"Engineer",
                     "%s":{"department":"Platform"}}
                    """
                        .formatted(ENTERPRISE, ENTERPRISE)));

    // then
    assertThat(response.getStatus()).isEqualTo(200);
    final var dto = ArgumentCaptor.forClass(UserDTO.class);
    final var mask = ArgumentCaptor.forClass(Set.class);
    verify(userServices).updateUser(dto.capture(), mask.capture(), eq(AUTHENTICATION));
    assertThat(mask.getValue()).contains(UserAttribute.ATTRIBUTES);
    assertThat(dto.getValue().attributes())
        .isEqualTo(Map.of("title", "Engineer", "department", "Platform"));
    final var body = ScimJson.read(response.getContentAsString(), ObjectNode.class);
    assertThat(body.get("title").asString()).isEqualTo("Engineer");
    assertThat(body.get(ENTERPRISE).get("department").asString()).isEqualTo("Platform");
  }

  @Test
  void shouldClearAttributesOnPutWithoutThem() throws Exception {
    // given
    when(userServices.updateUser(any(), any(), eq(AUTHENTICATION)))
        .thenReturn(CompletableFuture.completedFuture(new UserRecord().setUsername("alice")));

    // when
    perform(put("/scim/v2/Users/alice").contentType(SCIM_JSON).content("{\"userName\":\"alice\"}"));

    // then
    final var dto = ArgumentCaptor.forClass(UserDTO.class);
    final var mask = ArgumentCaptor.forClass(Set.class);
    verify(userServices).updateUser(dto.capture(), mask.capture(), eq(AUTHENTICATION));
    assertThat(mask.getValue()).contains(UserAttribute.ATTRIBUTES);
    assertThat(dto.getValue().attributes()).isEmpty();
  }

  @Test
  void shouldMergeEntraPatchOfExtensionAttribute() throws Exception {
    // given
    when(userServices.updateUser(any(), any(), eq(AUTHENTICATION)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new UserRecord()
                    .setUsername("alice")
                    .setActive(true)
                    .setAttributes(Map.of("department", "Sales", "clearance", 1))));

    // when
    final var response =
        perform(
            patch("/scim/v2/Users/alice")
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"schemas":["%s"],
                     "Operations":[{"op":"Replace","path":"%s:department","value":"Sales"}]}
                    """
                        .formatted(PATCH_SCHEMA, ENTERPRISE)));

    // then
    assertThat(response.getStatus()).isEqualTo(200);
    final var dto = ArgumentCaptor.forClass(UserDTO.class);
    final var mask = ArgumentCaptor.forClass(Set.class);
    verify(userServices).updateUser(dto.capture(), mask.capture(), eq(AUTHENTICATION));
    assertThat(mask.getValue()).containsExactly(UserAttribute.ATTRIBUTES_MERGE);
    assertThat(dto.getValue().attributes()).isEqualTo(Map.of("department", "Sales"));
    final var body = ScimJson.read(response.getContentAsString(), ObjectNode.class);
    assertThat(body.get(ENTERPRISE).get("department").asString()).isEqualTo("Sales");
    assertThat(body.get(ACME).get("clearance").asInt()).isEqualTo(1);
  }

  @Test
  void shouldRejectPatchInsideConfiguredAttributeWith400() throws Exception {
    // when
    final var response =
        perform(
            patch("/scim/v2/Users/alice")
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"schemas":["%s"],
                     "Operations":[{"op":"replace",
                       "path":"addresses[type eq \\\"work\\\"].locality","value":"Bonn"}]}
                    """
                        .formatted(PATCH_SCHEMA)));

    // then
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(ScimJson.read(response.getContentAsString(), ErrorResponse.class).getScimType())
        .isEqualTo("invalidPath");
    verify(userServices, never()).updateUser(any(), any(), any());
  }

  @Test
  void shouldRejectAttributesAboveSizeLimitWith400() throws Exception {
    // when
    final var response =
        perform(
            post("/scim/v2/Users")
                .contentType(SCIM_JSON)
                .content("{\"userName\":\"alice\",\"title\":\"" + "x".repeat(17 * 1024) + "\"}"));

    // then
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(ScimJson.read(response.getContentAsString(), ErrorResponse.class).getScimType())
        .isEqualTo("invalidValue");
    verify(userServices, never()).createUser(any(), any());
  }

  @Test
  void shouldStoreGroupAttributesOnCreateAndMergeOnPatch() throws Exception {
    // given
    when(groupServices.createGroup(any(), eq(AUTHENTICATION)))
        .thenAnswer(
            invocation -> {
              final GroupDTO dto = invocation.getArgument(0);
              return CompletableFuture.completedFuture(
                  new GroupRecord()
                      .setGroupId(dto.groupId())
                      .setName(dto.name())
                      .setAttributes(dto.attributes()));
            });
    when(groupServices.updateGroup(any(), any(), eq(AUTHENTICATION)))
        .thenReturn(CompletableFuture.completedFuture(new GroupRecord().setGroupId("eng")));

    // when
    final var created =
        perform(
            post("/scim/v2/Groups")
                .contentType(SCIM_JSON)
                .content(
                    "{\"displayName\":\"Eng\",\"" + GROUP_ACME + "\":{\"costCenter\":\"CC-1\"}}"));
    final var patched =
        perform(
            patch("/scim/v2/Groups/eng")
                .contentType(SCIM_JSON)
                .content(
                    "{\"schemas\":[\"%s\"],\"Operations\":[{\"op\":\"remove\",\"path\":\"%s:costCenter\"}]}"
                        .formatted(PATCH_SCHEMA, GROUP_ACME)));

    // then
    assertThat(created.getStatus()).isEqualTo(201);
    final var body = ScimJson.read(created.getContentAsString(), ObjectNode.class);
    assertThat(body.get(GROUP_ACME).get("costCenter").asString()).isEqualTo("CC-1");
    assertThat(body.get("schemas").valueStream().map(n -> n.asString())).contains(GROUP_ACME);

    assertThat(patched.getStatus()).isEqualTo(204);
    final var dto = ArgumentCaptor.forClass(GroupDTO.class);
    final var mask = ArgumentCaptor.forClass(Set.class);
    verify(groupServices).updateGroup(dto.capture(), mask.capture(), eq(AUTHENTICATION));
    assertThat(mask.getValue()).containsExactly(GroupAttribute.ATTRIBUTES_MERGE);
    assertThat(dto.getValue().attributes()).containsOnlyKeys("costCenter");
    assertThat(dto.getValue().attributes().get("costCenter")).isNull();
  }

  @Test
  void shouldWriteEntityAttributesBackAtConfiguredPathsOnGetAndList() throws Exception {
    // given
    final var attributes = Map.<String, Object>of("department", "R&D", "title", "Dr");
    final var entity = new UserEntity(1L, "alice", "Alice", null, null, null, true, attributes);
    when(userServices.getUser("alice", AUTHENTICATION)).thenReturn(entity);
    when(userServices.search(any(), eq(AUTHENTICATION))).thenReturn(SearchQueryResult.of(entity));

    // when
    final var single = perform(get("/scim/v2/Users/alice"));
    final var list = perform(get("/scim/v2/Users"));

    // then
    final var body = ScimJson.read(single.getContentAsString(), ObjectNode.class);
    assertThat(body.get("title").asString()).isEqualTo("Dr");
    assertThat(body.get(ENTERPRISE).get("department").asString()).isEqualTo("R&D");
    assertThat(body.get("schemas").valueStream().map(n -> n.asString())).contains(ENTERPRISE);
    final var listed = ScimJson.read(list.getContentAsString(), ObjectNode.class);
    assertThat(listed.get("Resources").get(0).get(ENTERPRISE).get("department").asString())
        .isEqualTo("R&D");
  }

  @Test
  void shouldWriteGroupEntityAttributesOnGet() throws Exception {
    // given
    when(groupServices.getGroup("eng", AUTHENTICATION))
        .thenReturn(
            new GroupEntity(
                1L, "eng", "Eng", null, null, Map.<String, Object>of("costCenter", "CC-9")));

    // when
    final var response = perform(get("/scim/v2/Groups/eng").param("excludedAttributes", "members"));

    // then
    final var body = ScimJson.read(response.getContentAsString(), ObjectNode.class);
    assertThat(body.get(GROUP_ACME).get("costCenter").asString()).isEqualTo("CC-9");
  }

  private MockHttpServletResponse perform(final RequestBuilder request) throws Exception {
    final MvcResult result = mockMvc.perform(request).andReturn();
    if (result.getRequest().isAsyncStarted()) {
      return mockMvc.perform(asyncDispatch(result)).andReturn().getResponse();
    }
    return result.getResponse();
  }
}
