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
import io.camunda.zeebe.model.bpmn.Bpmn;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.AfterClass;
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

  public CommandDistributionIdempotencyTest(
      final String testName, final Scenario scenario, final Class<?> processor) {
    this.scenario = scenario;
    this.processor = processor;
  }

  @AfterClass
  public static void afterClass() {
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
                  ENGINE
                      .authorization()
                      .newAuthorization()
                      .withOwnerKey(user.getKey())
                      .withOwnerId(user.getValue().getUsername())
                      .withResourceId("*")
                      .withResourceType(AuthorizationResourceType.USER)
                      .withPermissions(PermissionType.READ)
                      .create();
                },
                2),
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
                          .withOwnerKey(user.getKey())
                          .withOwnerId(user.getValue().getUsername())
                          .withResourceId("*")
                          .withResourceType(AuthorizationResourceType.USER)
                          .withPermissions(PermissionType.READ)
                          .create()
                          .getValue()
                          .getAuthorizationKey();

                  ENGINE.authorization().deleteAuthorization(key).delete();
                },
                3),
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
                          .withOwnerKey(user.getKey())
                          .withOwnerId(user.getValue().getUsername())
                          .withResourceId("*")
                          .withResourceType(AuthorizationResourceType.USER)
                          .withPermissions(PermissionType.READ)
                          .create()
                          .getValue()
                          .getAuthorizationKey();

                  ENGINE.authorization().updateAuthorization(key).update();
                },
                3),
            AuthorizationUpdateProcessor.class
          },
          {
            "Clock.RESET is idempotent",
            new Scenario(ValueType.CLOCK, ClockIntent.RESET, () -> ENGINE.clock().reset(), 1),
            ClockProcessor.class
          },
          {
            "Deployment.CREATE is idempotent",
            new Scenario(
                ValueType.DEPLOYMENT,
                DeploymentIntent.CREATE,
                CommandDistributionIdempotencyTest::deployProcess,
                1),
            DeploymentCreateProcessor.class
          },
          {
            "Group.CREATE is idempotent",
            new Scenario(
                ValueType.GROUP,
                GroupIntent.CREATE,
                CommandDistributionIdempotencyTest::createGroup,
                1),
            GroupCreateProcessor.class
          },
          {
            "Group.DELETE is idempotent",
            new Scenario(
                ValueType.GROUP,
                GroupIntent.DELETE,
                () -> {
                  final var group = createGroup();
                  ENGINE.group().deleteGroup(group.getKey()).delete();
                },
                2),
            GroupDeleteProcessor.class
          },
          {
            "Group.UPDATE is idempotent",
            new Scenario(
                ValueType.GROUP,
                GroupIntent.UPDATE,
                () -> {
                  final var group = createGroup();
                  ENGINE
                      .group()
                      .updateGroup(group.getKey())
                      .withName(UUID.randomUUID().toString())
                      .update();
                },
                2),
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
                  ENGINE
                      .group()
                      .addEntity(group.getKey())
                      .withEntityKey(user.getKey())
                      .withEntityType(EntityType.USER)
                      .add();
                },
                3),
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
                  ENGINE
                      .group()
                      .removeEntity(group.getKey())
                      .withEntityKey(user.getKey())
                      .withEntityType(EntityType.USER)
                      .remove();
                },
                4),
            GroupRemoveEntityProcessor.class
          },
          {
            "Mapping.CREATE is idempotent",
            new Scenario(
                ValueType.MAPPING,
                MappingIntent.CREATE,
                CommandDistributionIdempotencyTest::createMapping,
                1),
            MappingCreateProcessor.class
          },
          {
            "Mapping.DELETE is idempotent",
            new Scenario(
                ValueType.MAPPING,
                MappingIntent.DELETE,
                () -> {
                  final var mapping = createMapping();
                  ENGINE.mapping().deleteMapping(mapping.getKey()).delete();
                },
                2),
            MappingDeleteProcessor.class
          },
          {
            "ResourceDeletion.DELETE is idempotent",
            new Scenario(
                ValueType.RESOURCE_DELETION,
                ResourceDeletionIntent.DELETE,
                () -> {
                  final var process = deployProcess();
                  ENGINE
                      .resourceDeletion()
                      .withResourceKey(
                          process
                              .getValue()
                              .getProcessesMetadata()
                              .getFirst()
                              .getProcessDefinitionKey())
                      .delete();
                },
                2),
            ResourceDeletionDeleteProcessor.class
          },
          {
            "Role.CREATE is idempotent",
            new Scenario(
                ValueType.ROLE,
                RoleIntent.CREATE,
                CommandDistributionIdempotencyTest::createRole,
                1),
            RoleCreateProcessor.class
          },
          {
            "Role.DELETE is idempotent",
            new Scenario(
                ValueType.ROLE,
                RoleIntent.DELETE,
                () -> {
                  final var group = createRole();
                  ENGINE.role().deleteRole(group.getKey()).delete();
                },
                2),
            RoleDeleteProcessor.class
          },
          {
            "Role.UPDATE is idempotent",
            new Scenario(
                ValueType.ROLE,
                RoleIntent.UPDATE,
                () -> {
                  final var role = createRole();
                  ENGINE
                      .role()
                      .updateRole(role.getKey())
                      .withName(UUID.randomUUID().toString())
                      .update();
                },
                2),
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
                  ENGINE
                      .role()
                      .addEntity(role.getKey())
                      .withEntityKey(user.getKey())
                      .withEntityType(EntityType.USER)
                      .add();
                },
                3),
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
                  ENGINE
                      .role()
                      .removeEntity(role.getKey())
                      .withEntityKey(user.getKey())
                      .withEntityType(EntityType.USER)
                      .remove();
                },
                4),
            RoleRemoveEntityProcessor.class
          },
          {
            "Signal.BROADCAST is idempotent",
            new Scenario(
                ValueType.SIGNAL,
                SignalIntent.BROADCAST,
                () -> ENGINE.signal().withSignalName(UUID.randomUUID().toString()).broadcast(),
                1),
            SignalBroadcastProcessor.class
          },
          {
            "Tenant.CREATE is idempotent",
            new Scenario(
                ValueType.TENANT,
                TenantIntent.CREATE,
                CommandDistributionIdempotencyTest::createTenant,
                1),
            TenantCreateProcessor.class
          },
          {
            "Tenant.DELETE is idempotent",
            new Scenario(
                ValueType.TENANT,
                TenantIntent.DELETE,
                () -> {
                  final var tenant = createTenant();
                  ENGINE.tenant().deleteTenant(tenant.getValue().getTenantId()).delete();
                },
                2),
            TenantDeleteProcessor.class
          },
          {
            "Tenant.UPDATE is idempotent",
            new Scenario(
                ValueType.TENANT,
                TenantIntent.UPDATE,
                () -> {
                  final var tenant = createTenant();
                  ENGINE
                      .tenant()
                      .updateTenant(tenant.getValue().getTenantId())
                      .withName(UUID.randomUUID().toString())
                      .update();
                },
                2),
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
                  ENGINE
                      .tenant()
                      .addEntity(tenant.getValue().getTenantId())
                      .withEntityId(user.getValue().getUsername())
                      .withEntityType(EntityType.USER)
                      .add();
                },
                3),
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
                  ENGINE
                      .tenant()
                      .removeEntity(tenant.getKey())
                      .withEntityKey(user.getKey())
                      .withEntityType(EntityType.USER)
                      .remove();
                },
                4),
            TenantRemoveEntityProcessor.class
          },
          {
            "User.CREATE is idempotent",
            new Scenario(
                ValueType.USER,
                UserIntent.CREATE,
                CommandDistributionIdempotencyTest::createUser,
                1),
            UserCreateProcessor.class
          },
          {
            "User.DELETE is idempotent",
            new Scenario(
                ValueType.USER,
                UserIntent.DELETE,
                () -> {
                  final var user = createUser();
                  ENGINE.user().deleteUser(user.getValue().getUsername()).delete();
                },
                2),
            UserDeleteProcessor.class,
          },
          {
            "User.UPDATE is idempotent",
            new Scenario(
                ValueType.USER,
                UserIntent.UPDATE,
                () -> {
                  final var user = createUser();
                  ENGINE
                      .user()
                      .updateUser(user.getKey())
                      .withUsername(user.getValue().getUsername())
                      .withName(UUID.randomUUID().toString())
                      .update();
                },
                2),
            UserUpdateProcessor.class
          },
          {
            "MessageSubscription.MIGRATE is idempotent",
            new Scenario(
                ValueType.MESSAGE_SUBSCRIPTION,
                MessageSubscriptionIntent.MIGRATE,
                CommandDistributionIdempotencyTest::migrateMessageSubscription,
                2),
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
                                .setClaimName("claimName")
                                .setClaimValue("claimValue"))
                        .initialize(),
                1),
            IdentitySetupInitializeProcessor.class
          }
        });
  }

  @Test
  public void test() {
    DISTRIBUTING_PROCESSORS.remove(processor);
    // given we intercept the first acknowledgement
    interceptAcknowledgement(scenario);

    // when distribution is started
    scenario.commandSender.sendCommand();
    RecordingExporter.commandDistributionRecords(CommandDistributionIntent.STARTED)
        .withDistributionIntent(scenario.intent())
        .withDistributionValueType(scenario.valueType())
        .await();

    // wait until the 2nd partition received the command twice
    RecordingExporter.setMaximumWaitTime(100);
    Awaitility.await()
        .untilAsserted(
            () -> {
              ENGINE.getClock().addTime(CommandRedistributor.COMMAND_REDISTRIBUTION_INTERVAL);
              assertThat(
                      RecordingExporter.records()
                          .withPartitionId(2)
                          .withValueType(scenario.valueType())
                          .withIntent(scenario.intent())
                          .limit(2))
                  .hasSize(2);
            });
    RecordingExporter.setMaximumWaitTime(5000);

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
                .withPartitionId(1)
                .withDistributionValueType(scenario.valueType())
                .withDistributionIntent(scenario.intent())
                .exists())
        .isTrue();
  }

  private static void interceptAcknowledgement(final Scenario scenario) {
    final var hasInterceptedAlready = new AtomicBoolean(false);
    final var acknowledgementCounter = new AtomicInteger(0);
    ENGINE.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> {
          if (intent != CommandDistributionIntent.ACKNOWLEDGE) {
            return true;
          }

          final var currentAcknowledgementCount = acknowledgementCounter.incrementAndGet();

          if (hasInterceptedAlready.get()
              || currentAcknowledgementCount < scenario.expectedAmountOfDistributions) {
            return true;
          }

          hasInterceptedAlready.set(true);
          return false;
        });
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

  public record Scenario(
      ValueType valueType,
      Intent intent,
      CommandSender commandSender,
      int expectedAmountOfDistributions) {}

  @FunctionalInterface
  interface CommandSender {
    void sendCommand();
  }
}
