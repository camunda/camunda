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
}
