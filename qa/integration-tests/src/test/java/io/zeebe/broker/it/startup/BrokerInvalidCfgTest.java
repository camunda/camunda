/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.startup;

import io.zeebe.broker.Broker;
import io.zeebe.util.sched.clock.ControlledActorClock;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BrokerInvalidCfgTest {

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void shouldThrowExceptionWithNegativePartitions() throws URISyntaxException {
    // given
    final String path =
        Paths.get(
                BrokerInvalidCfgTest.class
                    .getResource("/invalidCfgs/negativePartitions.toml")
                    .toURI())
            .toAbsolutePath()
            .toString();

    // expected
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Partition count must not be smaller then 1.");

    // when
    new Broker(path, "", new ControlledActorClock());
  }
}
