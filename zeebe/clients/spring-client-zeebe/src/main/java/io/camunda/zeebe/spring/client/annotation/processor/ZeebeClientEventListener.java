package io.camunda.zeebe.spring.client.annotation.processor;

import io.camunda.zeebe.spring.client.event.ZeebeClientClosingEvent;
import io.camunda.zeebe.spring.client.event.ZeebeClientCreatedEvent;
import org.springframework.context.event.EventListener;

public class ZeebeClientEventListener {

  private final ZeebeAnnotationProcessorRegistry zeebeAnnotationProcessorRegistry;

  public ZeebeClientEventListener(
      ZeebeAnnotationProcessorRegistry zeebeAnnotationProcessorRegistry) {
    this.zeebeAnnotationProcessorRegistry = zeebeAnnotationProcessorRegistry;
  }

  @EventListener
  public void handleStart(ZeebeClientCreatedEvent evt) {
    zeebeAnnotationProcessorRegistry.startAll(evt.getClient());
  }

  @EventListener
  public void handleStop(ZeebeClientClosingEvent evt) {
    zeebeAnnotationProcessorRegistry.stopAll(evt.getClient());
  }
}
