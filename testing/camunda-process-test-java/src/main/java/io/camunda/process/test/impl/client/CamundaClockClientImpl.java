/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.impl.client;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import java.time.Duration;
import java.time.Instant;

/**
 * Controls the clock of the Camunda runtime through the public clock API of the {@link
 * CamundaClient}. The client is created once and closed together with this clock client.
 */
public final class CamundaClockClientImpl implements CamundaClockClient {

  private final CamundaClient client;

  public CamundaClockClientImpl(final CamundaClientBuilderFactory clientBuilderFactory) {
    client = clientBuilderFactory.get().build();
  }

  @Override
  public Instant getCurrentTime() {
    try {
      return client.newClockGetRequest().send().join().getTimestamp().toInstant();
    } catch (final Exception e) {
      throw new RuntimeException("Failed to read the current time.", e);
    }
  }

  @Override
  public void increaseTime(final Duration timeToAdd) {
    // The runtime applies the time in milliseconds. Converting it here makes an overflow fail with
    // an ArithmeticException, as documented in the CamundaProcessTestContext.
    final Duration timeToAddInMillis = Duration.ofMillis(timeToAdd.toMillis());

    try {
      client.newAddClockCommand().offset(timeToAddInMillis).send().join();
    } catch (final Exception e) {
      throw new RuntimeException("Failed to increase the time.", e);
    }
  }

  @Override
  public void setTime(final Instant timeToSet) {
    final Instant now = getCurrentTime();
    increaseTime(Duration.between(now, timeToSet));
  }

  @Override
  public void resetTime() {
    try {
      client.newResetClockCommand().send().join();
    } catch (final Exception e) {
      throw new RuntimeException("Failed to reset the time.", e);
    }
  }

  @Override
  public void close() {
    client.close();
  }
}
