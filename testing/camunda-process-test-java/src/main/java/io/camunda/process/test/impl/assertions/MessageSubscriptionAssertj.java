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
package io.camunda.process.test.impl.assertions;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.search.filter.MessageSubscriptionFilter;
import io.camunda.client.api.search.response.MessageSubscription;
import io.camunda.process.test.api.CamundaAssertAwaitBehavior;
import java.util.List;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageSubscriptionAssertj extends AbstractAssert<MessageSubscriptionAssertj, String> {

  private static final Logger LOG = LoggerFactory.getLogger(MessageSubscriptionAssertj.class);

  private final CamundaDataSource dataSource;
  private final CamundaAssertAwaitBehavior awaitBehavior;

  public MessageSubscriptionAssertj(
      final CamundaDataSource dataSource,
      final CamundaAssertAwaitBehavior awaitBehavior,
      final String failureMessagePrefix) {
    super(failureMessagePrefix, MessageSubscriptionAssertj.class);
    this.dataSource = dataSource;
    this.awaitBehavior = awaitBehavior;
  }

  public void isWaitingForMessage(final long processInstanceKey, final String expectedMessageName) {

    awaitMessageSubscription(
        processInstanceKey,
        f -> f.messageName(expectedMessageName),
        messageSubscriptions ->
            assertThat(messageSubscriptions)
                .withFailMessage(
                    "%s should be expecting message '%s', but was not.",
                    actual, expectedMessageName)
                .isNotEmpty());
  }

  public void isWaitingForMessage(
      final long processInstanceKey,
      final String expectedMessageName,
      final String correlationKey) {

    awaitMessageSubscription(
        processInstanceKey,
        f -> f.messageName(expectedMessageName).correlationKey(correlationKey),
        messageSubscriptions ->
            assertThat(messageSubscriptions)
                .withFailMessage(
                    "%s should be expecting message '%s' [correlation-key: %s], but was not.",
                    actual, expectedMessageName, correlationKey)
                .isNotEmpty());
  }

  public void isNotWaitingForMessage(
      final long processInstanceKey, final String expectedMessageName) {

    awaitMessageSubscription(
        processInstanceKey,
        f -> f.messageName(expectedMessageName),
        messageSubscriptions ->
            assertThat(messageSubscriptions)
                .withFailMessage(
                    "%s should not be expecting message '%s', but was.",
                    actual, expectedMessageName)
                .isEmpty());
  }

  public void isNotWaitingForMessage(
      final long processInstanceKey,
      final String expectedMessageName,
      final String correlationKey) {

    awaitMessageSubscription(
        processInstanceKey,
        f -> f.messageName(expectedMessageName).correlationKey(correlationKey),
        messageSubscriptions ->
            assertThat(messageSubscriptions)
                .withFailMessage(
                    "%s should not be expecting message '%s' [correlation-key: %s], but was.",
                    actual, expectedMessageName, correlationKey)
                .isEmpty());
  }

  private void awaitMessageSubscription(
      final long processInstanceKey,
      final Consumer<MessageSubscriptionFilter> filter,
      final Consumer<List<MessageSubscription>> assertionCallback) {

    awaitBehavior.untilAsserted(
        () ->
            dataSource.getMessageSubscriptions(
                f -> filter.accept(f.processInstanceKey(processInstanceKey))),
        assertionCallback);
  }
}
