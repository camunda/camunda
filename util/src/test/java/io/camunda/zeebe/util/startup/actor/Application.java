package io.camunda.zeebe.util.startup.actor;

import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.ActorScheduler;
import java.util.List;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Application extends Actor {
  private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

  private final BootstrapProcess<SampleContext> bootstrap =
      new BootstrapProcess<>("sample", List.of(new StepA(), new StepB()), LOGGER);

  private SampleContext context = new SampleContext();

  private Application() {}

  public static void main(final String[] args) throws Exception {
    final var shutdownBarrier = new ShutdownSignalBarrier();
    final var application = new Application();

    try (final var scheduler = ActorScheduler.newActorScheduler().build()) {
      scheduler.start();
      scheduler.submitActor(application).join();
      shutdownBarrier.await();
      application.close();
    }
  }

  @Override
  public String getName() {
    return "application";
  }

  @Override
  protected void onActorStarted() {
    bootstrap.startup(context).onComplete(this::onStartupComplete);
  }

  @Override
  protected void onActorClosing() {
    bootstrap.shutdown(context).onComplete(this::onShutdownComplete);
  }

  private void onShutdownComplete(final SampleContext newContext, final Throwable error) {
    if (error == null) {
      LOGGER.info("sample bootstrap shutdown completed with context: {}", newContext);
    } else {
      LOGGER.error("sample bootstrap shutdown failed with latest context: {}", context, error);
    }
  }

  private void onStartupComplete(final SampleContext newContext, final Throwable error) {
    if (error == null) {
      LOGGER.info("sample bootstrap startup completed with context: {}", newContext);
    } else {
      LOGGER.error("sample bootstrap startup failed with latest context: {}", context, error);
    }
  }
}
