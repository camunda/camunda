/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryContent;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryMetrics;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.AgentHistoryRole;
import io.camunda.client.api.response.CreateAgentHistoryItemResponse;
import io.camunda.client.impl.response.DocumentReferenceResponseImpl;
import io.camunda.client.protocol.rest.AgentInstanceDocumentContent;
import io.camunda.client.protocol.rest.AgentInstanceHistoryItemCreationResult;
import io.camunda.client.protocol.rest.AgentInstanceHistoryItemRequest;
import io.camunda.client.protocol.rest.AgentInstanceHistoryRoleEnum;
import io.camunda.client.protocol.rest.DocumentMetadataResponse;
import io.camunda.client.protocol.rest.DocumentReference;
import io.camunda.client.protocol.rest.DocumentReference.CamundaDocumentTypeEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.time.OffsetDateTime;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CreateAgentHistoryItemCommandTest extends ClientRestTest {

  private static final long AGENT_INSTANCE_KEY = 2251799813685248L;
  private static final long ELEMENT_INSTANCE_KEY = 2251799813685249L;
  private static final long JOB_KEY = 2251799813685250L;
  private static final OffsetDateTime PRODUCED_AT = OffsetDateTime.parse("2025-06-01T12:00:00Z");

  // ── Happy-path: request routing ───────────────────────────────────────────

  @Test
  void shouldSendPostToCorrectUrl() {
    // given
    gatewayService.onCreateAgentHistoryItemRequest(
        AGENT_INSTANCE_KEY, new AgentInstanceHistoryItemCreationResult().historyItemKey("1"));

    // when
    client
        .newCreateAgentHistoryItemCommand(AGENT_INSTANCE_KEY)
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .jobKey(JOB_KEY)
        .role(AgentHistoryRole.USER)
        .content(Collections.singletonList(AgentHistoryContent.text("hello")))
        .producedAt(PRODUCED_AT)
        .execute();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
    assertThat(request.getUrl())
        .isEqualTo("/v2/agent-instances/" + AGENT_INSTANCE_KEY + "/history");
  }

  @Test
  void shouldSendRequiredFieldsInRequestBody() {
    // given
    gatewayService.onCreateAgentHistoryItemRequest(
        AGENT_INSTANCE_KEY, new AgentInstanceHistoryItemCreationResult().historyItemKey("1"));

    // when
    client
        .newCreateAgentHistoryItemCommand(AGENT_INSTANCE_KEY)
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .jobKey(JOB_KEY)
        .role(AgentHistoryRole.ASSISTANT)
        .content(Collections.singletonList(AgentHistoryContent.text("I can help.")))
        .producedAt(PRODUCED_AT)
        .execute();

    // then
    final AgentInstanceHistoryItemRequest body =
        gatewayService.getLastRequest(AgentInstanceHistoryItemRequest.class);
    assertThat(body.getElementInstanceKey()).isEqualTo(String.valueOf(ELEMENT_INSTANCE_KEY));
    assertThat(body.getJobKey()).isEqualTo(String.valueOf(JOB_KEY));
    assertThat(body.getJobLease()).isNull();
    assertThat(body.getRole()).isEqualTo(AgentInstanceHistoryRoleEnum.ASSISTANT);
    assertThat(body.getContent()).hasSize(1);
    assertThat(body.getProducedAt()).isEqualTo(PRODUCED_AT.toString());
    assertThat(body.getIteration()).isNull();
    assertThat(body.getToolCalls()).isNull();
    assertThat(body.getMetrics()).isNull();
  }

  @Test
  void shouldSendOptionalFieldsInRequestBody() {
    // given
    gatewayService.onCreateAgentHistoryItemRequest(
        AGENT_INSTANCE_KEY, new AgentInstanceHistoryItemCreationResult().historyItemKey("2"));

    // when
    client
        .newCreateAgentHistoryItemCommand(AGENT_INSTANCE_KEY)
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .jobKey(JOB_KEY)
        .role(AgentHistoryRole.USER)
        .content(Collections.singletonList(AgentHistoryContent.text("hello")))
        .producedAt(PRODUCED_AT)
        .jobLease("lease-token")
        .iteration(3)
        .metrics(new AgentHistoryMetrics().inputTokens(100L).outputTokens(50L).durationMs(200L))
        .execute();

    // then
    final AgentInstanceHistoryItemRequest body =
        gatewayService.getLastRequest(AgentInstanceHistoryItemRequest.class);
    assertThat(body.getJobLease()).isEqualTo("lease-token");
    assertThat(body.getIteration()).isEqualTo(3);
    assertThat(body.getMetrics().getInputTokens()).isEqualTo(100L);
    assertThat(body.getMetrics().getOutputTokens()).isEqualTo(50L);
    assertThat(body.getMetrics().getDurationMs()).isEqualTo(200L);
  }

  @Test
  void shouldParseHistoryItemKeyFromResponse() {
    // given
    gatewayService.onCreateAgentHistoryItemRequest(
        AGENT_INSTANCE_KEY,
        new AgentInstanceHistoryItemCreationResult().historyItemKey("9876543210"));

    // when
    final CreateAgentHistoryItemResponse response =
        client
            .newCreateAgentHistoryItemCommand(AGENT_INSTANCE_KEY)
            .elementInstanceKey(ELEMENT_INSTANCE_KEY)
            .jobKey(JOB_KEY)
            .role(AgentHistoryRole.USER)
            .content(Collections.singletonList(AgentHistoryContent.text("hello")))
            .producedAt(PRODUCED_AT)
            .execute();

    // then
    assertThat(response.getHistoryItemKey()).isEqualTo(9876543210L);
  }

  // ── Argument validation: agentInstanceKey ────────────────────────────────

  @ParameterizedTest(name = "agentInstanceKey={0} should be rejected")
  @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
  void shouldRejectNonPositiveAgentInstanceKey(final long invalidKey) {
    assertThatThrownBy(() -> client.newCreateAgentHistoryItemCommand(invalidKey))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("agentInstanceKey must be greater than 0");
  }

  // ── Argument validation: elementInstanceKey ───────────────────────────────

  @ParameterizedTest(name = "elementInstanceKey={0} should be rejected")
  @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
  void shouldRejectNonPositiveElementInstanceKey(final long invalidKey) {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentHistoryItemCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(invalidKey))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("elementInstanceKey must be greater than 0");
  }

  // ── Argument validation: jobKey ───────────────────────────────────────────

  @ParameterizedTest(name = "jobKey={0} should be rejected")
  @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
  void shouldRejectNonPositiveJobKey(final long invalidKey) {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentHistoryItemCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(invalidKey))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("jobKey must be greater than 0");
  }

  // ── Argument validation: required fields ─────────────────────────────────

  @Test
  void shouldRejectNullRole() {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentHistoryItemCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .role(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("role must not be null");
  }

  @Test
  void shouldRejectNullContent() {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentHistoryItemCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .role(AgentHistoryRole.USER)
                    .content(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("content must not be null");
  }

  @Test
  void shouldRejectNullProducedAt() {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentHistoryItemCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .role(AgentHistoryRole.USER)
                    .content(Collections.singletonList(AgentHistoryContent.text("hello")))
                    .producedAt(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("producedAt must not be null");
  }

  // ── Argument validation: jobLease (optional, but non-blank when set) ──────

  @ParameterizedTest(name = "jobLease=''{0}'' should be rejected")
  @ValueSource(strings = {"", " "})
  void shouldRejectBlankJobLease(final String jobLease) {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentHistoryItemCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .role(AgentHistoryRole.USER)
                    .content(Collections.singletonList(AgentHistoryContent.text("hello")))
                    .producedAt(PRODUCED_AT)
                    .jobLease(jobLease))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("jobLease must not be blank");
  }

  // ── Argument validation: content (empty list) ─────────────────────────────

  @Test
  void shouldRejectEmptyContentList() {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentHistoryItemCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .role(AgentHistoryRole.USER)
                    .content(Collections.emptyList()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("content must not be empty");
  }

  @ParameterizedTest(name = "blank text [{0}] should be rejected")
  @ValueSource(strings = {"", " ", "\t"})
  void shouldRejectBlankTextContent(final String text) {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentHistoryItemCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .role(AgentHistoryRole.USER)
                    .content(Collections.singletonList(AgentHistoryContent.text(text))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("text content value must not be null or blank");
  }

  // ── Argument validation: iteration ───────────────────────────────────────

  @ParameterizedTest(name = "iteration={0} should be rejected")
  @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
  void shouldRejectNonPositiveIteration(final int iteration) {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentHistoryItemCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .role(AgentHistoryRole.USER)
                    .content(Collections.singletonList(AgentHistoryContent.text("hello")))
                    .producedAt(PRODUCED_AT)
                    .iteration(iteration))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("iteration must be greater than 0");
  }

  // ── Argument validation: content factory null guards ─────────────────────

  @Test
  void shouldRejectNullTextArg() {
    assertThatThrownBy(() -> AgentHistoryContent.text(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("text must not be null");
  }

  @Test
  void shouldRejectNullObjectArg() {
    assertThatThrownBy(() -> AgentHistoryContent.object(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("object must not be null");
  }

  @Test
  void shouldRejectNullDocumentReferenceArg() {
    assertThatThrownBy(() -> AgentHistoryContent.document(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("documentReference must not be null");
  }

  @Test
  void shouldRejectNullDocumentId() {
    final DocumentReferenceResponseImpl doc =
        new DocumentReferenceResponseImpl(
            new DocumentReference()
                .camundaDocumentType(CamundaDocumentTypeEnum.CAMUNDA)
                .storeId("store-1")); // documentId not set → null
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentHistoryItemCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .role(AgentHistoryRole.USER)
                    .content(Collections.singletonList(AgentHistoryContent.document(doc))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("documentId must not be null or blank");
  }

  // ── Argument validation: toolCalls ───────────────────────────────────────

  @Test
  void shouldRejectNullToolCallEntry() {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentHistoryItemCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .role(AgentHistoryRole.USER)
                    .content(Collections.singletonList(AgentHistoryContent.text("hello")))
                    .producedAt(PRODUCED_AT)
                    .toolCalls(Collections.singletonList(null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("toolCalls must not contain null elements");
  }

  // ── Happy-path: document content ─────────────────────────────────────────

  @Test
  void shouldSendDocumentContentInRequestBody() {
    // given
    gatewayService.onCreateAgentHistoryItemRequest(
        AGENT_INSTANCE_KEY, new AgentInstanceHistoryItemCreationResult().historyItemKey("3"));
    final DocumentReferenceResponseImpl doc =
        new DocumentReferenceResponseImpl(
            new DocumentReference()
                .camundaDocumentType(CamundaDocumentTypeEnum.CAMUNDA)
                .documentId("doc-abc")
                .storeId("store-1"));

    // when
    client
        .newCreateAgentHistoryItemCommand(AGENT_INSTANCE_KEY)
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .jobKey(JOB_KEY)
        .role(AgentHistoryRole.USER)
        .content(Collections.singletonList(AgentHistoryContent.document(doc)))
        .producedAt(PRODUCED_AT)
        .execute();

    // then
    final AgentInstanceHistoryItemRequest body =
        gatewayService.getLastRequest(AgentInstanceHistoryItemRequest.class);
    assertThat(body.getContent()).hasSize(1);
    assertThat(body.getContent().get(0)).isInstanceOf(AgentInstanceDocumentContent.class);
    final AgentInstanceDocumentContent docContent =
        (AgentInstanceDocumentContent) body.getContent().get(0);
    assertThat(docContent.getDocumentReference().getCamundaDocumentType())
        .isEqualTo(CamundaDocumentTypeEnum.CAMUNDA);
    assertThat(docContent.getDocumentReference().getDocumentId()).isEqualTo("doc-abc");
    assertThat(docContent.getDocumentReference().getStoreId()).isEqualTo("store-1");
    assertThat(docContent.getDocumentReference().getMetadata()).isNull();
  }

  @Test
  void shouldSendDocumentContentWithMetadataInRequestBody() {
    // given
    gatewayService.onCreateAgentHistoryItemRequest(
        AGENT_INSTANCE_KEY, new AgentInstanceHistoryItemCreationResult().historyItemKey("4"));
    final DocumentMetadataResponse meta =
        new DocumentMetadataResponse()
            .fileName("report.pdf")
            .contentType("application/pdf")
            .size(1024L)
            .expiresAt(PRODUCED_AT.toString())
            .processDefinitionId("proc-1")
            .processInstanceKey("42");
    final DocumentReferenceResponseImpl doc =
        new DocumentReferenceResponseImpl(
            new DocumentReference()
                .camundaDocumentType(CamundaDocumentTypeEnum.CAMUNDA)
                .documentId("doc-xyz")
                .storeId("store-2")
                .contentHash("sha256:abc")
                .metadata(meta));

    // when
    client
        .newCreateAgentHistoryItemCommand(AGENT_INSTANCE_KEY)
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .jobKey(JOB_KEY)
        .role(AgentHistoryRole.USER)
        .content(Collections.singletonList(AgentHistoryContent.document(doc)))
        .producedAt(PRODUCED_AT)
        .execute();

    // then
    final AgentInstanceHistoryItemRequest body =
        gatewayService.getLastRequest(AgentInstanceHistoryItemRequest.class);
    final AgentInstanceDocumentContent docContent =
        (AgentInstanceDocumentContent) body.getContent().get(0);
    assertThat(docContent.getDocumentReference().getCamundaDocumentType())
        .isEqualTo(CamundaDocumentTypeEnum.CAMUNDA);
    assertThat(docContent.getDocumentReference().getDocumentId()).isEqualTo("doc-xyz");
    assertThat(docContent.getDocumentReference().getStoreId()).isEqualTo("store-2");
    assertThat(docContent.getDocumentReference().getContentHash()).isEqualTo("sha256:abc");
    final DocumentMetadataResponse mappedMeta = docContent.getDocumentReference().getMetadata();
    assertThat(mappedMeta).isNotNull();
    assertThat(mappedMeta.getFileName()).isEqualTo("report.pdf");
    assertThat(mappedMeta.getContentType()).isEqualTo("application/pdf");
    assertThat(mappedMeta.getSize()).isEqualTo(1024L);
    assertThat(mappedMeta.getExpiresAt()).isEqualTo(PRODUCED_AT.toString());
    assertThat(mappedMeta.getProcessDefinitionId()).isEqualTo("proc-1");
    assertThat(mappedMeta.getProcessInstanceKey()).isEqualTo("42");
  }
}
