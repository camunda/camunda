/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.variablehistory;

import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.search.connect.configuration.DatabaseType.ELASTICSEARCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TenantDefinition;
import io.camunda.qa.util.auth.TestTenant;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension.DatabaseType;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.template.VariableHistoryTemplate;
import io.camunda.webapps.schema.entities.variablehistory.VariableHistoryActorType;
import io.camunda.webapps.schema.entities.variablehistory.VariableHistoryEntity;
import io.camunda.webapps.schema.entities.variablehistory.VariableHistoryOperationType;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.value.VariableOperationType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;

/**
 * Verifies the variable history search against the {@code variable-history} index. Nothing exports
 * variable history yet, so the test writes the documents itself, shaped like the exporter writes
 * them: the id is {@code <partitionId>-<position>} and the key encodes the same two parts.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "(?i)rdbms.*")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "(?i)aws_os")
class VariableHistorySearchIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withMultiTenancyEnabled();

  private static final String RUN = UUID.randomUUID().toString().substring(0, 8);
  private static final String PROCESS_1 = "process-1-" + RUN;
  private static final String PROCESS_2 = "process-2-" + RUN;
  private static final String TENANT_A = "tenantA";
  private static final String TENANT_B = "tenantB";
  private static final String ADMIN = "admin";
  private static final String PROCESS_1_USER = "process-1-user";
  private static final String TENANT_A_USER = "tenant-a-user";
  private static final String PASSWORD = "password";
  private static final String ALICE = "alice-" + RUN;
  private static final String BOB = "bob-" + RUN;
  private static final String CLIENT = "client-" + RUN;
  private static final String AGENT_ELEMENT = "agent-" + RUN;

  private static final int PARTITION_ID = 1;
  private static final long ITEM_POSITION_BASE = 1000;
  private static final long VARIABLE_ORDER = key(2001);
  private static final long VARIABLE_BIG = key(2002);
  private static final long VARIABLE_OTHER = key(2003);
  private static final long VARIABLE_TENANT_B = key(2004);
  private static final long VARIABLE_OTHER_B = key(2005);
  private static final long ROOT_INSTANCE = key(3000);
  private static final long INSTANCE_1 = key(3001);
  private static final long INSTANCE_2 = key(3002);
  private static final long INSTANCE_3 = key(3003);
  private static final long INSTANCE_4 = key(3004);
  private static final long INSTANCE_5 = key(3005);
  private static final long ELEMENT_INSTANCE_1 = key(4001);
  private static final long ELEMENT_INSTANCE_2 = key(4002);
  private static final long DEFINITION_1 = key(5001);
  private static final long DEFINITION_2 = key(5002);

  private static final OffsetDateTime T0 = OffsetDateTime.parse("2025-01-01T10:00:00Z");
  private static final OffsetDateTime T1 = T0.plusHours(1);
  private static final String PREVIEW = "\"a long value cut a";
  private static final String FULL_VALUE = "\"a long value cut at the size limit\"";

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          PASSWORD,
          List.of(new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*"))));

  @UserDefinition
  private static final TestUser PROCESS_1_USER_DEFINITION =
      new TestUser(
          PROCESS_1_USER,
          PASSWORD,
          List.of(new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(PROCESS_1))));

  @UserDefinition
  private static final TestUser TENANT_A_USER_DEFINITION =
      new TestUser(
          TENANT_A_USER,
          PASSWORD,
          List.of(new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*"))));

  @TenantDefinition
  private static final TestTenant TENANT_A_DEFINITION =
      new TestTenant(TENANT_A).setName(TENANT_A).addUsers(ADMIN, PROCESS_1_USER, TENANT_A_USER);

  @TenantDefinition
  private static final TestTenant TENANT_B_DEFINITION =
      new TestTenant(TENANT_B).setName(TENANT_B).addUsers(ADMIN, PROCESS_1_USER);

  private static DatabaseType databaseType;

  @BeforeAll
  static void seed() throws IOException {
    // the index prefix of a physical tenant is not the test class name
    assumeThat(CamundaMultiDBExtension.getPhysicalTenant()).isNull();

    final var changes =
        List.of(
            change(1, VARIABLE_ORDER, "order", PROCESS_1, DEFINITION_1, TENANT_A, INSTANCE_1)
                .setScopeKey(INSTANCE_1)
                .setTimestamp(T0)
                .setOperationType(VariableHistoryOperationType.CREATE)
                .setVariableOperationType(VariableOperationType.API)
                .setActorType(VariableHistoryActorType.USER)
                .setActorId(ALICE),
            change(2, VARIABLE_ORDER, "order", PROCESS_1, DEFINITION_1, TENANT_A, INSTANCE_1)
                .setScopeKey(ELEMENT_INSTANCE_1)
                .setElementInstanceKey(ELEMENT_INSTANCE_1)
                .setElementId("task-1")
                .setTimestamp(T1)
                .setOperationType(VariableHistoryOperationType.UPDATE)
                .setVariableOperationType(VariableOperationType.OUTPUT_MAPPING)
                .setActorType(VariableHistoryActorType.CLIENT)
                .setActorId(CLIENT),
            change(3, VARIABLE_ORDER, "order", PROCESS_1, DEFINITION_1, TENANT_A, INSTANCE_1)
                .setScopeKey(ELEMENT_INSTANCE_1)
                .setElementInstanceKey(ELEMENT_INSTANCE_1)
                .setElementId("task-1")
                .setTimestamp(T1)
                .setOperationType(VariableHistoryOperationType.UPDATE)
                .setVariableOperationType(VariableOperationType.JOB_COMPLETION)
                .setActorType(VariableHistoryActorType.ENGINE),
            change(4, VARIABLE_BIG, "big", PROCESS_1, DEFINITION_1, TENANT_A, INSTANCE_2)
                .setScopeKey(INSTANCE_2)
                .setElementInstanceKey(ELEMENT_INSTANCE_2)
                .setElementId("task-2")
                .setTimestamp(T0.plusHours(2))
                .setOperationType(VariableHistoryOperationType.CREATE)
                .setVariableOperationType(VariableOperationType.OUTPUT_MAPPING)
                .setActorType(VariableHistoryActorType.CLIENT)
                .setActorId(CLIENT)
                .setAgentElementId(AGENT_ELEMENT)
                .setValue(PREVIEW)
                .setFullValue(FULL_VALUE)
                .setIsPreview(true),
            change(5, VARIABLE_OTHER, "other", PROCESS_2, DEFINITION_2, TENANT_A, INSTANCE_3)
                .setScopeKey(INSTANCE_3)
                .setTimestamp(T0.plusMinutes(30))
                .setOperationType(VariableHistoryOperationType.CREATE)
                .setVariableOperationType(VariableOperationType.API)
                .setActorType(VariableHistoryActorType.USER)
                .setActorId(BOB),
            change(6, VARIABLE_TENANT_B, "tenant-b", PROCESS_1, DEFINITION_1, TENANT_B, INSTANCE_4)
                .setScopeKey(INSTANCE_4)
                .setTimestamp(T0.plusHours(3))
                .setOperationType(VariableHistoryOperationType.CREATE)
                .setVariableOperationType(VariableOperationType.API)
                .setActorType(VariableHistoryActorType.USER)
                .setActorId(ALICE),
            change(7, VARIABLE_OTHER_B, "other-b", PROCESS_2, DEFINITION_2, TENANT_B, INSTANCE_5)
                .setScopeKey(INSTANCE_5)
                .setTimestamp(T0.plusHours(4))
                .setOperationType(VariableHistoryOperationType.CREATE)
                .setVariableOperationType(VariableOperationType.PROCESS_INSTANCE_CREATION)
                .setActorType(VariableHistoryActorType.ENGINE));

    index(changes);
  }

  @Test
  void shouldReturnTheChangesOfAVariableOldestFirst() throws Exception {
    // given
    final var body = Map.of("filter", Map.of("variableKey", String.valueOf(VARIABLE_ORDER)));

    // when
    final var response = search(ADMIN, "", body);

    // then
    assertThat(itemKeys(response)).containsExactly(itemKey(1), itemKey(2), itemKey(3));
    assertThat(response.at("/page/totalItems").asLong()).isEqualTo(3);

    final var created = response.at("/items/0");
    assertThat(created.get("variableKey").asText()).isEqualTo(String.valueOf(VARIABLE_ORDER));
    assertThat(created.get("name").asText()).isEqualTo("order");
    assertThat(created.get("scopeKey").asText()).isEqualTo(String.valueOf(INSTANCE_1));
    assertThat(created.get("processInstanceKey").asText()).isEqualTo(String.valueOf(INSTANCE_1));
    assertThat(created.get("rootProcessInstanceKey").asText())
        .isEqualTo(String.valueOf(ROOT_INSTANCE));
    assertThat(timestamp(created)).isEqualTo(T0.toInstant());
    assertThat(created.get("processDefinitionKey").asText())
        .isEqualTo(String.valueOf(DEFINITION_1));
    assertThat(created.get("processDefinitionId").asText()).isEqualTo(PROCESS_1);
    assertThat(created.get("tenantId").asText()).isEqualTo(TENANT_A);
    assertThat(created.get("operationType").asText()).isEqualTo("CREATE");
    assertThat(created.get("variableOperationType").asText()).isEqualTo("API");
    assertThat(created.get("actorType").asText()).isEqualTo("USER");
    assertThat(created.get("actorId").asText()).isEqualTo(ALICE);
    assertThat(created.get("isTruncated").asBoolean()).isFalse();
    assertThat(created.hasNonNull("elementInstanceKey")).isFalse();
    assertThat(created.hasNonNull("elementId")).isFalse();
    assertThat(created.hasNonNull("agentElementId")).isFalse();

    final var updated = response.at("/items/1");
    assertThat(updated.get("scopeKey").asText()).isEqualTo(String.valueOf(ELEMENT_INSTANCE_1));
    assertThat(updated.get("processInstanceKey").asText()).isEqualTo(String.valueOf(INSTANCE_1));
    assertThat(timestamp(updated)).isEqualTo(T1.toInstant());
    assertThat(updated.get("elementInstanceKey").asText())
        .isEqualTo(String.valueOf(ELEMENT_INSTANCE_1));
    assertThat(updated.get("elementId").asText()).isEqualTo("task-1");
    assertThat(updated.get("operationType").asText()).isEqualTo("UPDATE");
    assertThat(updated.get("variableOperationType").asText()).isEqualTo("OUTPUT_MAPPING");
    assertThat(updated.get("actorType").asText()).isEqualTo("CLIENT");
  }

  @Test
  void shouldReturnTheAgentOfAChange() throws Exception {
    // given
    final var body = Map.of("filter", Map.of("variableHistoryItemKey", String.valueOf(itemKey(4))));

    // when
    final var response = search(ADMIN, "", body);

    // then
    final var change = response.at("/items/0");
    assertThat(change.get("agentElementId").asText()).isEqualTo(AGENT_ELEMENT);
    assertThat(change.get("elementInstanceKey").asText())
        .isEqualTo(String.valueOf(ELEMENT_INSTANCE_2));
    assertThat(change.get("elementId").asText()).isEqualTo("task-2");
    assertThat(timestamp(change)).isEqualTo(T0.plusHours(2).toInstant());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("filters")
  void shouldFilterBy(
      final String name, final Map<String, Object> filter, final List<Integer> expectedItems)
      throws Exception {
    // given
    final var body = Map.of("filter", filter);

    // when
    final var response = search(ADMIN, "", body);

    // then
    assertThat(itemKeys(response))
        .containsExactlyElementsOf(expectedItems.stream().map(n -> itemKey(n)).toList());
  }

  @Test
  void shouldSortByVariableHistoryItemKeyDescending() throws Exception {
    // given
    final var body =
        Map.of("sort", List.of(Map.of("field", "variableHistoryItemKey", "order", "DESC")));

    // when
    final var response = search(ADMIN, "", body);

    // then
    assertThat(itemKeys(response))
        .containsExactly(
            itemKey(7), itemKey(6), itemKey(5), itemKey(4), itemKey(3), itemKey(2), itemKey(1));
  }

  @Test
  void shouldPageWithOffset() throws Exception {
    // given
    final var body = Map.of("page", Map.of("from", 2, "limit", 3));

    // when
    final var response = search(ADMIN, "", body);

    // then
    assertThat(itemKeys(response)).containsExactly(itemKey(3), itemKey(4), itemKey(5));
    assertThat(response.at("/page/totalItems").asLong()).isEqualTo(7);
  }

  @Test
  void shouldPageWithCursor() throws Exception {
    // given
    final var firstPage = search(ADMIN, "", Map.of("page", Map.of("limit", 3)));
    assertThat(itemKeys(firstPage)).containsExactly(itemKey(1), itemKey(2), itemKey(3));

    // when
    final var secondPage =
        search(
            ADMIN,
            "",
            Map.of("page", Map.of("limit", 3, "after", firstPage.at("/page/endCursor").asText())));
    final var previousPage =
        search(
            ADMIN,
            "",
            Map.of(
                "page", Map.of("limit", 2, "before", secondPage.at("/page/startCursor").asText())));

    // then
    assertThat(itemKeys(secondPage)).containsExactly(itemKey(4), itemKey(5), itemKey(6));
    assertThat(itemKeys(previousPage)).containsExactly(itemKey(2), itemKey(3));
  }

  @Test
  void shouldRejectAnInvalidCursor() throws Exception {
    // given
    final var body = Map.of("page", Map.of("after", "not-a-cursor"));

    // when
    final var response = post(ADMIN, "", body);

    // then
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.headers().firstValue("Content-Type").orElse(""))
        .contains("application/problem+json");
  }

  @Test
  void shouldReturnThePreviewOrTheFullValue() throws Exception {
    // given
    final var body = Map.of("filter", Map.of("variableHistoryItemKey", String.valueOf(itemKey(4))));

    // when
    final var truncated = search(ADMIN, "", body);
    final var full = search(ADMIN, "?truncateValues=false", body);

    // then
    assertThat(truncated.at("/items/0/value").asText()).isEqualTo(PREVIEW);
    assertThat(truncated.at("/items/0/isTruncated").asBoolean()).isTrue();
    assertThat(full.at("/items/0/value").asText()).isEqualTo(FULL_VALUE);
    assertThat(full.at("/items/0/isTruncated").asBoolean()).isFalse();
  }

  @Test
  void shouldLeaveOutChangesOfProcessDefinitionsTheUserMayNotRead() throws Exception {
    // when
    final var response = search(PROCESS_1_USER, "", Map.of());

    // then
    assertThat(itemKeys(response))
        .containsExactly(itemKey(1), itemKey(2), itemKey(3), itemKey(4), itemKey(6));
    assertThat(response.at("/page/totalItems").asLong()).isEqualTo(5);
  }

  @Test
  void shouldLeaveOutChangesOfTenantsTheUserIsNotAssignedTo() throws Exception {
    // when
    final var response = search(TENANT_A_USER, "", Map.of());

    // then
    assertThat(itemKeys(response))
        .containsExactly(itemKey(1), itemKey(2), itemKey(3), itemKey(4), itemKey(5));
    assertThat(response.at("/page/totalItems").asLong()).isEqualTo(5);
  }

  static Stream<Arguments> filters() {
    return Stream.of(
        Arguments.of(
            "variableKey", Map.of("variableKey", String.valueOf(VARIABLE_ORDER)), List.of(1, 2, 3)),
        Arguments.of(
            "processInstanceKey in",
            Map.of(
                "processInstanceKey",
                Map.of("$in", List.of(String.valueOf(INSTANCE_1), String.valueOf(INSTANCE_2)))),
            List.of(1, 2, 3, 4)),
        Arguments.of(
            "name in", Map.of("name", Map.of("$in", List.of("big", "other"))), List.of(4, 5)),
        Arguments.of("scopeKey", Map.of("scopeKey", String.valueOf(INSTANCE_1)), List.of(1)),
        Arguments.of(
            "elementId exists", Map.of("elementId", Map.of("$exists", true)), List.of(2, 3, 4)),
        Arguments.of(
            "elementInstanceKey",
            Map.of("elementInstanceKey", String.valueOf(ELEMENT_INSTANCE_2)),
            List.of(4)),
        Arguments.of(
            "variableHistoryItemKey",
            Map.of("variableHistoryItemKey", String.valueOf(itemKey(5))),
            List.of(5)),
        Arguments.of(
            "variableHistoryItemKey in",
            Map.of(
                "variableHistoryItemKey",
                Map.of("$in", List.of(String.valueOf(itemKey(1)), String.valueOf(itemKey(7))))),
            List.of(1, 7)),
        Arguments.of(
            "processDefinitionKey",
            Map.of("processDefinitionKey", String.valueOf(DEFINITION_2)),
            List.of(5, 7)),
        Arguments.of(
            "processDefinitionId",
            Map.of("processDefinitionId", PROCESS_1),
            List.of(1, 2, 3, 4, 6)),
        Arguments.of("tenantId", Map.of("tenantId", TENANT_B), List.of(6, 7)),
        Arguments.of(
            "timestamp greater than",
            Map.of("timestamp", Map.of("$gt", "2025-01-01T11:00:00.000Z")),
            List.of(4, 6, 7)),
        Arguments.of("operationType", Map.of("operationType", "UPDATE"), List.of(2, 3)),
        Arguments.of(
            "variableOperationType",
            Map.of("variableOperationType", "OUTPUT_MAPPING"),
            List.of(2, 4)),
        Arguments.of("actorType", Map.of("actorType", "ENGINE"), List.of(3, 7)),
        Arguments.of("actorId", Map.of("actorId", ALICE), List.of(1, 6)),
        Arguments.of("agentElementId", Map.of("agentElementId", AGENT_ELEMENT), List.of(4)));
  }

  private static long key(final long position) {
    return Protocol.encodePartitionId(PARTITION_ID, position);
  }

  private static long itemKey(final long number) {
    return key(ITEM_POSITION_BASE + number);
  }

  private static VariableHistoryEntity change(
      final long number,
      final long variableKey,
      final String name,
      final String processDefinitionId,
      final long processDefinitionKey,
      final String tenantId,
      final long processInstanceKey) {
    return new VariableHistoryEntity()
        .setId(PARTITION_ID + "-" + (ITEM_POSITION_BASE + number))
        .setVariableHistoryItemKey(itemKey(number))
        .setVariableKey(variableKey)
        .setName(name)
        .setValue("\"" + name + "-" + number + "\"")
        .setIsPreview(false)
        .setProcessInstanceKey(processInstanceKey)
        .setRootProcessInstanceKey(ROOT_INSTANCE)
        .setProcessDefinitionId(processDefinitionId)
        .setProcessDefinitionKey(processDefinitionKey)
        .setTenantId(tenantId);
  }

  private static void index(final List<VariableHistoryEntity> changes) throws IOException {
    final var isElasticsearch = databaseType.storageType() == ELASTICSEARCH;
    final var index =
        new IndexDescriptors(
                VariableHistorySearchIT.class.getSimpleName().toLowerCase(), isElasticsearch)
            .get(VariableHistoryTemplate.class)
            .getFullQualifiedName();

    if (isElasticsearch) {
      final var client = BROKER.bean(ElasticsearchClient.class);
      for (final var change : changes) {
        client.index(
            i ->
                i.index(index)
                    .id(change.getId())
                    .document(change)
                    .refresh(co.elastic.clients.elasticsearch._types.Refresh.True));
      }
    } else {
      final var client = BROKER.bean(OpenSearchClient.class);
      for (final var change : changes) {
        client.index(i -> i.index(index).id(change.getId()).document(change).refresh(Refresh.True));
      }
    }
  }

  private static JsonNode search(final String user, final String query, final Object body)
      throws Exception {
    final var response = post(user, query, body);
    assertThat(response.statusCode()).describedAs(response.body()).isEqualTo(200);
    return MAPPER.readTree(response.body());
  }

  private static HttpResponse<String> post(final String user, final String query, final Object body)
      throws Exception {
    final var base = BROKER.restAddress().toString();
    final var separator = base.endsWith("/") ? "" : "/";
    final var request =
        HttpRequest.newBuilder()
            .uri(URI.create(base + separator + "v2/variables/history/search" + query))
            .header("Content-Type", "application/json")
            .header(
                "Authorization",
                "Basic "
                    + Base64.getEncoder()
                        .encodeToString((user + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8)))
            .POST(BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
            .build();
    return HTTP.send(request, BodyHandlers.ofString());
  }

  private static Instant timestamp(final JsonNode item) {
    return OffsetDateTime.parse(item.get("timestamp").asText()).toInstant();
  }

  private static List<Long> itemKeys(final JsonNode response) {
    return StreamSupport.stream(response.get("items").spliterator(), false)
        .map(item -> Long.parseLong(item.get("variableHistoryItemKey").asText()))
        .toList();
  }
}
