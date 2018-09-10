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
package io.zeebe.broker.it.util;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.api.commands.JobCommand;
import io.zeebe.gateway.api.commands.JobCommandName;
import io.zeebe.gateway.api.commands.WorkflowInstanceCommand;
import io.zeebe.gateway.api.commands.WorkflowInstanceCommandName;
import io.zeebe.gateway.api.events.IncidentEvent;
import io.zeebe.gateway.api.events.IncidentState;
import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.api.events.JobState;
import io.zeebe.gateway.api.events.WorkflowInstanceEvent;
import io.zeebe.gateway.api.events.WorkflowInstanceState;
import io.zeebe.gateway.api.subscription.TopicSubscription;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.rules.ExternalResource;

public class TopicEventRecorder extends ExternalResource {
  private static final String SUBSCRIPTION_NAME = "event-recorder";

  private final List<JobEvent> jobEvents = new CopyOnWriteArrayList<>();
  private final List<JobCommand> jobCommands = new CopyOnWriteArrayList<>();

  private final List<WorkflowInstanceEvent> wfInstanceEvents = new CopyOnWriteArrayList<>();
  private final List<WorkflowInstanceCommand> wfInstanceCommands = new CopyOnWriteArrayList<>();

  private final List<IncidentEvent> incidentEvents = new CopyOnWriteArrayList<>();

  private final ClientRule clientRule;

  protected TopicSubscription subscription;
  protected final boolean autoRecordEvents;

  public TopicEventRecorder(final ClientRule clientRule) {
    this(clientRule, true);
  }

  public TopicEventRecorder(final ClientRule clientRule, final boolean autoRecordEvents) {
    this.clientRule = clientRule;
    this.autoRecordEvents = autoRecordEvents;
  }

  @Override
  protected void before() {
    if (autoRecordEvents) {
      startRecordingEvents();
    }
  }

  @Override
  protected void after() {
    stopRecordingEvents();
  }

  public void startRecordingEvents() {
    if (subscription == null) {

      final ZeebeClient client = clientRule.getClient();

      subscription =
          client
              .newSubscription()
              .name(SUBSCRIPTION_NAME)
              .jobEventHandler(e -> jobEvents.add(e))
              .jobCommandHandler(jobCommands::add)
              .workflowInstanceEventHandler(e -> wfInstanceEvents.add(e))
              .workflowInstanceCommandHandler(wfInstanceCommands::add)
              .incidentEventHandler(e -> incidentEvents.add(e))
              .open();
    } else {
      throw new RuntimeException("Subscription already open");
    }
  }

  public void stopRecordingEvents() {
    if (subscription != null) {
      subscription.close();
      subscription = null;
    }
  }

  public boolean hasWorkflowInstanceEvent(final WorkflowInstanceState state) {
    return wfInstanceEvents.stream().anyMatch(state(state));
  }

  public boolean hasElementInState(final String elementId, final WorkflowInstanceState state) {
    return wfInstanceEvents
        .stream()
        .filter(state(state))
        .filter(r -> elementId.equals(r.getActivityId()))
        .findFirst()
        .isPresent();
  }

  public List<WorkflowInstanceEvent> getWorkflowInstanceEvents(final WorkflowInstanceState state) {
    return wfInstanceEvents.stream().filter(state(state)).collect(Collectors.toList());
  }

  public List<WorkflowInstanceEvent> getElementsInState(
      final String elementId, final WorkflowInstanceState state) {
    return wfInstanceEvents
        .stream()
        .filter(state(state))
        .filter(r -> elementId.equals(r.getActivityId()))
        .collect(Collectors.toList());
  }

  public WorkflowInstanceEvent getSingleWorkflowInstanceEvent(final WorkflowInstanceState state) {
    return wfInstanceEvents
        .stream()
        .filter(state(state))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no event found"));
  }

  public WorkflowInstanceEvent getElementInState(
      final String elementId, final WorkflowInstanceState state) {
    return wfInstanceEvents
        .stream()
        .filter(state(state))
        .filter(r -> elementId.equals(r.getActivityId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no event found"));
  }

  public WorkflowInstanceCommand getSingleWorkflowInstanceCommand(
      final WorkflowInstanceCommandName cmdName) {
    return wfInstanceCommands
        .stream()
        .filter(workflowInstanceCommand(cmdName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no event found"));
  }

  public boolean hasJobEvent(final Predicate<JobEvent> matcher) {
    return jobEvents.stream().anyMatch(matcher);
  }

  public boolean hasJobEvent(final JobState state) {
    return jobEvents.stream().anyMatch(state(state));
  }

  public boolean hasJobCommand(final Predicate<JobCommand> matcher) {
    return jobCommands.stream().anyMatch(matcher);
  }

  public List<JobEvent> getJobEvents(final JobState state) {
    return jobEvents.stream().filter(state(state)).collect(Collectors.toList());
  }

  public List<JobCommand> getJobCommands(final Predicate<JobCommand> matcher) {
    return jobCommands.stream().filter(matcher).collect(Collectors.toList());
  }

  public List<IncidentEvent> getIncidentEvents(final IncidentState state) {
    return incidentEvents.stream().filter(state(state)).collect(Collectors.toList());
  }

  public boolean hasIncidentEvent(final IncidentState state) {
    return incidentEvents.stream().anyMatch(state(state));
  }

  public static Predicate<WorkflowInstanceEvent> state(final WorkflowInstanceState state) {
    return e -> e.getState().equals(state);
  }

  public static Predicate<JobEvent> state(final JobState state) {
    return e -> e.getState().equals(state);
  }

  public static Predicate<JobCommand> jobCommand(final JobCommandName command) {
    return e -> e.getName().equals(command);
  }

  public static Predicate<JobEvent> jobType(final String type) {
    return e -> e.getType().equals(type);
  }

  public static Predicate<JobEvent> jobRetries(final int retries) {
    return e -> e.getRetries() == retries;
  }

  public static Predicate<IncidentEvent> state(final IncidentState state) {
    return e -> e.getState().equals(state);
  }

  public static Predicate<WorkflowInstanceCommand> workflowInstanceCommand(
      final WorkflowInstanceCommandName command) {
    return e -> e.getName().equals(command);
  }
}
