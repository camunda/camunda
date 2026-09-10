/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant;

import static io.camunda.it.util.DmnBuilderHelper.getDmnModelInstance;
import static io.camunda.it.util.TestHelper.activateAndFailJobs;
import static io.camunda.it.util.TestHelper.deployDmnModel;
import static io.camunda.it.util.TestHelper.evaluateDecision;
import static io.camunda.it.util.TestHelper.waitForBatchOperationCompleted;
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.MigrationPlan;
import io.camunda.client.api.response.Decision;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.configuration.HistoryDeletion;
import io.camunda.qa.util.multidb.MultiDbPhysicalTenants;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.qa.util.multidb.MultiPhysicalTenantClients;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import java.util.List;
import java.util.function.ToLongFunction;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * A batch operation does not carry the set of keys it will act on. It carries a filter, and the
 * engine resolves that filter against secondary storage through {@code SearchClientsProxy} when the
 * batch is executed. Under physical tenants each tenant has its own secondary storage, so the
 * scoping of a batch rests entirely on the batch being resolved against the storage of the tenant
 * that created it — nothing in the filter itself names a tenant.
 *
 * <p>That resolution happens in one of four {@code ItemProvider} implementations, each of which
 * consumes {@code SearchClientsProxy} independently. This class covers all four, and every
 * process-instance batch operation type that is destructive enough that a scoping bug could not be
 * undone:
 *
 * <ul>
 *   <li>{@code ProcessInstanceItemProvider} — delete, migrate and modify (cancel is covered by
 *       {@code MultiPhysicalTenantAuthorizationIT})
 *   <li>{@code JobItemProvider} — update job
 *   <li>{@code IncidentItemProvider} — resolve incident
 *   <li>{@code DecisionInstanceItemProvider} — delete decision instance
 * </ul>
 *
 * <p>Every test deploys the <em>same</em> resource id into {@code default}, {@code tenanta} and
 * {@code tenantb} and creates the same number of items in each, so the filter each batch is given
 * matches all three tenants' data and only the storage boundary can tell them apart. The batch is
 * always created through the {@code tenantb} admin.
 *
 * <p>Each test makes three assertions, and each one catches a different shape of failure. The
 * batch's total item count must equal one tenant's worth of items, which catches a provider
 * resolving against <em>shared</em> storage: the count comes back as three tenants' worth. The
 * creating tenant's items must actually be acted on, which catches a provider resolving against the
 * <em>wrong</em> tenant's storage — there the count still looks right, because the other tenant
 * holds an identical number of identically-named items, and only the absent effect gives it away.
 * The other two tenants' items must stay untouched, held for a period after the batch has already
 * reported completion, which catches a leak that arrives as a late follow-up command rather than as
 * an extra batch item.
 *
 * <p>The wrong-tenant case was the one confirmed against a real break: pinning every partition to
 * the {@code default} tenant's readers in {@code PartitionManagerImpl} instead of its own makes all
 * six tests fail, and every one of them fails on the second assertion while the item count still
 * reads correct. Do not weaken these tests to the item count alone.
 */
