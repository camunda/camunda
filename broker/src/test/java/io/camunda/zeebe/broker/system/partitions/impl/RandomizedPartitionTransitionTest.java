/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import static java.lang.String.format;
import static java.util.List.of;
import static java.util.concurrent.CompletableFuture.runAsync;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.framework;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer;
import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.StateController;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.impl.steps.StreamProcessorTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.ZeebeDbPartitionTransitionStep;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.util.health.HealthMonitor;
import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.ActorScheduler;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.GenerationMode;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomizedPartitionTransitionTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(RandomizedPartitionTransitionTest.class);

  private ActorScheduler actorScheduler;
  private TestActor actor;

  @BeforeTry
  public void beforeTry() {
    actorScheduler = ActorScheduler.newActorScheduler().build();
    actorScheduler.start();

    actor = new TestActor();
    actorScheduler.submitActor(actor);
  }

  /**
   * Verifies that during transitions at most one {@code StreamProcessor} is created. It sets up the
   * following transition chain:
   *
   * <ol>
   *   <li>Pausable dummy step
   *   <li>{@code StreamProcessorTransitionStep}
   * </ol>
   *
   * The first step is there to manipulate execution order. In particular, the step will wait for a
   * countdown latch thus pausing transition execution. This in turn, allows scheduling successive
   * transition which cancel their predecessors
   *
   * @param operations the operations to run
   */
  @Property(generation = GenerationMode.RANDOMIZED)
  void atMostOneStreamProcessorIsRunningAtAnyTime(
      @ForAll("testOperations") final List<TestOperation> operations) {
    LOGGER.debug(
        format(
            "Testing property 'atMostOneStreamProcessorIsRunningAtAnyTime' on sequence %s",
            operations));

    final var instanceTracker =
        new PropertyAssertingInstanceTracker<StreamProcessor>() {
          @Override
          void assertProperties() {
            if (opened.size() > 1) {
              throw new IllegalStateException(
                  "More than one stream processors opened at the same time");
            }
          }
        };

    final var firstStep = new PausableStep(operations);
    final var streamProcessorStep =
        new StreamProcessorTransitionStep(
            (context, role) -> produceMockStreamProcessor(instanceTracker));

    final var context = new TestPartitionTransitionContext();
    context.setComponentHealthMonitor(mock(HealthMonitor.class));

    final var sut = new PartitionTransitionImpl(of(firstStep, streamProcessorStep));
    sut.setConcurrencyControl(actor);
    sut.updateTransitionContext(context);

    runOperations(operations, sut);

    assertThat(instanceTracker.getOpenedInstances())
        .describedAs("Active stream processors at end of transition sequence")
        .hasSizeLessThan(2);
  }

  /**
   * Verifies that during transitions at most one {@code ZeebeDb} is created. It sets up the
   * following transition chain:
   *
   * <ol>
   *   <li>Pausable dummy step
   *   <li>{@code ZeebeDbPartitionTransitionStep}
   * </ol>
   *
   * The first step is there to manipulate execution order. In particular, the step will wait for a
   * countdown latch thus pausing transition execution. This in turn, allows scheduling successive
   * transition which cancel their predecessors
   *
   * @param operations the operations to run
   */
  @Property(generation = GenerationMode.RANDOMIZED)
  void atMostOneZeebeDbIsOpenAtAnyTime(
      @ForAll("testOperations") final List<TestOperation> operations) {
    LOGGER.debug(
        format("Testing property 'atMostOneZeebeDbIsOpenAtAnyTime' on sequence %s", operations));

    final var instanceTracker =
        new PropertyAssertingInstanceTracker<ZeebeDb>() {
          @Override
          void assertProperties() {
            if (opened.size() > 1) {
              throw new IllegalStateException("More than one zeebe db opened at the same time");
            }
          }
        };

    final var firstStep = new PausableStep(operations);
    final var zeebeDbStep = new ZeebeDbPartitionTransitionStep();

    final var context = new TestPartitionTransitionContext();
    context.setStateController(new TestStateController(instanceTracker));

    final var sut = new PartitionTransitionImpl(of(firstStep, zeebeDbStep));
    sut.setConcurrencyControl(actor);
    sut.updateTransitionContext(context);

    runOperations(operations, sut);

    assertThat(instanceTracker.getOpenedInstances())
        .describedAs("Zeebe DB processes at end of transition sequence")
        .hasSizeLessThan(2);
  }

  @AfterTry
  public void afterTry() {
    actorScheduler.stop();
    framework().clearInlineMocks(); // prevent memory leaks from statically held mocks and stubbings
  }

  private void runOperations(
      final List<TestOperation> operations, final PartitionTransitionImpl sut) {
    final var pausedSteps = new ArrayList<CountDownLatch>();
    final var transitionFutures = new ArrayList<ActorFuture<Void>>();
    ActorFuture<Void> latestTransitionFuture = null;

    for (int index = 0; index < operations.size(); index++) {
      final var operation = operations.get(index);

      if (operation instanceof RequestTransition) {
        final var requestTransition = (RequestTransition) operation;

        latestTransitionFuture = sut.transitionTo(index, requestTransition.getRole());
        transitionFutures.add(latestTransitionFuture);
        if (requestTransition.isPause()) {
          pausedSteps.add(requestTransition.getCountDownLatch());
        } else {
          requestTransition.getCountDownLatch().countDown();
        }
      } else { // catch up operation
        catchUp(latestTransitionFuture, pausedSteps);
      }
    }

    // join all transition future to capture any exceptions
    transitionFutures.forEach(caf -> caf.join());
  }

  private void catchUp(
      final ActorFuture<Void> latestTransitionFuture, final ArrayList<CountDownLatch> pausedSteps) {
    if (latestTransitionFuture == null) {
      return;
    }
    while (!latestTransitionFuture.isDone()) { // wait for last transition to complete
      final var stepsToResume = new ArrayList<>(pausedSteps);
      pausedSteps.clear();
      /* Unblock any steps that have been paused.
       * This needs to be done repeatedly, because resuming one paused step
       * might lead to execution of another step which is also scheduled to pause.
       */
      stepsToResume.forEach(CountDownLatch::countDown);
    }
  }

  @Provide
  Arbitrary<List<TestOperation>> testOperations() {
    final var kind = Arbitraries.of(TestOperationKind.class);
    final var role = Arbitraries.of(RaftServer.Role.class);

    final var operation = Combinators.combine(kind, role).as(this::createTestOperation);

    return operation
        .list()
        .ofMaxSize(4)
        .filter(list -> list.stream().anyMatch(RequestTransition.class::isInstance))
        .map(
            list -> {
              list.add(new CatchUpOperation());
              return list;
            });
  }

  private TestOperation createTestOperation(
      final TestOperationKind kind, final RaftServer.Role role) {
    switch (kind) {
      case TRANSITION_TO_ROLE_NO_PAUSE:
        return new RequestTransition(role, false);
      case TRANSITION_TO_RULE_PAUSED:
        return new RequestTransition(role, true);
      case CATCH_UP:
      default:
        return new CatchUpOperation();
    }
  }

  private StreamProcessor produceMockStreamProcessor(
      final PropertyAssertingInstanceTracker<StreamProcessor> instanceTracker) {
    final var mockStreamProcessor = mock(StreamProcessor.class);

    instanceTracker.registerCreation(mockStreamProcessor);

    when(mockStreamProcessor.openAsync(anyBoolean()))
        .thenAnswer(
            invocation -> {
              instanceTracker.registerOpen(mockStreamProcessor);
              return CompletableActorFuture.completed(null);
            });

    when(mockStreamProcessor.closeAsync())
        .thenAnswer(
            invocation -> {
              instanceTracker.registerClose(mockStreamProcessor);
              return CompletableActorFuture.completed(null);
            });

    return mockStreamProcessor;
  }

  private static final class RequestTransition implements TestOperation {
    final RaftServer.Role role;
    final boolean pause;
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    private RequestTransition(final RaftServer.Role role, final boolean pause) {
      this.role = role;
      this.pause = pause;
    }

    Role getRole() {
      return role;
    }

    boolean isPause() {
      return pause;
    }

    CountDownLatch getCountDownLatch() {
      return countDownLatch;
    }

    @Override
    public int hashCode() {
      int result = role.hashCode();
      result = 31 * result + (pause ? 1 : 0);
      return result;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final RequestTransition that = (RequestTransition) o;

      if (pause != that.pause) {
        return false;
      }
      return role == that.role;
    }

    @Override
    public String toString() {
      return "RequestTransition{" + "role=" + role + ", pause=" + pause + '}';
    }
  }

  private static final class CatchUpOperation implements TestOperation {
    @Override
    public int hashCode() {
      return 1;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      return o != null && getClass() == o.getClass();
    }

    @Override
    public String toString() {
      return "Catch Up";
    }
  }

  private abstract static class PropertyAssertingInstanceTracker<T> {
    final List<T> created = new ArrayList<>();
    final List<T> opened = new ArrayList<>();
    final List<T> closed = new ArrayList<>();

    abstract void assertProperties();

    void registerCreation(final T instance) {
      created.add(instance);
      assertProperties();
    }

    void registerOpen(final T instance) {
      created.remove(instance);
      opened.add(instance);
      assertProperties();
    }

    void registerClose(final T instance) {
      opened.remove(instance);
      closed.add(instance);
      assertProperties();
    }

    List<T> getOpenedInstances() {
      return opened;
    }
  }

  private static final class PausableStep implements PartitionTransitionStep {

    final List<TestOperation> operations;

    public PausableStep(final List<TestOperation> operations) {
      this.operations = operations;
    }

    @Override
    public ActorFuture<Void> prepareTransition(
        final PartitionTransitionContext context, final long term, final Role targetRole) {
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Void> transitionTo(
        final PartitionTransitionContext context, final long term, final Role targetRole) {

      final var testOperation = operations.get(Long.valueOf(term).intValue());

      final var requestedTransition = (RequestTransition) testOperation;

      final var countdownLatch = requestedTransition.getCountDownLatch();

      final var transitionFuture = new CompletableActorFuture<Void>();
      runAsync(
              () -> {
                try {
                  countdownLatch.await();
                } catch (final InterruptedException e) {
                  LOGGER.error(e.getMessage(), e);
                }
              })
          .whenComplete(
              (ok, error) -> {
                if (error != null) {
                  transitionFuture.completeExceptionally(error);
                } else {
                  transitionFuture.complete(null);
                }
              });

      return transitionFuture;
    }

    @Override
    public String getName() {
      return getClass().getSimpleName();
    }
  }

  private static class TestActor extends Actor {

    @Override
    public String getName() {
      return "RandomizedPartitionTransitionTest.testActor";
    }
  }

  private static final class TestStateController implements StateController {

    private final PropertyAssertingInstanceTracker<ZeebeDb> instanceTracker;
    private ZeebeDb zeebeDb;

    private TestStateController(final PropertyAssertingInstanceTracker<ZeebeDb> instanceTracker) {
      this.instanceTracker = instanceTracker;
    }

    @Override
    public ActorFuture<TransientSnapshot> takeTransientSnapshot(
        final long lowerBoundSnapshotPosition) {
      throw new IllegalStateException("Not implemented");
    }

    @Override
    public ActorFuture<ZeebeDb> recover() {
      zeebeDb = mock(ZeebeDb.class);
      instanceTracker.registerCreation(zeebeDb);
      instanceTracker.registerOpen(zeebeDb);
      return CompletableActorFuture.completed(zeebeDb);
    }

    @Override
    public ActorFuture<Void> closeDb() {
      if (zeebeDb != null) {
        instanceTracker.registerClose(zeebeDb);
      }
      return CompletableActorFuture.completed(null);
    }

    @Override
    public void close() throws Exception {
      throw new IllegalStateException("Not implemented");
    }
  }

  private interface TestOperation {}

  private enum TestOperationKind {
    /**
     * Request a transition to a certain role. Do not pause on the first step of that transition.
     */
    TRANSITION_TO_ROLE_NO_PAUSE,
    /**
     * Request a transition to a certain role. Do pause on the first step of that transition. (this
     * is to allow the transition to be cancelled by successor steps)
     */
    TRANSITION_TO_RULE_PAUSED,
    /** Resume all paused steps and run all scheduled transitions to their respective end */
    CATCH_UP
  }
}
