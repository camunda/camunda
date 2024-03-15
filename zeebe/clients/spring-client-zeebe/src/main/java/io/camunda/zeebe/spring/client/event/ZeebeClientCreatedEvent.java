/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.spring.client.event;

import io.camunda.zeebe.client.ZeebeClient;
import org.springframework.context.ApplicationEvent;

/**
 * Event which is triggered when the ZeebeClient was created. This can be used to register further
 * work that should be done, like starting job workers or doing deployments.
 *
 * <p>In a normal production application this event is simply fired once during startup when the
 * ZeebeClient is created and thus ready to use. However, in test cases it might be fired multiple
 * times, as every test case gets its own dedicated engine also leading to new ZeebeClients being
 * created (at least logically, as the ZeebeClient Spring bean might simply be a proxy always
 * pointing to the right client automatically to avoid problems with @Autowire).
 *
 * <p>Furthermore, when `zeebe.client.enabled=false`, the event might not be fired ever
 */
public class ZeebeClientCreatedEvent extends ApplicationEvent {

  public final ZeebeClient client;

  public ZeebeClientCreatedEvent(final Object source, final ZeebeClient client) {
    super(source);
    this.client = client;
  }

  public ZeebeClient getClient() {
    return client;
  }
}
