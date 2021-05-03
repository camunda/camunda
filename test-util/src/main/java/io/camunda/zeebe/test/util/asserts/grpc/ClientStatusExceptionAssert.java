/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.asserts.grpc;

import io.grpc.Status;
import io.zeebe.client.api.command.ClientStatusException;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.InstanceOfAssertFactory;

public final class ClientStatusExceptionAssert
    extends AbstractThrowableAssert<ClientStatusExceptionAssert, ClientStatusException> {
  private static final InstanceOfAssertFactory<ClientStatusException, ClientStatusExceptionAssert>
      ASSERT_FACTORY =
          new InstanceOfAssertFactory<>(
              ClientStatusException.class, ClientStatusExceptionAssert::assertThat);

  public ClientStatusExceptionAssert(final ClientStatusException e) {
    super(e, ClientStatusExceptionAssert.class);
  }

  public static ClientStatusExceptionAssert assertThat(final ClientStatusException e) {
    return new ClientStatusExceptionAssert(e);
  }

  public static InstanceOfAssertFactory<ClientStatusException, ClientStatusExceptionAssert>
      assertFactory() {
    return ASSERT_FACTORY;
  }

  public ClientStatusExceptionAssert hasStatusSatisfying(final Consumer<Status> statusAssertions) {
    statusAssertions.accept(actual.getStatus());
    return myself;
  }
}
