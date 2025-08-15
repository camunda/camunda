/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.health;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthIssue;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CriticalComponentsHealthMonitorTest {
  private static final Logger LOG =
      LoggerFactory.getLogger(CriticalComponentsHealthMonitorTest.class);

  @Rule public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();
  private CriticalComponentsHealthMonitor monitor;
  private ActorControl actorControl;

  @Before
  public void setup() {
    final Actor testActor =
        new Actor() {
          @Override
          public String getName() {
            return "test-actor";
          }

          @Override
          protected void onActorStarting() {
            monitor =
                new CriticalComponentsHealthMonitor(
                    "TestMonitor", actor, LoggerFactory.getLogger("test"));
            actorControl = actor;
          }

          @Override
          protected void onActorStarted() {
            monitor.startMonitoring();
          }
        };
    actorSchedulerRule.submitActor(testActor).join();
  }

  @Test
  public void shouldMonitorComponent() {
    // given
    final ControllableComponent component = new ControllableComponent("test");
    monitor.registerComponent(component);

    // when
    waitUntilAllDone();
    Awaitility.await("component is healthy")
        .until(
            () -> {
              waitUntilAllDone();
              return monitor.getHealthReport().getStatus().equals(HealthStatus.HEALTHY);
            });

    component.setUnhealthy();
    waitUntilAllDone();

    // then
    assertThat(monitor.getHealthReport().getStatus()).isEqualTo(HealthStatus.UNHEALTHY);
  }

  @Test
  public void shouldRecover() {
    // given
    final ControllableComponent component = new ControllableComponent("test");
    monitor.registerComponent(component);
    waitUntilAllDone();
    component.setUnhealthy();
    waitUntilAllDone();
    assertThat(monitor.getHealthReport().getStatus()).isEqualTo(HealthStatus.UNHEALTHY);

    // when
    component.setHealthy();
    waitUntilAllDone();

    // then
    assertThat(monitor.getHealthReport().getStatus()).isEqualTo(HealthStatus.HEALTHY);
  }

  @Test
  public void shouldMonitorMultipleComponent() {
    // given
    final ControllableComponent component1 = new ControllableComponent("test1");
    final ControllableComponent component2 = new ControllableComponent("test2");

    monitor.registerComponent(component1);
    monitor.registerComponent(component2);

    waitUntilAllDone();
    Awaitility.await("component is healthy")
        .until(
            () -> {
              waitUntilAllDone();
              return monitor.getHealthReport().getStatus().equals(HealthStatus.HEALTHY);
            });

    // when
    component2.setUnhealthy();
    waitUntilAllDone();

    // then
    assertThat(monitor.getHealthReport().getStatus()).isEqualTo(HealthStatus.UNHEALTHY);

    // when
    component2.setHealthy();
    component1.setUnhealthy();
    waitUntilAllDone();

    // then
    assertThat(monitor.getHealthReport().getStatus()).isEqualTo(HealthStatus.UNHEALTHY);

    // when
    component1.setHealthy();
    waitUntilAllDone();

    // then
    assertThat(monitor.getHealthReport().getStatus()).isEqualTo(HealthStatus.HEALTHY);
  }

  @Test
  public void shouldRemoveComponent() {
    // given
    final ControllableComponent component = new ControllableComponent("test");
    monitor.registerComponent(component);
    Awaitility.await().until(() -> monitor.getHealthReport().getStatus() == HealthStatus.HEALTHY);

    // when
    monitor.removeComponent(component);
    waitUntilAllDone();
    component.setUnhealthy();
    waitUntilAllDone();

    // then
    assertThat(monitor.getHealthReport().getStatus()).isEqualTo(HealthStatus.HEALTHY);
  }

  @Test
  public void shouldMonitorComponentDeath() {
    // given
    final ControllableComponent component1 = new ControllableComponent("comp1");
    final ControllableComponent component2 = new ControllableComponent("comp2");

    monitor.registerComponent(component1);
    monitor.registerComponent(component2);
    waitUntilAllDone();

    // when/then
    component1.setUnhealthy();
    component2.setDead();
    waitUntilAllDone();
    assertThat(monitor.getHealthReport().getStatus()).isEqualTo(HealthStatus.DEAD);

    // when/then
    component1.setHealthy();
    waitUntilAllDone();
    assertThat(monitor.getHealthReport().getStatus()).isEqualTo(HealthStatus.DEAD);
  }

  @Test
  public void shouldReportArbitrarilyNestedComponents() {
    // given
    final var levels = 6;
    final var children = 3;
    final CriticalComponentsHealthMonitor[] parentComponents =
        new CriticalComponentsHealthMonitor[levels];
    final ControllableComponent[][] components =
        new ControllableComponent[parentComponents.length][children];

    for (int i = 0; i < parentComponents.length; i++) {
      final var parentComponent =
          new CriticalComponentsHealthMonitor("parent-%d".formatted(i), actorControl, LOG);

      parentComponents[i] = parentComponent;
      if (i > 0) {
        parentComponents[i - 1].registerComponent(parentComponent);
      }

      for (int j = 0; j < children; j++) {
        final var component = new ControllableComponent("child-at-%d-%d".formatted(i, j));
        components[i][j] = component;
        parentComponents[i].registerComponent(component);
      }
    }
    waitUntilAllDone();
    final var root = parentComponents[0];

    // when
    // set a child unhealthy at level +1: all its "parents" will be unhealthy
    final var unhealthyFrom = levels - 2;
    components[unhealthyFrom][0].setUnhealthy();
    waitUntilAllDone();
    final var report = root.getHealthReport();
    var parentAtLevel = report;

    // then
    for (int i = 0; i < levels; i++) {
      if (i > 0) {
        parentAtLevel = parentAtLevel.children().get("parent-%d".formatted(i));
      }
      assertThat(parentAtLevel.componentName()).isEqualTo("parent-%d".formatted(i));
      assertThat(parentAtLevel.children()).isNotEmpty();
      if (i <= unhealthyFrom) {
        assertThat(parentAtLevel.status()).isEqualTo(HealthStatus.UNHEALTHY);
      } else {
        assertThat(parentAtLevel.status()).isEqualTo(HealthStatus.HEALTHY);
      }
    }
  }

  @Test
  public void shouldTrackRootIssue() {
    // given
    final var issue = HealthIssue.of(new IllegalStateException(), Instant.ofEpochMilli(19201293L));
    final ControllableComponent component = new ControllableComponent("component");
    monitor.registerComponent(component);
    waitUntilAllDone();

    // when
    component.setDead(issue);
    waitUntilAllDone();

    // then

    Awaitility.await("component is healthy")
        .until(
            () -> {
              waitUntilAllDone();
              return monitor.getHealthReport().issue().equals(issue);
            });
  }

  private void waitUntilAllDone() {
    actorControl.call(() -> null).join();
  }

  private static final class ControllableComponent implements HealthMonitorable {
    private final Set<FailureListener> failureListeners = new HashSet<>();
    private volatile HealthReport healthReport;
    private final String name;

    public ControllableComponent(final String name) {
      this.name = name;
      healthReport = HealthReport.healthy(this);
    }

    @Override
    public String componentName() {
      return name;
    }

    @Override
    public HealthReport getHealthReport() {
      return healthReport;
    }

    @Override
    public void addFailureListener(final FailureListener failureListener) {
      failureListeners.add(failureListener);
    }

    @Override
    public void removeFailureListener(final FailureListener failureListener) {
      failureListeners.remove(failureListener);
    }

    void setHealthy() {
      if (healthReport.getStatus() != HealthStatus.HEALTHY) {
        healthReport = HealthReport.healthy(this);
        failureListeners.forEach(l -> l.onRecovered(healthReport));
      }
    }

    void setUnhealthy() {
      if (healthReport.getStatus() != HealthStatus.UNHEALTHY) {
        healthReport =
            HealthReport.unhealthy(this)
                .withMessage("manually set to status unhealthy", Instant.ofEpochMilli(19201293L));
        failureListeners.forEach((l) -> l.onFailure(healthReport));
      }
    }

    void setDead() {
      setDead(HealthIssue.of("manually set to status dead", Instant.ofEpochMilli(192201293L)));
    }

    void setDead(final HealthIssue issue) {
      if (healthReport.getStatus() != HealthStatus.DEAD) {
        healthReport = HealthReport.dead(this).withIssue(issue);
        failureListeners.forEach((l) -> l.onUnrecoverableFailure(healthReport));
      }
    }
  }
}
