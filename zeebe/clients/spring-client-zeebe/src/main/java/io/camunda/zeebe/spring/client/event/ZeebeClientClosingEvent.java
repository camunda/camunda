package io.camunda.zeebe.spring.client.event;

import io.camunda.zeebe.client.ZeebeClient;
import org.springframework.context.ApplicationEvent;

/**
 * Emitted when the ZeebeClient is about to close. Typically, during application shutdown, but maybe
 * more often in test case or never if the ZeebeClient is disabled, see {@link
 * ZeebeClientCreatedEvent} for more details
 */
public class ZeebeClientClosingEvent extends ApplicationEvent {

  public final ZeebeClient client;

  public ZeebeClientClosingEvent(Object source, ZeebeClient client) {
    super(source);
    this.client = client;
  }

  public ZeebeClient getClient() {
    return client;
  }
}
