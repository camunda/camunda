/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import io.camunda.zeebe.mailbox.api.MailboxStore;
import io.camunda.zeebe.mailbox.api.MailboxStoreContractTest;
import java.time.Duration;

final class InMemoryMailboxStoreTest extends MailboxStoreContractTest {

  private final MutableInstantSource clock = new MutableInstantSource();

  @Override
  protected MailboxStore createStore() {
    return new InMemoryMailboxStore(clock);
  }

  @Override
  protected void advanceTime(final Duration duration) {
    clock.advance(duration);
  }
}
