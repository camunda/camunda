/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.system.configuration.engine.GlobalListenerCfg;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
public class ClusterGlobalListenersTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.global-listeners.user-task.0.type=my-type",
        "camunda.cluster.global-listeners.user-task.0.event-types.0=creating",
        "camunda.cluster.global-listeners.user-task.0.event-types.1=assigning",
        "camunda.cluster.global-listeners.user-task.0.retries=5",
        "camunda.cluster.global-listeners.user-task.0.after-non-global=true"
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetTaskListenerType() {
      final List<GlobalListenerCfg> taskListeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask();
      assertThat(taskListeners).hasSize(1);
      assertThat(taskListeners.getFirst().getType()).isEqualTo("my-type");
    }

    @Test
    void shouldSetTaskListenerEventTypes() {
      final List<GlobalListenerCfg> taskListeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask();
      assertThat(taskListeners).hasSize(1);
      assertThat(taskListeners.getFirst().getEventTypes()).containsExactly("creating", "assigning");
    }

    @Test
    void shouldSetTaskListenerRetries() {
      final List<GlobalListenerCfg> taskListeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask();
      assertThat(taskListeners).hasSize(1);
      assertThat(taskListeners.getFirst().getRetries()).isEqualTo("5");
    }

    @Test
    void shouldSetTaskListenerAfterNonGlobalFlag() {
      final List<GlobalListenerCfg> taskListeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask();
      assertThat(taskListeners).hasSize(1);
      assertThat(taskListeners.getFirst().isAfterNonGlobal()).isEqualTo(true);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.experimental.engine.globalListeners.userTask.0.type=my-type",
        "zeebe.broker.experimental.engine.globalListeners.userTask.0.eventTypes.0=creating",
        "zeebe.broker.experimental.engine.globalListeners.userTask.0.eventTypes.1=assigning",
        "zeebe.broker.experimental.engine.globalListeners.userTask.0.retries=5",
        "zeebe.broker.experimental.engine.globalListeners.userTask.0.afterNonGlobal=true"
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldIgnoreLegacyConfiguration() {
      final List<GlobalListenerCfg> taskListeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask();
      assertThat(taskListeners).hasSize(0);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.global-listeners.user-task.0.type=my-type",
        "camunda.cluster.global-listeners.user-task.0.event-types.0=creating",
        "camunda.cluster.global-listeners.user-task.0.event-types.1=assigning",
        "camunda.cluster.global-listeners.user-task.0.retries=5",
        "camunda.cluster.global-listeners.user-task.0.after-non-global=true",
        "zeebe.broker.experimental.engine.globalListeners.userTask.0.type=my-other-type",
        "zeebe.broker.experimental.engine.globalListeners.userTask.0.eventTypes.0=updating",
        "zeebe.broker.experimental.engine.globalListeners.userTask.0.retries=4",
        "zeebe.broker.experimental.engine.globalListeners.userTask.0.afterNonGlobal=false"
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetTaskListenerTypeFromNew() {
      final List<GlobalListenerCfg> taskListeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask();
      assertThat(taskListeners).hasSize(1);
      assertThat(taskListeners.getFirst().getType()).isEqualTo("my-type");
    }

    @Test
    void shouldSetTaskListenerEventTypesFromNew() {
      final List<GlobalListenerCfg> taskListeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask();
      assertThat(taskListeners).hasSize(1);
      assertThat(taskListeners.getFirst().getEventTypes()).containsExactly("creating", "assigning");
    }

    @Test
    void shouldSetTaskListenerRetriesFromNew() {
      final List<GlobalListenerCfg> taskListeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask();
      assertThat(taskListeners).hasSize(1);
      assertThat(taskListeners.getFirst().getRetries()).isEqualTo("5");
    }

    @Test
    void shouldSetTaskListenerAfterNonGlobalFlagFromNew() {
      final List<GlobalListenerCfg> taskListeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask();
      assertThat(taskListeners).hasSize(1);
      assertThat(taskListeners.getFirst().isAfterNonGlobal()).isEqualTo(true);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.global-listeners.execution.0.type=audit-event",
        "camunda.cluster.global-listeners.execution.0.event-types.0=start",
        "camunda.cluster.global-listeners.execution.0.event-types.1=end",
        "camunda.cluster.global-listeners.execution.0.retries=5",
        "camunda.cluster.global-listeners.execution.0.after-non-global=true",
        "camunda.cluster.global-listeners.execution.0.priority=100",
        "camunda.cluster.global-listeners.execution.0.element-types.0=process",
        "camunda.cluster.global-listeners.execution.0.element-types.1=serviceTask",
        "camunda.cluster.global-listeners.execution.0.categories.0=gateways"
      })
  class WithExecutionListenerConfig {
    final BrokerBasedProperties brokerCfg;

    WithExecutionListenerConfig(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetExecutionListenerType() {
      final List<GlobalListenerCfg> listeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getExecution();
      assertThat(listeners).hasSize(1);
      assertThat(listeners.getFirst().getType()).isEqualTo("audit-event");
    }

    @Test
    void shouldSetExecutionListenerEventTypes() {
      final List<GlobalListenerCfg> listeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getExecution();
      assertThat(listeners).hasSize(1);
      assertThat(listeners.getFirst().getEventTypes()).containsExactly("start", "end");
    }

    @Test
    void shouldSetExecutionListenerRetries() {
      final List<GlobalListenerCfg> listeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getExecution();
      assertThat(listeners).hasSize(1);
      assertThat(listeners.getFirst().getRetries()).isEqualTo("5");
    }

    @Test
    void shouldSetExecutionListenerAfterNonGlobalFlag() {
      final List<GlobalListenerCfg> listeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getExecution();
      assertThat(listeners).hasSize(1);
      assertThat(listeners.getFirst().isAfterNonGlobal()).isTrue();
    }

    @Test
    void shouldSetExecutionListenerPriority() {
      final List<GlobalListenerCfg> listeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getExecution();
      assertThat(listeners).hasSize(1);
      assertThat(listeners.getFirst().getPriority()).isEqualTo(100);
    }

    @Test
    void shouldSetExecutionListenerElementTypes() {
      final List<GlobalListenerCfg> listeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getExecution();
      assertThat(listeners).hasSize(1);
      assertThat(listeners.getFirst().getElementTypes()).containsExactly("process", "serviceTask");
    }

    @Test
    void shouldSetExecutionListenerCategories() {
      final List<GlobalListenerCfg> listeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getExecution();
      assertThat(listeners).hasSize(1);
      assertThat(listeners.getFirst().getCategories()).containsExactly("gateways");
    }

    @Test
    void shouldNotAffectUserTaskListeners() {
      final List<GlobalListenerCfg> taskListeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask();
      assertThat(taskListeners).isEmpty();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.listener.execution.0.type=shortcut-listener",
        "camunda.listener.execution.0.event-types.0=start",
        "camunda.listener.execution.0.event-types.1=end",
        "camunda.listener.execution.0.retries=3",
        "camunda.listener.execution.0.element-types.0=serviceTask"
      })
  class WithListenerExecutionPath {
    final BrokerBasedProperties brokerCfg;

    WithListenerExecutionPath(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldBindExecutionListenerFromListenerPath() {
      final List<GlobalListenerCfg> listeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getExecution();
      assertThat(listeners).hasSize(1);
      assertThat(listeners.getFirst().getType()).isEqualTo("shortcut-listener");
    }

    @Test
    void shouldSetEventTypesFromListenerPath() {
      final List<GlobalListenerCfg> listeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getExecution();
      assertThat(listeners.getFirst().getEventTypes()).containsExactly("start", "end");
    }

    @Test
    void shouldSetRetriesFromListenerPath() {
      final List<GlobalListenerCfg> listeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getExecution();
      assertThat(listeners.getFirst().getRetries()).isEqualTo("3");
    }

    @Test
    void shouldSetElementTypesFromListenerPath() {
      final List<GlobalListenerCfg> listeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getExecution();
      assertThat(listeners.getFirst().getElementTypes()).containsExactly("serviceTask");
    }

    @Test
    void shouldForceExecutionTypeOnRuntimeConfiguration() {
      final var runtimeConfig =
          brokerCfg
              .getExperimental()
              .getEngine()
              .getGlobalListeners()
              .createGlobalListenersConfiguration();
      assertThat(runtimeConfig.execution()).hasSize(1);
      assertThat(runtimeConfig.execution().getFirst().listenerType())
          .isEqualTo(GlobalListenerType.EXECUTION);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.global-listeners.execution.0.type=cluster-listener",
        "camunda.cluster.global-listeners.execution.0.event-types.0=start",
        "camunda.listener.execution.0.type=shortcut-listener",
        "camunda.listener.execution.0.event-types.0=end"
      })
  class WithMergedClusterAndListenerExecutionPaths {
    final BrokerBasedProperties brokerCfg;

    WithMergedClusterAndListenerExecutionPaths(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldMergeBothSourcesIntoExecutionList() {
      final List<GlobalListenerCfg> listeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getExecution();
      assertThat(listeners).hasSize(2);
      assertThat(listeners)
          .extracting(GlobalListenerCfg::getType)
          .containsExactly("cluster-listener", "shortcut-listener");
    }

    @Test
    void shouldForceExecutionTypeForAllMergedEntries() {
      final var runtimeConfig =
          brokerCfg
              .getExperimental()
              .getEngine()
              .getGlobalListeners()
              .createGlobalListenersConfiguration();
      assertThat(runtimeConfig.execution()).hasSize(2);
      assertThat(runtimeConfig.execution())
          .allSatisfy(
              cfg -> assertThat(cfg.listenerType()).isEqualTo(GlobalListenerType.EXECUTION));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.global-listeners.execution.0.type=audit-event",
        "camunda.cluster.global-listeners.execution.0.event-types.0=start",
        "camunda.cluster.global-listeners.execution.0.element-types.0=process",
        "camunda.cluster.global-listeners.user-task.0.type=task-hook",
        "camunda.cluster.global-listeners.user-task.0.event-types.0=creating"
      })
  class WithBothUserTaskAndExecutionListeners {
    final BrokerBasedProperties brokerCfg;

    WithBothUserTaskAndExecutionListeners(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldConfigureBothListenerTypesIndependently() {
      final List<GlobalListenerCfg> executionListeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getExecution();
      final List<GlobalListenerCfg> taskListeners =
          brokerCfg.getExperimental().getEngine().getGlobalListeners().getUserTask();
      assertThat(executionListeners).hasSize(1);
      assertThat(executionListeners.getFirst().getType()).isEqualTo("audit-event");
      assertThat(taskListeners).hasSize(1);
      assertThat(taskListeners.getFirst().getType()).isEqualTo("task-hook");
    }

    @Test
    void shouldForceSetCorrectListenerTypesInRuntimeConfiguration() {
      // given
      final var globalListenersCfg = brokerCfg.getExperimental().getEngine().getGlobalListeners();

      // when
      final var runtimeConfig = globalListenersCfg.createGlobalListenersConfiguration();

      // then — execution listeners must have EXECUTION type, not the USER_TASK default
      assertThat(runtimeConfig.execution()).hasSize(1);
      assertThat(runtimeConfig.execution().getFirst().listenerType())
          .isEqualTo(GlobalListenerType.EXECUTION);

      // then — user-task listeners must have USER_TASK type
      assertThat(runtimeConfig.userTask()).hasSize(1);
      assertThat(runtimeConfig.userTask().getFirst().listenerType())
          .isEqualTo(GlobalListenerType.USER_TASK);
    }
  }
}
