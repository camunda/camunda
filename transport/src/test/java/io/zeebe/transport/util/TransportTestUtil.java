/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.util;

import static io.zeebe.test.util.TestUtil.doRepeatedly;

import io.zeebe.dispatcher.ClaimedFragment;
import io.zeebe.dispatcher.Dispatcher;

public class TransportTestUtil {

  public static void waitUntilExhausted(Dispatcher dispatcher) {
    final ClaimedFragment fragment = new ClaimedFragment();

    // claim until its not possible anymore
    doRepeatedly(() -> dispatcher.claim(fragment, 1))
        .until(
            p -> {
              if (p < 0) {
                return true;
              } else {
                fragment.abort();
                return false;
              }
            });
  }
}
