/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.agentinstance;

import static io.camunda.it.rdbms.db.fixtures.AgentInstanceFixtures.createAndSaveRandomAgentInstance;
import static io.camunda.it.rdbms.db.fixtures.AgentInstanceFixtures.createAndSaveRandomAgentInstances;
import static io.camunda.it.rdbms.db.fixtures.AgentInstanceFixtures.createRandomAgentInstance;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel;
import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel.AgentInstanceToolDbValue;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.AgentInstanceEntity;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceStatus;
import io.camunda.search.entities.ContentItem;
import io.camunda.search.entities.ContentItem.ContentType;
import io.camunda.search.filter.AgentInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AgentInstanceQuery;
import io.camunda.search.sort.AgentInstanceSort;
import io.camunda.security.core.authz.ResourceAccessChecks;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class AgentInstanceIT {

  @TestTemplate
  public void shouldCreateAndGetAgentInstanceByKey(
      final CamundaRdbmsTestApplication testApplication) {
    final AgentInstanceDbModel model = createAndSaveRandomAgentInstance(testApplication, b -> b);

    final var entity =
        testApplication
            .getRdbmsService()
            .getAgentInstanceDbReader()
            .getByKey(model.agentInstanceKey(), ResourceAccessChecks.disabled());

    assertThat(entity).isNotNull();
    assertThat(entity.agentInstanceKey()).isEqualTo(model.agentInstanceKey());
    assertFieldsMatch(model, entity);
  }

  @TestTemplate
  public void shouldUpdateDefinitionFieldsOnMigration(
      final CamundaRdbmsTestApplication testApplication) {
    final AgentInstanceDbModel model = createAndSaveRandomAgentInstance(testApplication, b -> b);

    final AgentInstanceDbModel migrated =
        model.copy(
            b ->
                ((AgentInstanceDbModel.Builder) b)
                    .processDefinitionId("migrated-" + nextStringId())
                    .processDefinitionKey(model.processDefinitionKey() + 1)
                    .processDefinitionVersion(model.processDefinitionVersion() + 1)
                    .processDefinitionVersionTag("v2")
                    .agentDefinitionKey(model.agentDefinitionKey() + 1)
                    .elementId("migrated-element"));
    final RdbmsWriters rdbmsWriters = testApplication.getRdbmsService().createWriter(0);
    rdbmsWriters.getAgentInstanceWriter().update(migrated);
    rdbmsWriters.flush();

    final var entity =
        testApplication
            .getRdbmsService()
            .getAgentInstanceDbReader()
            .getByKey(model.agentInstanceKey(), ResourceAccessChecks.disabled());

    assertThat(entity.processDefinitionId()).isEqualTo(migrated.processDefinitionId());
    assertThat(entity.processDefinitionKey()).isEqualTo(migrated.processDefinitionKey());
    assertThat(entity.processDefinitionVersion()).isEqualTo(migrated.processDefinitionVersion());
    assertThat(entity.processDefinitionVersionTag()).isEqualTo("v2");
    assertThat(entity.agentDefinitionKey()).isEqualTo(migrated.agentDefinitionKey());
    assertThat(entity.elementId()).isEqualTo("migrated-element");
  }

  @TestTemplate
  public void shouldUpdateConfigurationFieldsOnUpdate(
      final CamundaRdbmsTestApplication testApplication) {
    // given — a CONFIGURATION history item commit changes model, provider, systemPrompt, and the
    // limits; these are mutable, not write-once, so an UPDATE must actually persist the new values
    final AgentInstanceDbModel model = createAndSaveRandomAgentInstance(testApplication, b -> b);

    final var updated =
        model.copy(
            b ->
                ((AgentInstanceDbModel.Builder) b)
                    .model("updated-model")
                    .provider("updated-provider")
                    .systemPromptItems(
                        List.of(new ContentItem(ContentType.TEXT, "updated prompt", null, null)))
                    .maxTokens(model.maxTokens() + 1)
                    .maxModelCalls(model.maxModelCalls() + 1)
                    .maxToolCalls(model.maxToolCalls() + 1));
    final RdbmsWriters rdbmsWriters = testApplication.getRdbmsService().createWriter(0);
    rdbmsWriters.getAgentInstanceWriter().update(updated);
    rdbmsWriters.flush();

    final var entity =
        testApplication
            .getRdbmsService()
            .getAgentInstanceDbReader()
            .getByKey(model.agentInstanceKey(), ResourceAccessChecks.disabled());

    assertThat(entity.definition().model()).isEqualTo("updated-model");
    assertThat(entity.definition().provider()).isEqualTo("updated-provider");
    assertThat(entity.definition().systemPrompt())
        .containsExactly(new ContentItem(ContentType.TEXT, "updated prompt", null, null));
    assertThat(entity.limits().maxTokens()).isEqualTo(model.maxTokens() + 1);
    assertThat(entity.limits().maxModelCalls()).isEqualTo(model.maxModelCalls() + 1);
    assertThat(entity.limits().maxToolCalls()).isEqualTo(model.maxToolCalls() + 1);
  }

  @TestTemplate
  public void shouldCoalescePendingCreateAndUpdateBeforeFlush(
      final CamundaRdbmsTestApplication testApplication) {
    // given — a create() queued but not yet flushed
    final RdbmsWriters rdbmsWriters = testApplication.getRdbmsService().createWriter(0);
    final var initialTools =
        List.of(new AgentInstanceToolDbValue("search", "search the web", "el-1"));
    final var created =
        createRandomAgentInstance(
            b ->
                b.status(AgentInstanceStatus.IDLE)
                    .toolValues(initialTools)
                    // Empty on purpose: a non-empty list makes create() queue a second QueueItem
                    // (insertElementInstanceKeys) whose parameter is also an AgentInstanceDbModel,
                    // which UpsertMerger.canBeMerged() can't tell apart from the row-INSERT item
                    // by id+contextType+type alone -- a separate, pre-existing bug (tracked
                    // separately, out of scope here) that would make the update() below merge
                    // into the wrong queued item.
                    .elementInstanceKeys(List.of()));
    rdbmsWriters.getAgentInstanceWriter().create(created);

    // when — a second write to the same row is queued before the first is flushed, so it must
    // coalesce into the still-pending INSERT via UpsertMerger/Copyable.copy() instead of issuing
    // a separate UPDATE. The update also re-resolves agentDefinitionKey (as a migration would), so
    // the merge must carry the new key rather than leaving the create-time one on the pending row.
    final var updatedTools =
        List.of(new AgentInstanceToolDbValue("calculator", "does math", "el-2"));
    final var updated =
        created.copy(
            b ->
                ((AgentInstanceDbModel.Builder) b)
                    .status(AgentInstanceStatus.THINKING)
                    .agentDefinitionKey(created.agentDefinitionKey() + 1)
                    .toolValues(updatedTools));
    rdbmsWriters.getAgentInstanceWriter().update(updated);
    rdbmsWriters.flush();

    // then — only the merged, final state was ever persisted; the update's own copy() carried
    // model/provider/systemPrompt unchanged, and tenantId is still genuinely write-once, so all
    // three still read back exactly as create() originally wrote them
    final var entity =
        testApplication
            .getRdbmsService()
            .getAgentInstanceDbReader()
            .getByKey(created.agentInstanceKey(), ResourceAccessChecks.disabled());

    assertThat(entity).isNotNull();
    assertThat(entity.status()).isEqualTo(AgentInstanceStatus.THINKING);
    assertThat(entity.agentDefinitionKey()).isEqualTo(updated.agentDefinitionKey());
    assertThat(entity.tools()).hasSize(1);
    assertThat(entity.tools().getFirst().name()).isEqualTo("calculator");
    assertThat(entity.definition().model()).isEqualTo(created.model());
    assertThat(entity.definition().provider()).isEqualTo(created.provider());
    assertThat(entity.tenantId()).isEqualTo(created.tenantId());
  }

  @TestTemplate
  public void shouldReturnNullForUnknownKey(final CamundaRdbmsTestApplication testApplication) {
    final var entity =
        testApplication
            .getRdbmsService()
            .getAgentInstanceDbReader()
            .getByKey(Long.MIN_VALUE, ResourceAccessChecks.disabled());

    assertThat(entity).isNull();
  }

  @TestTemplate
  public void shouldFindAllAgentInstancesPaged(final CamundaRdbmsTestApplication testApplication) {
    final String processId = "process-paged-" + nextStringId();
    createAndSaveRandomAgentInstances(testApplication, 20, b -> b.processDefinitionId(processId));

    final var result =
        testApplication
            .getRdbmsService()
            .getAgentInstanceDbReader()
            .search(
                new AgentInstanceQuery(
                    new AgentInstanceFilter.Builder().processDefinitionIds(processId).build(),
                    AgentInstanceSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))),
                ResourceAccessChecks.disabled());

    assertThat(result).isNotNull();
    assertThat(result.total()).isEqualTo(20);
    assertThat(result.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldReturnEmptyResultForPageSizeZero(
      final CamundaRdbmsTestApplication testApplication) {
    final String processId = "process-zero-" + nextStringId();
    createAndSaveRandomAgentInstances(testApplication, 3, b -> b.processDefinitionId(processId));

    final var result =
        testApplication
            .getRdbmsService()
            .getAgentInstanceDbReader()
            .search(
                new AgentInstanceQuery(
                    new AgentInstanceFilter.Builder().processDefinitionIds(processId).build(),
                    AgentInstanceSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(0))),
                ResourceAccessChecks.disabled());

    assertThat(result.total()).isEqualTo(3);
    assertThat(result.items()).isEmpty();
  }

  private void assertFieldsMatch(
      final AgentInstanceDbModel dbModel, final AgentInstanceEntity entity) {
    assertThat(entity.agentInstanceKey()).isEqualTo(dbModel.agentInstanceKey());
    assertThat(entity.agentDefinitionKey()).isEqualTo(dbModel.agentDefinitionKey());
    assertThat(entity.elementId()).isEqualTo(dbModel.elementId());
    assertThat(entity.processInstanceKey()).isEqualTo(dbModel.processInstanceKey());
    assertThat(entity.processDefinitionKey()).isEqualTo(dbModel.processDefinitionKey());
    assertThat(entity.processDefinitionId()).isEqualTo(dbModel.processDefinitionId());
    assertThat(entity.tenantId()).isEqualTo(dbModel.tenantId());
    assertThat(entity.status())
        .isEqualTo(dbModel.status() != null ? dbModel.status() : AgentInstanceStatus.UNKNOWN);
    assertThat(entity.definition().model()).isEqualTo(dbModel.model());
    assertThat(entity.definition().provider()).isEqualTo(dbModel.provider());
    assertThat(entity.definition().systemPrompt()).isEqualTo(dbModel.systemPromptItems());
    assertThat(entity.limits().maxTokens()).isEqualTo(dbModel.maxTokens());
    assertThat(entity.limits().maxModelCalls()).isEqualTo(dbModel.maxModelCalls());
    assertThat(entity.limits().maxToolCalls()).isEqualTo(dbModel.maxToolCalls());
    assertThat(entity.metrics().inputTokens()).isEqualTo(dbModel.inputTokens());
    assertThat(entity.metrics().outputTokens()).isEqualTo(dbModel.outputTokens());
    assertThat(entity.metrics().reasoningTokenCount()).isEqualTo(dbModel.reasoningTokenCount());
    assertThat(entity.metrics().cacheCreationTokenCount())
        .isEqualTo(dbModel.cacheCreationTokenCount());
    assertThat(entity.metrics().cacheReadTokenCount()).isEqualTo(dbModel.cacheReadTokenCount());
    assertThat(entity.metrics().modelCalls()).isEqualTo(dbModel.modelCalls());
    assertThat(entity.metrics().toolCalls()).isEqualTo(dbModel.toolCalls());
  }
}