@MultiDbTest
@MultiDbPhysicalTenants({"tenanta", "tenantb"})
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
final class BatchOperationPhysicalTenantScopingIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withAuthenticationMethod(AuthenticationMethod.BASIC)
          .withProcessingConfig(
              config ->
                  config
                      .getEngine()
                      .getBatchOperations()
                      .setSchedulerInterval(Duration.ofMillis(100)))
          // the delete batch operations hand the keys to the history deletion job; without a short
          // interval the deletion only runs long after the batch reports completion
          .withDataConfig(
              config -> {
                final var historyDeletion = new HistoryDeletion();
                historyDeletion.setDelayBetweenRuns(Duration.ofMillis(100));
                historyDeletion.setMaxDelayBetweenRuns(Duration.ofMillis(100));
                config.setHistoryDeletion(historyDeletion);
              });

  // Injected by the extension: admin clients for tenanta and tenantb
  static MultiPhysicalTenantClients ptClients;

  // Injected by the extension: default-PT admin (broker's built-in default user)
  static CamundaClient client;

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final Duration PROPAGATION_TIMEOUT = Duration.ofSeconds(60);
  private static final Duration UNTOUCHED_HOLD = Duration.ofSeconds(5);
  private static final int ITEMS_PER_TENANT = 2;

  @Test
  void shouldScopeDeleteProcessInstanceBatchToCreatingTenant() {
    // given — the same completed instances exist in all three tenants
    final String processId = Strings.newRandomValidBpmnId();
    forEachTenant(admin -> deploy(admin, completingProcess(processId), processId));
    forEachTenant(admin -> startInstances(admin, processId));
    forEachTenant(admin -> awaitInstanceCount(admin, processId, ITEMS_PER_TENANT));

    // when — tenantb deletes by a process definition id that all three tenants share
    final String batchKey =
        createBatch(
            tenantB()
                .newCreateBatchOperationCommand()
                .deleteProcessInstance()
                .filter(f -> f.processDefinitionId(processId)));

    // then
    assertBatchCoversOneTenantsItems(batchKey);
    Awaitility.await("tenantb's instances are deleted")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(instanceCount(tenantB(), processId)).isZero());
    assertBystandersUntouched(
        "instances", admin -> instanceCount(admin, processId), ITEMS_PER_TENANT);
  }

  @Test
  void shouldScopeMigrateProcessInstanceBatchToCreatingTenant() {
    // given — source and target definitions, both deployed under the same ids in all three tenants
    final String sourceProcessId = Strings.newRandomValidBpmnId();
    final String targetProcessId = Strings.newRandomValidBpmnId();
    forEachTenant(
        admin -> deploy(admin, userTaskProcess(sourceProcessId, "taskA"), sourceProcessId));
    final long tenantBTargetKey =
        deploy(tenantB(), userTaskProcess(targetProcessId, "taskB"), targetProcessId);
    deploy(client, userTaskProcess(targetProcessId, "taskB"), targetProcessId);
    deploy(tenantA(), userTaskProcess(targetProcessId, "taskB"), targetProcessId);
    forEachTenant(admin -> startInstances(admin, sourceProcessId));
    forEachTenant(admin -> awaitInstanceCount(admin, sourceProcessId, ITEMS_PER_TENANT));

    // when — tenantb migrates onto its own target definition. The definition key is per-tenant, but
    // partition ids restart at 1 in every tenant, so a leaked instance would find an equally valid
    // definition under the same number rather than failing on an unknown key.
    final String batchKey =
        createBatch(
            tenantB()
                .newCreateBatchOperationCommand()
                .migrateProcessInstance()
                .migrationPlan(
                    MigrationPlan.newBuilder()
                        .withTargetProcessDefinitionKey(tenantBTargetKey)
                        .addMappingInstruction("taskA", "taskB")
                        .build())
                .filter(f -> f.processDefinitionId(sourceProcessId)));

    // then
    assertBatchCoversOneTenantsItems(batchKey);
    Awaitility.await("tenantb's instances now run the target definition")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(instanceCount(tenantB(), targetProcessId)).isEqualTo(ITEMS_PER_TENANT));
    assertBystandersUntouched(
        "instances still on the source definition",
        admin -> instanceCount(admin, sourceProcessId),
        ITEMS_PER_TENANT);
    assertBystandersUntouched(
        "instances migrated onto the target definition",
        admin -> instanceCount(admin, targetProcessId),
        0);
  }

  @Test
  void shouldScopeModifyProcessInstanceBatchToCreatingTenant() {
    // given — instances waiting on taskA in all three tenants
    final String processId = Strings.newRandomValidBpmnId();
    forEachTenant(admin -> deploy(admin, twoUserTaskProcess(processId), processId));
    forEachTenant(admin -> startInstances(admin, processId));
    forEachTenant(admin -> awaitActiveElementCount(admin, processId, "taskA", ITEMS_PER_TENANT));

    // when — tenantb moves the token from taskA to taskB
    final String batchKey =
        createBatch(
            tenantB()
                .newCreateBatchOperationCommand()
                .modifyProcessInstance()
                .addMoveInstruction("taskA", "taskB")
                .filter(f -> f.processDefinitionId(processId)));

    // then
    assertBatchCoversOneTenantsItems(batchKey);
    Awaitility.await("tenantb's instances moved to taskB")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(activeElementCount(tenantB(), processId, "taskB"))
                    .isEqualTo(ITEMS_PER_TENANT));
    assertBystandersUntouched(
        "instances still waiting on taskA",
        admin -> activeElementCount(admin, processId, "taskA"),
        ITEMS_PER_TENANT);
    assertBystandersUntouched(
        "instances moved to taskB", admin -> activeElementCount(admin, processId, "taskB"), 0);
  }

  @Test
  void shouldScopeUpdateJobBatchToCreatingTenant() {
    // given — JobItemProvider. The same job type is waiting in all three tenants.
    final String processId = Strings.newRandomValidBpmnId();
    final String jobType = "job-" + processId;
    forEachTenant(admin -> deploy(admin, serviceTaskProcess(processId, jobType), processId));
    forEachTenant(admin -> startInstances(admin, processId));
    forEachTenant(admin -> awaitJobCount(admin, processId, null, ITEMS_PER_TENANT));

    // when — tenantb raises the priority. 99 is not a value any of these jobs can already hold, so
    // a leak shows up as a bystander job carrying it.
    final String batchKey =
        createBatch(
            tenantB()
                .newCreateBatchOperationCommand()
                .updateJob()
                .priority(99)
                .filter(f -> f.processDefinitionId(processId)));

    // then
    assertBatchCoversOneTenantsItems(batchKey);
    Awaitility.await("tenantb's jobs carry the new priority")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> assertThat(jobCount(tenantB(), processId, 99)).isEqualTo(ITEMS_PER_TENANT));
    assertBystandersUntouched(
        "jobs with the batch's priority", admin -> jobCount(admin, processId, 99), 0);
  }

  @Test
  void shouldScopeResolveIncidentBatchToCreatingTenant() {
    // given — IncidentItemProvider. Failing the jobs with no retries left raises one incident per
    // instance in each tenant.
    final String processId = Strings.newRandomValidBpmnId();
    final String jobType = "job-" + processId;
    forEachTenant(admin -> deploy(admin, serviceTaskProcess(processId, jobType), processId));
    forEachTenant(admin -> startInstances(admin, processId));
    forEachTenant(
        admin ->
            activateAndFailJobs(admin, jobType, "pt-scoping-worker", ITEMS_PER_TENANT, "boom"));
    forEachTenant(admin -> awaitActiveIncidentCount(admin, processId, ITEMS_PER_TENANT));

    // when
    final String batchKey =
        createBatch(
            tenantB()
                .newCreateBatchOperationCommand()
                .resolveIncident()
                .filter(f -> f.processDefinitionId(processId).hasIncident(true)));

    // then — the executor sets the job's retries to 1 before resolving, and no worker is running to
    // fail it again, so a resolved incident stays resolved.
    assertBatchCoversOneTenantsItems(batchKey);
    Awaitility.await("tenantb's incidents are resolved")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(activeIncidentCount(tenantB(), processId)).isZero());
    assertBystandersUntouched(
        "active incidents", admin -> activeIncidentCount(admin, processId), ITEMS_PER_TENANT);
  }

  @Test
  void shouldScopeDeleteDecisionInstanceBatchToCreatingTenant() {
    // given — DecisionInstanceItemProvider. This is the only provider whose filter never mentions a
    // process, so it is the one a process-scoped fix would miss.
    final String decisionId = Strings.newRandomValidBpmnId();
    forEachTenant(admin -> evaluateTwice(admin, decisionId));
    forEachTenant(admin -> awaitDecisionInstanceCount(admin, decisionId, ITEMS_PER_TENANT));

    // when
    final String batchKey =
        createBatch(
            tenantB()
                .newCreateBatchOperationCommand()
                .deleteDecisionInstance()
                .filter(f -> f.decisionDefinitionId(decisionId)));

    // then
    assertBatchCoversOneTenantsItems(batchKey);
    Awaitility.await("tenantb's decision instances are deleted")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(decisionInstanceCount(tenantB(), decisionId)).isZero());
    assertBystandersUntouched(
        "decision instances", admin -> decisionInstanceCount(admin, decisionId), ITEMS_PER_TENANT);
  }

  // --- tenants -------------------------------------------------------------------------

  private static CamundaClient tenantA() {
    return ptClients.admin(TENANT_A);
  }

  private static CamundaClient tenantB() {
    return ptClients.admin(TENANT_B);
  }

  /** The tenants that must stay untouched: everyone but the one that creates the batch. */
  private static List<Bystander> bystanders() {
    return List.of(new Bystander("default", client), new Bystander(TENANT_A, tenantA()));
  }

  private static void forEachTenant(final java.util.function.Consumer<CamundaClient> action) {
    action.accept(client);
    action.accept(tenantA());
    action.accept(tenantB());
  }

  private record Bystander(String label, CamundaClient admin) {}

  // --- shared assertions ---------------------------------------------------------------

  /**
   * The item provider resolved the filter against one tenant's storage. A provider reading shared
   * storage would report every tenant's items instead.
   */
  private static void assertBatchCoversOneTenantsItems(final String batchKey) {
    waitForBatchOperationWithCorrectTotalCount(tenantB(), batchKey, ITEMS_PER_TENANT);
    waitForBatchOperationCompleted(tenantB(), batchKey, ITEMS_PER_TENANT, 0);
  }

  /**
   * Holds the bystanders' counts for a period <em>after</em> the batch has reported completion, so
   * a leak that arrives as a late follow-up command still fails the test.
   */
  private static void assertBystandersUntouched(
      final String what, final ToLongFunction<CamundaClient> count, final long expected) {
    Awaitility.await("bystander tenants' " + what + " stay untouched by tenantb's batch")
        .during(UNTOUCHED_HOLD)
        .atMost(PROPAGATION_TIMEOUT)
        .untilAsserted(
            () ->
                bystanders()
                    .forEach(
                        bystander ->
                            assertThat(count.applyAsLong(bystander.admin()))
                                .as("%s in tenant '%s'", what, bystander.label())
                                .isEqualTo(expected)));
  }

  // --- process models ------------------------------------------------------------------

  private static BpmnModelInstance completingProcess(final String processId) {
    return Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
  }

  private static BpmnModelInstance serviceTaskProcess(
      final String processId, final String jobType) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .serviceTask("task", t -> t.zeebeJobType(jobType))
        .endEvent()
        .done();
  }

  private static BpmnModelInstance userTaskProcess(
      final String processId, final String userTaskId) {
    return Bpmn.createExecutableProcess(processId).startEvent().userTask(userTaskId).done();
  }

  private static BpmnModelInstance twoUserTaskProcess(final String processId) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .userTask("taskA")
        .userTask("taskB")
        .endEvent()
        .done();
  }

  // --- setup helpers -------------------------------------------------------------------

  private static long deploy(
      final CamundaClient admin, final BpmnModelInstance model, final String processId) {
    return admin
        .newDeployResourceCommand()
        .addProcessModel(model, processId + ".bpmn")
        .send()
        .join()
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private static void startInstances(final CamundaClient admin, final String processId) {
    for (int i = 0; i < ITEMS_PER_TENANT; i++) {
      admin.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
    }
  }

  private static void evaluateTwice(final CamundaClient admin, final String decisionId) {
    final var dmnModel = getDmnModelInstance(decisionId);
    final Decision decision = deployDmnModel(admin, dmnModel, decisionId + ".dmn");
    for (int i = 0; i < ITEMS_PER_TENANT; i++) {
      evaluateDecision(admin, decision.getDecisionKey(), "{}");
    }
  }

  private static String createBatch(
      final io.camunda.client.api.command.CreateBatchOperationCommandStep1
                  .CreateBatchOperationCommandStep3<
              ?>
          command) {
    return command.send().join().getBatchOperationKey();
  }

  // --- counts --------------------------------------------------------------------------

  private static long instanceCount(final CamundaClient admin, final String processId) {
    return admin
        .newProcessInstanceSearchRequest()
        .filter(f -> f.processDefinitionId(processId))
        .send()
        .join()
        .items()
        .size();
  }

  private static long activeElementCount(
      final CamundaClient admin, final String processId, final String elementId) {
    return admin
        .newElementInstanceSearchRequest()
        .filter(
            f ->
                f.processDefinitionId(processId)
                    .elementId(elementId)
                    .state(ElementInstanceState.ACTIVE))
        .send()
        .join()
        .items()
        .size();
  }

  private static long jobCount(
      final CamundaClient admin, final String processId, final Integer priority) {
    return admin
        .newJobSearchRequest()
        .filter(
            f -> {
              f.processDefinitionId(processId);
              if (priority != null) {
                f.priority(priority);
              }
            })
        .send()
        .join()
        .items()
        .size();
  }

  private static long activeIncidentCount(final CamundaClient admin, final String processId) {
    return admin
        .newIncidentSearchRequest()
        .filter(f -> f.processDefinitionId(processId).state(IncidentState.ACTIVE))
        .send()
        .join()
        .items()
        .size();
  }

  private static long decisionInstanceCount(final CamundaClient admin, final String decisionId) {
    return admin
        .newDecisionInstanceSearchRequest()
        .filter(f -> f.decisionDefinitionId(decisionId))
        .send()
        .join()
        .items()
        .size();
  }

  // --- awaits --------------------------------------------------------------------------

  private static void awaitInstanceCount(
      final CamundaClient admin, final String processId, final int expected) {
    awaitCount(
        "process instances for '" + processId + "'",
        () -> instanceCount(admin, processId),
        expected);
  }

  private static void awaitActiveElementCount(
      final CamundaClient admin,
      final String processId,
      final String elementId,
      final int expected) {
    awaitCount(
        "active '" + elementId + "' elements",
        () -> activeElementCount(admin, processId, elementId),
        expected);
  }

  private static void awaitJobCount(
      final CamundaClient admin,
      final String processId,
      final Integer priority,
      final int expected) {
    awaitCount(
        "jobs for '" + processId + "'", () -> jobCount(admin, processId, priority), expected);
  }

  private static void awaitActiveIncidentCount(
      final CamundaClient admin, final String processId, final int expected) {
    awaitCount(
        "active incidents for '" + processId + "'",
        () -> activeIncidentCount(admin, processId),
        expected);
  }

  private static void awaitDecisionInstanceCount(
      final CamundaClient admin, final String decisionId, final int expected) {
    awaitCount(
        "decision instances for '" + decisionId + "'",
        () -> decisionInstanceCount(admin, decisionId),
        expected);
  }

  private static void awaitCount(
      final String what, final java.util.function.LongSupplier count, final int expected) {
    Awaitility.await(what + " are searchable")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(count.getAsLong()).isEqualTo(expected));
  }
}
