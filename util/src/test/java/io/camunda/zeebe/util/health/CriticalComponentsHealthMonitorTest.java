/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.health;

import static io.camunda.zeebe.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.ActorControl;
import io.camunda.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.LoggerFactory;

public class CriticalComponentsHealthMonitorTest {

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
            monitor = new CriticalComponentsHealthMonitor(actor, LoggerFactory.getLogger("test"));
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
    final ControllableComponent component = new ControllableComponent();
    monitor.registerComponent("test", component);

    // when
    waitUntilAllDone();
    assertThat(monitor.getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);

    component.setHealthStatus(HealthStatus.UNHEALTHY);
    waitUntilAllDone();

    // then
    assertThat(monitor.getHealthStatus()).isEqualTo(HealthStatus.UNHEALTHY);
  }

  @Test
  public void shouldRecover() {
    // given
    final ControllableComponent component = new ControllableComponent();
    monitor.registerComponent("test", component);
    waitUntilAllDone();
    component.setHealthStatus(HealthStatus.UNHEALTHY);
    waitUntilAllDone();
    assertThat(monitor.getHealthStatus()).isEqualTo(HealthStatus.UNHEALTHY);

    // when
    component.setHealthStatus(HealthStatus.HEALTHY);
    waitUntilAllDone();

    // then
    assertThat(monitor.getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);
  }

  @Test
  public void shouldMonitorMultipleComponent() {
    // given
    final ControllableComponent component1 = new ControllableComponent();
    final ControllableComponent component2 = new ControllableComponent();

    monitor.registerComponent("test1", component1);
    monitor.registerComponent("test2", component2);

    waitUntilAllDone();
    assertThat(monitor.getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);

    // when
    component2.setHealthStatus(HealthStatus.UNHEALTHY);
    waitUntilAllDone();

    // then
    assertThat(monitor.getHealthStatus()).isEqualTo(HealthStatus.UNHEALTHY);

    // when
    component2.setHealthStatus(HealthStatus.HEALTHY);
    component1.setHealthStatus(HealthStatus.UNHEALTHY);
    waitUntilAllDone();

    // then
    assertThat(monitor.getHealthStatus()).isEqualTo(HealthStatus.UNHEALTHY);

    // when
    component1.setHealthStatus(HealthStatus.HEALTHY);
    waitUntilAllDone();

    // then
    assertThat(monitor.getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);
  }

  @Test
  public void shouldRemoveComponent() {
    // given
    final ControllableComponent component = new ControllableComponent();
    monitor.registerComponent("test", component);
    waitUntil(() -> monitor.getHealthStatus() == HealthStatus.HEALTHY);

    // when
    monitor.removeComponent("test");
    component.setHealthStatus(HealthStatus.UNHEALTHY);
    waitUntilAllDone();

    // then
    assertThat(monitor.getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);
  }

  @Test
  public void shouldMonitorComponentDeath() {
    // given
    final ControllableComponent component1 = new ControllableComponent();
    final ControllableComponent component2 = new ControllableComponent();

    monitor.registerComponent("comp1", component1);
    monitor.registerComponent("comp2", component2);
    waitUntilAllDone();

    // when/then
    component1.setHealthStatus(HealthStatus.UNHEALTHY);
    component2.setHealthStatus(HealthStatus.DEAD);
    waitUntilAllDone();
    assertThat(monitor.getHealthStatus()).isEqualTo(HealthStatus.DEAD);

    // when/then
    component1.setHealthStatus(HealthStatus.HEALTHY);
    waitUntilAllDone();
    assertThat(monitor.getHealthStatus()).isEqualTo(HealthStatus.DEAD);
  }

  private void waitUntilAllDone() {
    actorControl.call(() -> null).join();
  }

  private static class ControllableComponent implements HealthMonitorable {
    private final Set<FailureListener> failureListeners = new HashSet<>();
    private volatile HealthStatus healthStatus = HealthStatus.HEALTHY;

    @Override
    public HealthStatus getHealthStatus() {
      return healthStatus;
    }

    void setHealthStatus(final HealthStatus healthStatus) {
      if (this.healthStatus != healthStatus) {
        switch (healthStatus) {
          case HEALTHY:
            failureListeners.forEach(FailureListener::onRecovered);
            break;
          case UNHEALTHY:
            failureListeners.forEach(FailureListener::onFailure);
            break;
          case DEAD:
            failureListeners.forEach(FailureListener::onUnrecoverableFailure);
            break;
          default:
            break;
        }
      }
      this.healthStatus = healthStatus;
    }

    @Override
    public void addFailureListener(final FailureListener failureListener) {
      failureListeners.add(failureListener);
    }

    @Override
    public void removeFailureListener(final FailureListener failureListener) {
      failureListeners.remove(failureListener);
    }
  }
}
