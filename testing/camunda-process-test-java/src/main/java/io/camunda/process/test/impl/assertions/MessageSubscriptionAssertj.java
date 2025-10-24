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

import io.camunda.client.api.search.enums.MessageSubscriptionState;
import io.camunda.client.api.search.filter.CorrelatedMessageSubscriptionFilter;
import io.camunda.client.api.search.filter.MessageSubscriptionFilter;
import io.camunda.client.api.search.response.CorrelatedMessageSubscription;
import io.camunda.client.api.search.response.MessageSubscription;
import io.camunda.process.test.api.CamundaAssertAwaitBehavior;
import java.util.List;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;

public class MessageSubscriptionAssertj extends AbstractAssert<MessageSubscriptionAssertj, String> {

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

  public void isWaitingForMessage(final long processInstanceKey, final String messageName) {

    awaitMessageSubscription(
        processInstanceKey,
        filter ->
            filter
                .messageName(messageName)
                .messageSubscriptionState(MessageSubscriptionState.CREATED),
        messageSubscriptions ->
            assertThat(messageSubscriptions)
                .withFailMessage(
                    "%s should have an active message subscription [message-name: '%s'], but no such subscription was found.",
                    actual, messageName)
                .isNotEmpty());
  }

  public void isWaitingForMessage(
      final long processInstanceKey, final String messageName, final String correlationKey) {

    awaitMessageSubscription(
        processInstanceKey,
        f -> f.messageName(messageName).correlationKey(correlationKey),
        messageSubscriptions ->
            assertThat(messageSubscriptions)
                .withFailMessage(
                    "%s should have a message subscription [message-name: '%s', correlation-key: '%s'], but no such subscription was found.",
                    actual, messageName, correlationKey)
                .isNotEmpty());
  }

  public void isNotWaitingForMessage(final long processInstanceKey, final String messageName) {

    awaitMessageSubscription(
        processInstanceKey,
        f -> f.messageName(messageName),
        messageSubscriptions ->
            assertThat(messageSubscriptions)
                .withFailMessage(
                    "%s should have no active message subscription [message-name: '%s'], but found <%d> active subscriptions.",
                    actual, messageName, messageSubscriptions.size())
                .isEmpty());
  }

  public void isNotWaitingForMessage(
      final long processInstanceKey, final String messageName, final String correlationKey) {

    awaitMessageSubscription(
        processInstanceKey,
        filter ->
            filter
                .messageSubscriptionState(MessageSubscriptionState.CREATED)
                .messageName(messageName)
                .correlationKey(correlationKey),
        messageSubscriptions ->
            assertThat(messageSubscriptions)
                .withFailMessage(
                    "%s should have no active message subscription [message-name: '%s', correlation-key: '%s'], but found <%d> active subscriptions.",
                    actual, messageName, correlationKey, messageSubscriptions.size())
                .isEmpty());
  }

  public void hasCorrelatedMessage(final long processInstanceKey, final String messageName) {

    awaitCorrelatedMessages(
        processInstanceKey,
        filter -> filter.messageName(messageName),
        correlatedMessages ->
            assertThat(correlatedMessages)
                .withFailMessage(
                    "%s should have at least one correlated message [message-name: '%s'], but found none.",
                    actual, messageName)
                .isNotEmpty());
  }

  public void hasCorrelatedMessage(
      final long processInstanceKey, final String messageName, final String correlationKey) {

    awaitCorrelatedMessages(
        processInstanceKey,
        filter -> filter.messageName(messageName).correlationKey(correlationKey),
        correlatedMessages ->
            assertThat(correlatedMessages)
                .withFailMessage(
                    "%s should have at least one correlated message [message-name: '%s', correlation-key: '%s'], but found none.",
                    actual, messageName, correlationKey)
                .isNotEmpty());
  }

  private void awaitMessageSubscription(
      final long processInstanceKey,
      final Consumer<MessageSubscriptionFilter> filter,
      final Consumer<List<MessageSubscription>> assertionCallback) {

    awaitBehavior.untilAsserted(
        () ->
            dataSource.findMessageSubscriptions(
                f -> filter.accept(f.processInstanceKey(processInstanceKey))),
        assertionCallback);
  }

  private void awaitCorrelatedMessages(
      final long processInstanceKey,
      final Consumer<CorrelatedMessageSubscriptionFilter> filter,
      final Consumer<List<CorrelatedMessageSubscription>> assertionCallback) {

    awaitBehavior.untilAsserted(
        () ->
            dataSource.findCorrelatedMessages(
                f -> filter.accept(f.processInstanceKey(processInstanceKey))),
        assertionCallback);
  }
}
