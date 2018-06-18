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
package io.zeebe.test;

import io.zeebe.client.api.clients.TopicClient;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.events.WorkflowInstanceState;
import io.zeebe.client.api.subscription.TopicSubscription;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.rules.ExternalResource;

public class TopicEventRecorder extends ExternalResource {
  private static final String SUBSCRIPTION_NAME = "event-recorder";

  private final List<JobEvent> jobEvents = new CopyOnWriteArrayList<>();
  private final List<WorkflowInstanceEvent> wfInstanceEvents = new CopyOnWriteArrayList<>();

  private final ClientRule clientRule;

  private final String topicName;

  protected TopicSubscription subscription;

  public TopicEventRecorder(final ClientRule clientRule, final String topicName) {
    this.clientRule = clientRule;
    this.topicName = topicName;
  }

  @Override
  protected void before() {
    startRecordingEvents();
  }

  @Override
  protected void after() {
    stopRecordingEvents();
  }

  private void startRecordingEvents() {
    final TopicClient client = clientRule.getClient().topicClient(topicName);

    subscription =
        client
            .newSubscription()
            .name(SUBSCRIPTION_NAME)
            .jobEventHandler(jobEvents::add)
            .workflowInstanceEventHandler(wfInstanceEvents::add)
            .open();
  }

  private void stopRecordingEvents() {
    subscription.close();
    subscription = null;
  }

  private <T> Optional<T> getLastEvent(final Stream<T> eventStream) {
    final List<T> events = eventStream.collect(Collectors.toList());

    if (events.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(events.get(events.size() - 1));
    }
  }

  public List<WorkflowInstanceEvent> getWorkflowInstanceEvents(
      final Predicate<WorkflowInstanceEvent> matcher) {
    return wfInstanceEvents.stream().filter(matcher).collect(Collectors.toList());
  }

  public WorkflowInstanceEvent getLastWorkflowInstanceEvent(
      final Predicate<WorkflowInstanceEvent> matcher) {
    return getLastEvent(wfInstanceEvents.stream().filter(matcher))
        .orElseThrow(() -> new AssertionError("no event found"));
  }

  public List<JobEvent> getJobEvents(final Predicate<JobEvent> matcher) {
    return jobEvents.stream().filter(matcher).collect(Collectors.toList());
  }

  public JobEvent getLastJobEvent(final Predicate<JobEvent> matcher) {
    return getLastEvent(jobEvents.stream().filter(matcher))
        .orElseThrow(() -> new AssertionError("no event found"));
  }

  public static Predicate<WorkflowInstanceEvent> wfInstance(
      final WorkflowInstanceState eventState) {
    return e -> e.getState().equals(eventState);
  }

  public static Predicate<WorkflowInstanceEvent> wfInstanceKey(final long key) {
    return e -> e.getWorkflowInstanceKey() == key;
  }

  public static Predicate<JobEvent> jobKey(final long key) {
    return e -> e.getMetadata().getKey() == key;
  }

  public static Predicate<JobEvent> jobType(final String type) {
    return e -> e.getType().equals(type);
  }
}
