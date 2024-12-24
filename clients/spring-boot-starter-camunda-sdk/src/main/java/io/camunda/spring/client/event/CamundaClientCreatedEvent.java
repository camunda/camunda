/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.event;

import io.camunda.client.CamundaClient;
import org.springframework.context.ApplicationEvent;

/**
 * Event which is triggered when the CamundaClient was created. This can be used to register further
 * work that should be done, like starting job workers or doing deployments.
 *
 * <p>In a normal production application this event is simply fired once during startup when the
 * CamundaClient is created and thus ready to use. However, in test cases it might be fired multiple
 * times, as every test case gets its own dedicated engine also leading to new CamundaClients being
 * created (at least logically, as the CamundaClient Spring bean might simply be a proxy always
 * pointing to the right client automatically to avoid problems with @Autowire).
 *
 * <p>Furthermore, when `camunda.client.enabled=false`, the event might not be fired ever
 */
public class CamundaClientCreatedEvent extends ApplicationEvent {

  public final CamundaClient client;

  public CamundaClientCreatedEvent(final Object source, final CamundaClient client) {
    super(source);
    this.client = client;
  }

  public CamundaClient getClient() {
    return client;
  }
}
