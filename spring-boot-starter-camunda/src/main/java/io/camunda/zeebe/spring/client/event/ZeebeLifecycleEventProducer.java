package io.camunda.zeebe.spring.client.event;

import io.camunda.zeebe.client.ZeebeClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;

public class ZeebeLifecycleEventProducer implements SmartLifecycle {

  protected boolean running = false;

  private final ApplicationEventPublisher publisher;

  private final ZeebeClient client;

  public ZeebeLifecycleEventProducer(
      final ZeebeClient client, final ApplicationEventPublisher publisher) {
    this.client = client;
    this.publisher = publisher;
  }

  @Override
  public void start() {
    publisher.publishEvent(
        new ClientStartedEvent()); // keep old deprecated event for a bit before delting it
    publisher.publishEvent(new ZeebeClientCreatedEvent(this, client));

    this.running = true;
  }

  @Override
  public void stop() {
    publisher.publishEvent(
        new ClientStoppedEvent()); // keep old deprecated event for a bit before delting it
    publisher.publishEvent(new ZeebeClientClosingEvent(this, client));

    this.running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
