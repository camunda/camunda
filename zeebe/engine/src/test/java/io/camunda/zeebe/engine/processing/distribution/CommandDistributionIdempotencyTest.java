/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.distribution;

import static io.camunda.zeebe.engine.processing.processinstance.migration.MigrationTestUtil.extractProcessDefinitionKeyByProcessId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.camunda.zeebe.engine.processing.clock.ClockProcessor;
import io.camunda.zeebe.engine.processing.deployment.DeploymentCreateProcessor;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCreateProcessor;
import io.camunda.zeebe.engine.processing.identity.AuthorizationDeleteProcessor;
import io.camunda.zeebe.engine.processing.identity.AuthorizationUpdateProcessor;
import io.camunda.zeebe.engine.processing.identity.GroupAddEntityProcessor;
import io.camunda.zeebe.engine.processing.identity.GroupCreateProcessor;
import io.camunda.zeebe.engine.processing.identity.GroupDeleteProcessor;
import io.camunda.zeebe.engine.processing.identity.GroupRemoveEntityProcessor;
import io.camunda.zeebe.engine.processing.identity.GroupUpdateProcessor;
import io.camunda.zeebe.engine.processing.identity.IdentitySetupInitializeProcessor;
import io.camunda.zeebe.engine.processing.identity.MappingCreateProcessor;
import io.camunda.zeebe.engine.processing.identity.MappingDeleteProcessor;
import io.camunda.zeebe.engine.processing.identity.RoleAddEntityProcessor;
import io.camunda.zeebe.engine.processing.identity.RoleCreateProcessor;
import io.camunda.zeebe.engine.processing.identity.RoleDeleteProcessor;
import io.camunda.zeebe.engine.processing.identity.RoleRemoveEntityProcessor;
import io.camunda.zeebe.engine.processing.identity.RoleUpdateProcessor;
import io.camunda.zeebe.engine.processing.message.MessageSubscriptionMigrateProcessor;
import io.camunda.zeebe.engine.processing.resource.ResourceDeletionDeleteProcessor;
import io.camunda.zeebe.engine.processing.signal.SignalBroadcastProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.tenant.TenantAddEntityProcessor;
import io.camunda.zeebe.engine.processing.tenant.TenantCreateProcessor;
import io.camunda.zeebe.engine.processing.tenant.TenantDeleteProcessor;
import io.camunda.zeebe.engine.processing.tenant.TenantRemoveEntityProcessor;
import io.camunda.zeebe.engine.processing.tenant.TenantUpdateProcessor;
import io.camunda.zeebe.engine.processing.user.UserCreateProcessor;
import io.camunda.zeebe.engine.processing.user.UserDeleteProcessor;
import io.camunda.zeebe.engine.processing.user.UserUpdateProcessor;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.TestInterPartitionCommandSender.CommandInterceptor;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CommandDistributionIdempotencyTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.multiplePartition(2);

  private static final Set<Class<?>> DISTRIBUTING_PROCESSORS =
      new HashSet<>(
          ReflectionSupport.findAllClassesInPackage(
              "io.camunda.zeebe.engine.processing",
              c -> {
                final var interfaces = c.getInterfaces();
                return Arrays.asList(interfaces).contains(DistributedTypedRecordProcessor.class);
              },
              ignored -> true));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final Scenario scenario;
  private final Class<?> processor;
  private AcknowledgementInterceptor interceptor;

  public CommandDistributionIdempotencyTest(
      final String testName, final Scenario scenario, final Class<?> processor) {
    this.scenario = scenario;
    this.processor = processor;
  }

  @After
  public void updateTestedProcessors() {
    DISTRIBUTING_PROCESSORS.remove(processor);
  }

  @AfterClass
  public static void assertAllProcessorsTested() {
    if (!DISTRIBUTING_PROCESSORS.isEmpty()) {
      fail("No test scenario found for processors: '%s'".formatted(DISTRIBUTING_PROCESSORS));
    }
  }

  @Parameters(name = "{0}")
  public static Collection<Object[]> scenarios() {
    return Arrays.asList(
        new Object[][] {
          {
            "Authorization.CREATE is idempotent",
            new Scenario(
                ValueType.AUTHORIZATION,
                AuthorizationIntent.CREATE,
                () -> {
                  final var user = createUser();
                  return ENGINE
                      .authorization()
                      .newAuthorization()
                      .withOwnerId(user.getValue().getUsername())
                      .withResourceId("*")
                      .withResourceType(AuthorizationResourceType.USER)
                      .withPermissions(PermissionType.READ)
                      .create();
                }),
            AuthorizationCreateProcessor.class
          },
          {
            "Authorization.DELETE is idempotent",
            new Scenario(
                ValueType.AUTHORIZATION,
                AuthorizationIntent.DELETE,
                () -> {
                  final var user = createUser();
                  final var key =
                      ENGINE
                          .authorization()
                          .newAuthorization()
                          .withOwnerId(user.getValue().getUsername())
                          .withResourceId("*")
                          .withResourceType(AuthorizationResourceType.USER)
                          .withPermissions(PermissionType.READ)
                          .create()
                          .getValue()
                          .getAuthorizationKey();

                  return ENGINE.authorization().deleteAuthorization(key).delete();
                }),
            AuthorizationDeleteProcessor.class
          },
          {
            "Authorization.UPDATE is idempotent",
            new Scenario(
                ValueType.AUTHORIZATION,
                AuthorizationIntent.UPDATE,
                () -> {
                  final var user = createUser();
                  final var key =
                      ENGINE
                          .authorization()
                          .newAuthorization()
                          .withOwnerId(user.getValue().getUsername())
                          .withResourceId("*")
                          .withResourceType(AuthorizationResourceType.USER)
                          .withPermissions(PermissionType.READ)
                          .create()
                          .getValue()
                          .getAuthorizationKey();

                  return ENGINE.authorization().updateAuthorization(key).update();
                }),
            AuthorizationUpdateProcessor.class
          },
          {
            "Clock.RESET is idempotent",
            new Scenario(ValueType.CLOCK, ClockIntent.RESET, () -> ENGINE.clock().reset()),
            ClockProcessor.class
          },
          {
            "Deployment.CREATE is idempotent",
            new Scenario(
                ValueType.DEPLOYMENT,
                DeploymentIntent.CREATE,
                CommandDistributionIdempotencyTest::deployProcess),
            DeploymentCreateProcessor.class
          },
          {
            "Group.CREATE is idempotent",
            new Scenario(
                ValueType.GROUP,
                GroupIntent.CREATE,
                CommandDistributionIdempotencyTest::createGroup),
            GroupCreateProcessor.class
          },
          {
            "Group.DELETE is idempotent",
            new Scenario(
                ValueType.GROUP,
                GroupIntent.DELETE,
                () -> {
                  final var group = createGroup();
                  return ENGINE.group().deleteGroup(group.getKey()).delete();
                }),
            GroupDeleteProcessor.class
          },
          {
            "Group.UPDATE is idempotent",
            new Scenario(
                ValueType.GROUP,
                GroupIntent.UPDATE,
                () -> {
                  final var group = createGroup();
                  return ENGINE
                      .group()
                      .updateGroup(group.getKey())
                      .withName(UUID.randomUUID().toString())
                      .update();
                }),
            GroupUpdateProcessor.class
          },
          {
            "Group.ADD_ENTITY is idempotent",
            new Scenario(
                ValueType.GROUP,
                GroupIntent.ADD_ENTITY,
                () -> {
                  final var group = createGroup();
                  final var user = createUser();
                  return ENGINE
                      .group()
                      .addEntity(group.getKey())
                      .withEntityKey(user.getKey())
                      .withEntityType(EntityType.USER)
                      .add();
                }),
            GroupAddEntityProcessor.class
          },
          {
            "Group.REMOVE_ENTITY is idempotent",
            new Scenario(
                ValueType.GROUP,
                GroupIntent.REMOVE_ENTITY,
                () -> {
                  final var group = createGroup();
                  final var user = createUser();
                  ENGINE
                      .group()
                      .addEntity(group.getKey())
                      .withEntityKey(user.getKey())
                      .withEntityType(EntityType.USER)
                      .add();
                  return ENGINE
                      .group()
                      .removeEntity(group.getKey())
                      .withEntityKey(user.getKey())
                      .withEntityType(EntityType.USER)
                      .remove();
                }),
            GroupRemoveEntityProcessor.class
          },
          {
            "Mapping.CREATE is idempotent",
            new Scenario(
                ValueType.MAPPING,
                MappingIntent.CREATE,
                CommandDistributionIdempotencyTest::createMapping),
            MappingCreateProcessor.class
          },
          {
            "Mapping.DELETE is idempotent",
            new Scenario(
                ValueType.MAPPING,
                MappingIntent.DELETE,
                () -> {
                  final var mapping = createMapping();
                  return ENGINE.mapping().deleteMapping(mapping.getKey()).delete();
                }),
            MappingDeleteProcessor.class
          },
          {
            "ResourceDeletion.DELETE is idempotent",
            new Scenario(
                ValueType.RESOURCE_DELETION,
                ResourceDeletionIntent.DELETE,
                () -> {
                  final var process = deployProcess();
                  return ENGINE
                      .resourceDeletion()
                      .withResourceKey(
                          process
                              .getValue()
                              .getProcessesMetadata()
                              .getFirst()
                              .getProcessDefinitionKey())
                      .delete();
                }),
            ResourceDeletionDeleteProcessor.class
          },
          {
            "Role.CREATE is idempotent",
            new Scenario(
                ValueType.ROLE, RoleIntent.CREATE, CommandDistributionIdempotencyTest::createRole),
            RoleCreateProcessor.class
          },
          {
            "Role.DELETE is idempotent",
            new Scenario(
                ValueType.ROLE,
                RoleIntent.DELETE,
                () -> {
                  final var group = createRole();
                  return ENGINE.role().deleteRole(group.getKey()).delete();
                }),
            RoleDeleteProcessor.class
          },
          {
            "Role.UPDATE is idempotent",
            new Scenario(
                ValueType.ROLE,
                RoleIntent.UPDATE,
                () -> {
                  final var role = createRole();
                  return ENGINE
                      .role()
                      .updateRole(role.getKey())
                      .withName(UUID.randomUUID().toString())
                      .update();
                }),
            RoleUpdateProcessor.class
          },
          {
            "Role.ADD_ENTITY is idempotent",
            new Scenario(
                ValueType.ROLE,
                RoleIntent.ADD_ENTITY,
                () -> {
                  final var role = createRole();
                  final var user = createUser();
                  return ENGINE
                      .role()
                      .addEntity(role.getKey())
                      .withEntityKey(user.getKey())
                      .withEntityType(EntityType.USER)
                      .add();
                }),
            RoleAddEntityProcessor.class
          },
          {
            "Role.REMOVE_ENTITY is idempotent",
            new Scenario(
                ValueType.ROLE,
                RoleIntent.REMOVE_ENTITY,
                () -> {
                  final var role = createRole();
                  final var user = createUser();
                  ENGINE
                      .role()
                      .addEntity(role.getKey())
                      .withEntityKey(user.getKey())
                      .withEntityType(EntityType.USER)
                      .add();
                  return ENGINE
                      .role()
                      .removeEntity(role.getKey())
                      .withEntityKey(user.getKey())
                      .withEntityType(EntityType.USER)
                      .remove();
                }),
            RoleRemoveEntityProcessor.class
          },
          {
            "Signal.BROADCAST is idempotent",
            new Scenario(
                ValueType.SIGNAL,
                SignalIntent.BROADCAST,
                () -> ENGINE.signal().withSignalName(UUID.randomUUID().toString()).broadcast()),
            SignalBroadcastProcessor.class
          },
          {
            "Tenant.CREATE is idempotent",
            new Scenario(
                ValueType.TENANT,
                TenantIntent.CREATE,
                CommandDistributionIdempotencyTest::createTenant),
            TenantCreateProcessor.class
          },
          {
            "Tenant.DELETE is idempotent",
            new Scenario(
                ValueType.TENANT,
                TenantIntent.DELETE,
                () -> {
                  final var tenant = createTenant();
                  return ENGINE.tenant().deleteTenant(tenant.getValue().getTenantId()).delete();
                }),
            TenantDeleteProcessor.class
          },
          {
            "Tenant.UPDATE is idempotent",
            new Scenario(
                ValueType.TENANT,
                TenantIntent.UPDATE,
                () -> {
                  final var tenant = createTenant();
                  return ENGINE
                      .tenant()
                      .updateTenant(tenant.getValue().getTenantId())
                      .withName(UUID.randomUUID().toString())
                      .update();
                }),
            TenantUpdateProcessor.class
          },
          {
            "Tenant.ADD_ENTITY is idempotent",
            new Scenario(
                ValueType.TENANT,
                TenantIntent.ADD_ENTITY,
                () -> {
                  final var tenant = createTenant();
                  final var user = createUser();
                  return ENGINE
                      .tenant()
                      .addEntity(tenant.getValue().getTenantId())
                      .withEntityId(user.getValue().getUsername())
                      .withEntityType(EntityType.USER)
                      .add();
                }),
            TenantAddEntityProcessor.class
          },
          {
            "Tenant.REMOVE_ENTITY is idempotent",
            new Scenario(
                ValueType.TENANT,
                TenantIntent.REMOVE_ENTITY,
                () -> {
                  final var tenant = createTenant();
                  final var user = createUser();
                  ENGINE
                      .tenant()
                      .addEntity(tenant.getValue().getTenantId())
                      .withEntityId(user.getValue().getUsername())
                      .withEntityType(EntityType.USER)
                      .add();
                  return ENGINE
                      .tenant()
                      .removeEntity(tenant.getValue().getTenantId())
                      .withEntityId(user.getValue().getUsername())
                      .withEntityType(EntityType.USER)
                      .remove();
                }),
            TenantRemoveEntityProcessor.class
          },
          {
            "User.CREATE is idempotent",
            new Scenario(
                ValueType.USER, UserIntent.CREATE, CommandDistributionIdempotencyTest::createUser),
            UserCreateProcessor.class
          },
          {
            "User.DELETE is idempotent",
            new Scenario(
                ValueType.USER,
                UserIntent.DELETE,
                () -> {
                  final var user = createUser();
                  return ENGINE.user().deleteUser(user.getValue().getUsername()).delete();
                }),
            UserDeleteProcessor.class,
          },
          {
            "User.UPDATE is idempotent",
            new Scenario(
                ValueType.USER,
                UserIntent.UPDATE,
                () -> {
                  final var user = createUser();
                  return ENGINE
                      .user()
                      .updateUser()
                      .withUsername(user.getValue().getUsername())
                      .withName(UUID.randomUUID().toString())
                      .update();
                }),
            UserUpdateProcessor.class
          },
          {
            "MessageSubscription.MIGRATE is idempotent",
            new Scenario(
                ValueType.MESSAGE_SUBSCRIPTION,
                MessageSubscriptionIntent.MIGRATE,
                CommandDistributionIdempotencyTest::migrateMessageSubscription),
            MessageSubscriptionMigrateProcessor.class
          },
          {
            "IdentitySetup.INITIALIZE is idempotent",
            new Scenario(
                ValueType.IDENTITY_SETUP,
                IdentitySetupIntent.INITIALIZE,
                () ->
                    ENGINE
                        .identitySetup()
                        .initialize()
                        .withRole(new RoleRecord().setRoleKey(1L).setName("role"))
                        .withUser(
                            new UserRecord()
                                .setUserKey(2L)
                                .setUsername("user")
                                .setEmail("email")
                                .setPassword("password")
                                .setName("name"))
                        .withTenant(
                            new TenantRecord()
                                .setTenantKey(3L)
                                .setTenantId("tenant-id")
                                .setName("tenant-name"))
                        .withMapping(
                            new MappingRecord()
                                .setMappingKey(4)
                                .setId("id")
                                .setClaimName("claimName")
                                .setClaimValue("claimValue"))
                        .initialize()),
            IdentitySetupInitializeProcessor.class
          }
        });
  }

  @Before
  public void setup() {
    interceptor = AcknowledgementInterceptor.create(scenario);
  }

  @After
  public void tearDown() {
    interceptor.close();
    interceptor = null;
  }

  @Test
  public void test() {
    // when we trigger the scenario and initiate command distribution
    final var event = scenario.commandSender.sendCommand();
    final var distributionCommand =
        RecordingExporter.commandDistributionRecords(CommandDistributionIntent.STARTED)
            .withSourceRecordPosition(event.getSourceRecordPosition())
            .filter(a -> scenario.matches(a.getValue()))
            .getFirst();

    // given we intercept the first acknowledgement of the command distribution
    interceptor.enable(distributionCommand);

    // then we expect the command will written to the target partition twice (retry)
    RecordingExporter.setMaximumWaitTime(100);
    Awaitility.await()
        .untilAsserted(
            () -> {
              // wait for retry mechanism to trigger second distribution
              // (while we intercepted the first acknowledgement)
              ENGINE.getClock().addTime(CommandRedistributor.COMMAND_REDISTRIBUTION_INTERVAL);

              // Make sure we have two records on the target partition
              assertThat(
                      RecordingExporter.records()
                          .withPartitionId(2)
                          .withValueType(distributionCommand.getValue().getValueType())
                          .withIntent(distributionCommand.getValue().getIntent())
                          .withRecordKey(distributionCommand.getKey())
                          .limit(2))
                  .hasSize(2);
            });
    RecordingExporter.setMaximumWaitTime(5000);

    // then we expect the distribution still finishes based on the second (retried) acknowledgement
    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
                .withPartitionId(1)
                .withRecordKey(distributionCommand.getKey())
                .exists())
        .isTrue();
  }

  private static Record<UserRecordValue> createUser() {
    return ENGINE
        .user()
        .newUser(UUID.randomUUID().toString())
        .withName("name")
        .withEmail("email")
        .withPassword("password")
        .create();
  }

  private static Record<GroupRecordValue> createGroup() {
    return ENGINE.group().newGroup(UUID.randomUUID().toString()).create();
  }

  private static Record<RoleRecordValue> createRole() {
    return ENGINE.role().newRole(UUID.randomUUID().toString()).create();
  }

  private static Record<TenantRecordValue> createTenant() {
    return ENGINE
        .tenant()
        .newTenant()
        .withName(UUID.randomUUID().toString())
        .withTenantId(UUID.randomUUID().toString())
        .create();
  }

  private static Record<MappingRecordValue> createMapping() {
    return ENGINE
        .mapping()
        .newMapping(UUID.randomUUID().toString())
        .withClaimValue(UUID.randomUUID().toString())
        .withId(UUID.randomUUID().toString())
        .create();
  }

  private static Record<DeploymentRecordValue> deployProcess() {
    return ENGINE
        .deployment()
        .withXmlResource(
            "process.bpmn", Bpmn.createExecutableProcess().startEvent().endEvent().done())
        .expectCreated()
        .deploy();
  }

  private static Record<ProcessInstanceMigrationRecordValue> migrateMessageSubscription() {
    // given
    final String processId = "process1";
    final String targetProcessId = "process2";
    // the process instance on partition 1 will wait for a message published on 2
    final var correlationKey = "correlationKey";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key1"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary2")
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key2"))
                    .endEvent()
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("key1", correlationKey))
            .create();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    return ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .addMappingInstruction("boundary1", "boundary2")
        .migrate();
  }

  private record Scenario(ValueType valueType, Intent intent, CommandSender commandSender) {

    public boolean matches(final CommandDistributionRecordValue record) {
      return record.getValueType() == valueType && record.getIntent() == intent;
    }
  }

  private static class AcknowledgementInterceptor {
    private final CompletableFuture<Record<CommandDistributionRecordValue>> signal =
        new CompletableFuture();
    private final AtomicInteger acknowledgeCount = new AtomicInteger(0);

    private final Scenario scenario;

    public AcknowledgementInterceptor(final Scenario scenario) {
      this.scenario = scenario;
    }

    /**
     * Creates an interceptor for the given scenario and registers it with the engine
     *
     * @param scenario to match with the intercepted commands
     * @return interceptor instance create
     */
    public static AcknowledgementInterceptor create(final Scenario scenario) {
      final var interceptor = new AcknowledgementInterceptor(scenario);
      ENGINE.interceptInterPartitionCommands(interceptor::shouldPassCommand);
      return interceptor;
    }

    /**
     * Closes the interceptor
     *
     * <p>It cancels the future it might wait for to unblock the thread and unregisters it from the
     * engine
     */
    public void close() {
      ENGINE.interceptInterPartitionCommands(CommandInterceptor.SEND_ALL);

      if (signal.isCompletedExceptionally()) {
        fail(
            "Interceptor hasn't received a signal in time, and thus couldn't do any interceptions");
      } else {
        signal.cancel(true);
      }
    }

    /**
     * Signals that the initial command distribution started and allows to identify the
     * acknowledgements we want to intercept
     *
     * @param distributionCommand
     */
    public void enable(final Record<CommandDistributionRecordValue> distributionCommand) {
      signal.complete(distributionCommand);
    }

    /**
     * This method will be called for all inter-partition commands
     *
     * <p>Attention: this also means for commands that are not related to the scenario, or we don't
     * want to swallow
     */
    private boolean shouldPassCommand(
        final int targetPartitionId,
        final ValueType valueType,
        final Intent intent,
        final Long recordKey,
        final UnifiedRecordValue commandValue) {

      if (matchesScenario(intent, recordKey, commandValue)) {
        // intercept the first acknowledgement, pass on the rest
        return acknowledgeCount.incrementAndGet() > 1;
      }

      return true;
    }

    private boolean matchesScenario(
        final Intent intent, final Long recordKey, final UnifiedRecordValue commandValue) {
      // allow other inter-partition commands to continue without waiting for the signal
      if (intent != CommandDistributionIntent.ACKNOWLEDGE) {
        return false;
      }

      // allow other distribution commands to continue early and not wait for the signal
      if (!scenario.matches((CommandDistributionRecordValue) commandValue)) {
        return false;
      }

      return getScenarioKey().map(recordKey::equals).orElse(false);
    }

    private Optional<Long> getScenarioKey() {
      try {
        return Optional.of(signal.get(5000, TimeUnit.MILLISECONDS).getKey());
      } catch (final InterruptedException
          | ExecutionException
          | TimeoutException
          | CancellationException e) {
        // complete the future, let the commands pass, but make sure to fail the test
        if (!signal.isDone()) {
          signal.completeExceptionally(e);
        }
        return Optional.empty();
      }
    }
  }

  @FunctionalInterface
  interface CommandSender {
    Record sendCommand();
  }
}
