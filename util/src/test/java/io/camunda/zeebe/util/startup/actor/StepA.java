package io.camunda.zeebe.util.startup.actor;

import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

final class StepA implements BootstrapStep<SampleContext> {
  private String initialPropertyA;

  @Override
  public String getName() {
    return "set property A to foo";
  }

  @Override
  public ActorFuture<SampleContext> startup(final SampleContext context) {
    initialPropertyA = context.getPropertyA();
    if (initialPropertyA.equals("foo")) {
      return CompletableActorFuture.completedExceptionally(
          new IllegalStateException("Expected to set propertyA to foo, but it's already foo!"));
    }

    context.setPropertyA("foo");
    return CompletableActorFuture.completed(context);
  }

  @Override
  public ActorFuture<SampleContext> shutdown(final SampleContext context) {
    if (!context.getPropertyA().equals("foo")) {
      return CompletableActorFuture.completedExceptionally(
          new IllegalStateException("Expected to rollback propertyA from foo, but it's not foo!"));
    }

    context.setPropertyA(initialPropertyA);
    return CompletableActorFuture.completed(context);
  }
}
