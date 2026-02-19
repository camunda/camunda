/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.getScopedVariables;
import static io.camunda.it.util.TestHelper.startScopedProcessInstance;
import static io.camunda.it.util.TestHelper.waitForBatchOperationCompleted;
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static io.camunda.it.util.TestHelper.waitForProcessInstanceToBeTerminated;
import static io.camunda.it.util.TestHelper.waitForScopedActiveProcessInstances;
import static io.camunda.search.exception.ErrorMessages.ERROR_ENTITY_BY_ID_NOT_FOUND;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.MigrationPlan;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.AuditLogActorTypeEnum;
import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.enums.BatchOperationType;
import io.camunda.client.api.search.response.BatchOperation;
import io.camunda.client.api.search.response.BatchOperationItems.BatchOperationItem;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.BatchOperationItemSort;
import io.camunda.client.api.search.sort.BatchOperationSort;
import io.camunda.client.impl.search.filter.ProcessInstanceFilterImpl;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.configuration.InitializationConfiguration;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class BatchOperationSearchIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static String testScopeId;

  private static String batchOperationKey1;
  private static String batchOperationKey2;

  private static final List<Long> ACTIVE_PROCESS_INSTANCES_1 = new ArrayList<>();
  private static final List<Long> ACTIVE_PROCESS_INSTANCES_2 = new ArrayList<>();

  @BeforeAll
  public static void beforeAll(
      final TestInfo testInfo, @Authenticated final CamundaClient camundaClient) {
    testScopeId =
        testInfo.getTestMethod().map(Method::toString).orElse(UUID.randomUUID().toString());

    deployProcessAndWaitForIt(camundaClient, "process/service_tasks_v1.bpmn")
        .getProcessDefinitionKey();
    final long sourceProcessDefinitionKey =
        deployProcessAndWaitForIt(camundaClient, "process/migration-process_v1.bpmn")
            .getProcessDefinitionKey();
    final var targetProcessDefinitionKey =
        deployProcessAndWaitForIt(camundaClient, "process/migration-process_v2.bpmn")
            .getProcessDefinitionKey();

    // Batch 1 - Cancel Processes
    IntStream.range(0, 10)
        .forEach(
            i ->
                ACTIVE_PROCESS_INSTANCES_1.add(
                    startScopedProcessInstance(
                            camundaClient, "service_tasks_v1", testScopeId, emptyMap())
                        .getProcessInstanceKey()));

    waitForScopedActiveProcessInstances(
        camundaClient, testScopeId, ACTIVE_PROCESS_INSTANCES_1.size());

    batchOperationKey1 = startBatchOperationCancelProcesses(camundaClient, testScopeId);

    waitForBatchOperationWithCorrectTotalCount(
        camundaClient, batchOperationKey1, ACTIVE_PROCESS_INSTANCES_1.size());

    waitForBatchOperationCompleted(
        camundaClient, batchOperationKey1, ACTIVE_PROCESS_INSTANCES_1.size(), 0);

    for (final Long key : ACTIVE_PROCESS_INSTANCES_1) {
      waitForProcessInstanceToBeTerminated(camundaClient, key);
    }

    // Batch 2 - Migrate Processes
    IntStream.range(0, 5)
        .forEach(
            i ->
                ACTIVE_PROCESS_INSTANCES_2.add(
                    startScopedProcessInstance(
                            camundaClient,
                            sourceProcessDefinitionKey,
                            testScopeId,
                            Map.of("foo", "bar"))
                        .getProcessInstanceKey()));

    waitForScopedActiveProcessInstances(
        camundaClient, testScopeId, ACTIVE_PROCESS_INSTANCES_2.size());

    batchOperationKey2 =
        startBatchOperationMigrateProcesses(
            camundaClient, testScopeId, sourceProcessDefinitionKey, targetProcessDefinitionKey);

    waitForBatchOperationWithCorrectTotalCount(
        camundaClient, batchOperationKey2, ACTIVE_PROCESS_INSTANCES_2.size());

    waitForBatchOperationCompleted(
        camundaClient, batchOperationKey2, ACTIVE_PROCESS_INSTANCES_2.size(), 0);
  }

  @Test
  void shouldGetBatchOperation(@Authenticated final CamundaClient camundaClient) {
    // when
    final var batch = camundaClient.newBatchOperationGetRequest(batchOperationKey1).send().join();
    final var batch2 = camundaClient.newBatchOperationGetRequest(batchOperationKey2).send().join();

    // then
    assertCancelBatchOperation(batch);
    assertMigrateBatchOperation(batch2);

    // and when we query items
    final var items1 =
        camundaClient
            .newBatchOperationItemsSearchRequest()
            .filter(f -> f.batchOperationKey(batchOperationKey1))
            .send()
            .join();
    final var items2 =
        camundaClient
            .newBatchOperationItemsSearchRequest()
            .filter(f -> f.batchOperationKey(batchOperationKey2))
            .send()
            .join();

    // then
    assertItems(items1, ACTIVE_PROCESS_INSTANCES_1);
    assertItems(items2, ACTIVE_PROCESS_INSTANCES_2);
  }

  @Test
  void shouldReturnNotFoundOnGetWhenIdDoesNotExist(
      @Authenticated final CamundaClient camundaClient) {
    // when / then
    assertThatThrownBy(
            () -> camundaClient.newBatchOperationGetRequest("someBatchOperation").send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            ERROR_ENTITY_BY_ID_NOT_FOUND.formatted("Batch Operation", "id", "someBatchOperation"));
  }

  @Test
  void shouldSearchBatchOperationWithIn(@Authenticated final CamundaClient camundaClient) {
    // when
    final var page =
        camundaClient
            .newBatchOperationSearchRequest()
            .filter(
                f ->
                    f.batchOperationKey(batchOperationKey2)
                        .operationType(
                            p ->
                                p.in(
                                    BatchOperationType.CANCEL_PROCESS_INSTANCE,
                                    BatchOperationType.MIGRATE_PROCESS_INSTANCE))
                        .state(p -> p.in(BatchOperationState.COMPLETED)))
            .send()
            .join();

    // then
    assertThat(page).isNotNull();
    assertThat(page.page().totalItems()).isEqualTo(1);
    final var items = page.items();
    assertThat(items.size()).isEqualTo(1);
    assertMigrateBatchOperation(items.getFirst());
  }

  @Test
  void shouldSearchBatchOperationItemsWithIn(@Authenticated final CamundaClient camundaClient) {
    // when
    final var page =
        camundaClient
            .newBatchOperationItemsSearchRequest()
            .filter(
                f ->
                    f.batchOperationKey(p -> p.in(batchOperationKey1))
                        .processInstanceKey(p -> p.in(ACTIVE_PROCESS_INSTANCES_1))
                        .itemKey(p -> p.in(ACTIVE_PROCESS_INSTANCES_1))
                        .state(p -> p.in(BatchOperationItemState.COMPLETED)))
            .send()
            .join();

    // then
    assertThat(page).isNotNull();
    assertItems(page, ACTIVE_PROCESS_INSTANCES_1);
  }

  @Test
  void shouldSearchCompletedBatchOperation(@Authenticated final CamundaClient camundaClient) {
    // when
    final var page =
        camundaClient
            .newBatchOperationSearchRequest()
            .filter(f -> f.state(BatchOperationState.COMPLETED))
            .send()
            .join();

    // then
    assertThat(page).isNotNull();
    assertThat(page.page().totalItems()).isEqualTo(2);
    final var items = page.items();
    assertThat(items.size()).isEqualTo(2);
    final var batch1 =
        items.stream().filter(b -> b.getBatchOperationKey().equals(batchOperationKey1)).findFirst();
    assertThat(batch1).isPresent();
    assertCancelBatchOperation(batch1.get());
    final var batch2 =
        items.stream().filter(b -> b.getBatchOperationKey().equals(batchOperationKey2)).findFirst();
    assertThat(batch2).isPresent();
    assertMigrateBatchOperation(batch2.get());
  }

  @Test
  void shouldSearchNotCompletedBatchOperation(@Authenticated final CamundaClient camundaClient) {
    // when
    final var page =
        camundaClient
            .newBatchOperationSearchRequest()
            .filter(f -> f.state(p -> p.neq(BatchOperationState.COMPLETED)))
            .send()
            .join();

    // then
    assertThat(page).isNotNull();
    assertThat(page.page().totalItems()).isEqualTo(0);
    final var items = page.items();
    assertThat(items.size()).isEqualTo(0);
  }

  @Test
  void shouldSearchCancelProcessesBatchOperation(@Authenticated final CamundaClient camundaClient) {
    // when
    final var page =
        camundaClient
            .newBatchOperationSearchRequest()
            .filter(f -> f.operationType(BatchOperationType.CANCEL_PROCESS_INSTANCE))
            .send()
            .join();

    // then
    assertThat(page).isNotNull();
    assertThat(page.page().totalItems()).isEqualTo(1);
    final var items = page.items();
    assertThat(items.size()).isEqualTo(1);
    assertCancelBatchOperation(items.getFirst());
  }

  @Test
  void shouldSearchMigrateProcessesBatchOperationWithNeq(
      @Authenticated final CamundaClient camundaClient) {
    // when
    final var page =
        camundaClient
            .newBatchOperationSearchRequest()
            .filter(f -> f.operationType(p -> p.neq(BatchOperationType.CANCEL_PROCESS_INSTANCE)))
            .send()
            .join();

    // then
    assertThat(page).isNotNull();
    assertThat(page.page().totalItems()).isEqualTo(1);
    final var items = page.items();
    assertThat(items.size()).isEqualTo(1);
    assertMigrateBatchOperation(items.getFirst());
  }

  @Test
  void shouldSearchBatchOperationItemsWithNeq(@Authenticated final CamundaClient camundaClient) {
    // when
    final var page =
        camundaClient
            .newBatchOperationItemsSearchRequest()
            .filter(
                f ->
                    f.batchOperationKey(p -> p.neq(batchOperationKey1))
                        .state(p -> p.neq(BatchOperationItemState.ACTIVE)))
            .send()
            .join();

    // then
    assertThat(page).isNotNull();
    assertItems(page, ACTIVE_PROCESS_INSTANCES_2);
  }

  @Test
  void shouldSearchBatchOperationItemsWithNotIn(@Authenticated final CamundaClient camundaClient) {
    // when
    final var page =
        camundaClient
            .newBatchOperationItemsSearchRequest()
            .filter(
                f ->
                    f.batchOperationKey(p -> p.notIn(batchOperationKey1))
                        .processInstanceKey(p -> p.notIn(ACTIVE_PROCESS_INSTANCES_1))
                        .itemKey(p -> p.notIn(ACTIVE_PROCESS_INSTANCES_1)))
            .send()
            .join();

    // then
    assertThat(page).isNotNull();
    assertItems(page, ACTIVE_PROCESS_INSTANCES_2);
  }

  @Test
  void shouldSearchBatchOperationItemsByOperationType(
      @Authenticated final CamundaClient camundaClient) {
    // when
    final var page =
        camundaClient
            .newBatchOperationItemsSearchRequest()
            .filter(f -> f.operationType(BatchOperationType.CANCEL_PROCESS_INSTANCE))
            .send()
            .join();

    // then
    assertThat(page).isNotNull();
    assertItems(page, ACTIVE_PROCESS_INSTANCES_1);
  }

  @Test
  void shouldSearchBatchOperationItemsByOperationTypeWithNeq(
      @Authenticated final CamundaClient camundaClient) {
    // when
    final var page =
        camundaClient
            .newBatchOperationItemsSearchRequest()
            .filter(f -> f.operationType(p -> p.neq(BatchOperationType.CANCEL_PROCESS_INSTANCE)))
            .send()
            .join();

    // then
    assertThat(page).isNotNull();
    assertItems(page, ACTIVE_PROCESS_INSTANCES_2);
  }

  @Test
  void shouldSearchProcessInstanceByBatchOperationKey(
      @Authenticated final CamundaClient camundaClient) {
    // when
    final var page =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.batchOperationId(batchOperationKey1))
            .send()
            .join();

    // then
    assertThat(page).isNotNull();
    assertThat(page.items()).hasSize(ACTIVE_PROCESS_INSTANCES_1.size());
    assertThat(page.items().stream().map(ProcessInstance::getProcessInstanceKey))
        .containsExactlyInAnyOrderElementsOf(ACTIVE_PROCESS_INSTANCES_1);
  }

  @ParameterizedTest(name = "{argumentSetName}")
  @MethodSource("sortOptions")
  <T extends Comparable<? super T>> void shouldSortBatchOperationByAttribute(
      final Function<BatchOperation, T> attributeExtractor,
      final Function<BatchOperationSort, BatchOperationSort> sortOption,
      @Authenticated final CamundaClient camundaClient) {
    // given
    final var all =
        camundaClient.newBatchOperationSearchRequest().execute().items().stream()
            .map(attributeExtractor)
            .toList();
    // when
    final var resultAsc =
        camundaClient
            .newBatchOperationSearchRequest()
            .sort(s -> sortOption.apply(s).asc())
            .execute();
    final var resultDesc =
        camundaClient
            .newBatchOperationSearchRequest()
            .sort(s -> sortOption.apply(s).desc())
            .execute();

    // then
    assertThat(resultAsc.items().stream().map(attributeExtractor).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items().stream().map(attributeExtractor).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.reverseOrder()).toList());
  }

  @ParameterizedTest(name = "{argumentSetName}")
  @MethodSource("sortItemOptions")
  <T extends Comparable<? super T>> void shouldSortBatchOperationItemsByAttribute(
      final Function<BatchOperationItem, T> attributeExtractor,
      final Function<BatchOperationItemSort, BatchOperationItemSort> sortOption,
      @Authenticated final CamundaClient camundaClient) {
    // given
    final var all =
        camundaClient.newBatchOperationItemsSearchRequest().execute().items().stream()
            .map(attributeExtractor)
            .toList();
    // when
    final var resultAsc =
        camundaClient
            .newBatchOperationItemsSearchRequest()
            .sort(s -> sortOption.apply(s).asc())
            .execute();
    final var resultDesc =
        camundaClient
            .newBatchOperationItemsSearchRequest()
            .sort(s -> sortOption.apply(s).desc())
            .execute();

    // then
    assertThat(resultAsc.items().stream().map(attributeExtractor).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.naturalOrder()).toList());
    assertThat(resultDesc.items().stream().map(attributeExtractor).filter(Objects::nonNull))
        .containsExactlyElementsOf(
            all.stream().filter(Objects::nonNull).sorted(Comparator.reverseOrder()).toList());
  }

  private static void assertCancelBatchOperation(final BatchOperation batch) {
    assertThat(batch).isNotNull();
    assertThat(batch.getStartDate()).isNotNull();
    assertThat(batch.getBatchOperationKey()).isEqualTo(batchOperationKey1);
    assertThat(batch.getType()).isEqualTo(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(batch.getStatus()).isEqualTo(BatchOperationState.COMPLETED);
    assertThat(batch.getOperationsTotalCount()).isEqualTo(ACTIVE_PROCESS_INSTANCES_1.size());
    assertThat(batch.getOperationsCompletedCount()).isEqualTo(ACTIVE_PROCESS_INSTANCES_1.size());
    assertThat(batch.getOperationsFailedCount()).isEqualTo(0);
    assertThat(batch.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
    assertThat(batch.getActorId()).isEqualTo(InitializationConfiguration.DEFAULT_USER_USERNAME);
  }

  private static void assertMigrateBatchOperation(final BatchOperation batch) {
    assertThat(batch).isNotNull();
    assertThat(batch.getStartDate()).isNotNull();
    assertThat(batch.getBatchOperationKey()).isEqualTo(batchOperationKey2);
    assertThat(batch.getType()).isEqualTo(BatchOperationType.MIGRATE_PROCESS_INSTANCE);
    assertThat(batch.getStatus()).isEqualTo(BatchOperationState.COMPLETED);
    assertThat(batch.getOperationsTotalCount()).isEqualTo(ACTIVE_PROCESS_INSTANCES_2.size());
    assertThat(batch.getOperationsCompletedCount()).isEqualTo(ACTIVE_PROCESS_INSTANCES_2.size());
    assertThat(batch.getOperationsFailedCount()).isEqualTo(0);
    assertThat(batch.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
    assertThat(batch.getActorId()).isEqualTo(InitializationConfiguration.DEFAULT_USER_USERNAME);
  }

  private static void assertItems(
      final SearchResponse<BatchOperationItem> searchResponse,
      final List<Long> expectedProcessInstanceKeys) {
    assertThat(searchResponse).isNotNull();
    assertThat(searchResponse.page().totalItems()).isEqualTo(expectedProcessInstanceKeys.size());
    final var items = searchResponse.items();
    assertThat(items).isNotNull();
    assertThat(items.size()).isEqualTo(expectedProcessInstanceKeys.size());
    for (final Long key : expectedProcessInstanceKeys) {
      final var item = items.stream().filter(i -> Objects.equals(i.getItemKey(), key)).findFirst();
      assertThat(item).isPresent();
      assertThat(item.get().getProcessInstanceKey()).isEqualTo(key);
      assertThat(item.get().getOperationType()).isNotNull();
      assertThat(item.get().getStatus()).isEqualTo(BatchOperationItemState.COMPLETED);
      assertThat(item.get().getRootProcessInstanceKey()).isEqualTo(key);
    }
  }

  private static String startBatchOperationCancelProcesses(
      final CamundaClient camundaClient, final String testScopeId) {
    final var result =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(b -> b.variables(getScopedVariables(testScopeId)))
            .send()
            .join();

    assertThat(result).isNotNull();

    return result.getBatchOperationKey();
  }

  private static String startBatchOperationMigrateProcesses(
      final CamundaClient camundaClient,
      final String testScopeId,
      final long sourceProcessDefinitionKey,
      final long targetProcessDefinitionKey) {
    final var result =
        camundaClient
            .newCreateBatchOperationCommand()
            .migrateProcessInstance()
            .migrationPlan(
                MigrationPlan.newBuilder()
                    .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
                    .addMappingInstruction("taskA", "taskA2")
                    .addMappingInstruction("taskB", "taskB2")
                    .addMappingInstruction("taskC", "taskC2")
                    .build())
            .filter(
                new ProcessInstanceFilterImpl()
                    .processDefinitionKey(sourceProcessDefinitionKey)
                    .variables(getScopedVariables(testScopeId)))
            .send()
            .join();

    assertThat(result).isNotNull();

    return result.getBatchOperationKey();
  }

  static Stream<Arguments> sortOptions() {
    return Stream.of(
        argumentSet(
            "batchOperationKey",
            new SortTestArguments<>(
                    BatchOperation::getBatchOperationKey, BatchOperationSort::batchOperationKey)
                .get()),
        argumentSet(
            "endDate",
            new SortTestArguments<>(BatchOperation::getEndDate, BatchOperationSort::endDate).get()),
        argumentSet(
            "type",
            new SortTestArguments<>(BatchOperation::getType, BatchOperationSort::operationType)
                .get()),
        argumentSet(
            "startDate",
            new SortTestArguments<>(BatchOperation::getStartDate, BatchOperationSort::startDate)
                .get()),
        argumentSet(
            "actorType",
            new SortTestArguments<>(BatchOperation::getActorType, BatchOperationSort::actorType)
                .get()),
        argumentSet(
            "actorId",
            new SortTestArguments<>(BatchOperation::getActorId, BatchOperationSort::actorId).get()),
        argumentSet(
            "state",
            new SortTestArguments<>(BatchOperation::getStatus, BatchOperationSort::state).get()));
  }

  static Stream<Arguments> sortItemOptions() {
    return Stream.of(
        argumentSet(
            "batchOperationKey",
            new SortItemTestArguments<>(
                    BatchOperationItem::getBatchOperationKey,
                    BatchOperationItemSort::batchOperationKey)
                .get()),
        argumentSet(
            "processInstanceKey",
            new SortItemTestArguments<>(
                    BatchOperationItem::getProcessInstanceKey,
                    BatchOperationItemSort::processInstanceKey)
                .get()),
        argumentSet(
            "itemKey",
            new SortItemTestArguments<>(
                    BatchOperationItem::getItemKey, BatchOperationItemSort::itemKey)
                .get()),
        argumentSet(
            "processedDate",
            new SortItemTestArguments<>(
                    BatchOperationItem::getProcessedDate, BatchOperationItemSort::processedDate)
                .get()),
        argumentSet(
            "state",
            new SortItemTestArguments<>(
                    BatchOperationItem::getStatus, BatchOperationItemSort::state)
                .get()));
  }

  record SortTestArguments<T extends Comparable<? super T>>(
      Function<BatchOperation, T> attributeExtractor,
      Function<BatchOperationSort, BatchOperationSort> sortOption)
      implements Arguments {

    @Override
    public Object[] get() {
      return new Object[] {attributeExtractor, sortOption};
    }
  }

  record SortItemTestArguments<T extends Comparable<? super T>>(
      Function<BatchOperationItem, T> attributeExtractor,
      Function<BatchOperationItemSort, BatchOperationItemSort> sortOption)
      implements Arguments {

    @Override
    public Object[] get() {
      return new Object[] {attributeExtractor, sortOption};
    }
  }
}
