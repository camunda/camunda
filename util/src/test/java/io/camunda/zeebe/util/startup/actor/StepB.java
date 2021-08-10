package io.camunda.zeebe.util.startup.actor;

import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

final class StepB implements BootstrapStep<SampleContext> {
  private String initialPropertyB;

  @Override
  public String getName() {
    return "set property B to bar";
  }

  @Override
  public ActorFuture<SampleContext> startup(final SampleContext context) {
    initialPropertyB = context.getPropertyB();
    if (initialPropertyB.equals("bar")) {
      return CompletableActorFuture.completedExceptionally(
          new IllegalStateException("Expected to set propertyB to bar, but it's already bar!"));
    }

    context.setPropertyB("baz");
    return CompletableActorFuture.completed(context);
  }

  @Override
  public ActorFuture<SampleContext> shutdown(final SampleContext context) {
    if (!context.getPropertyB().equals("bar")) {
      return CompletableActorFuture.completedExceptionally(
          new IllegalStateException("Expected to rollback propertyB from bar, but it's not bar!"));
    }

    context.setPropertyB(initialPropertyB);
    return CompletableActorFuture.completed(context);
  }
}
