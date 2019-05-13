/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.zeebe.engine.processor.CommandResponseWriter;
import io.zeebe.engine.processor.TypedEventImpl;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamProcessor.DelegatingEventProcessor;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.TypedStreamWriterImpl;
import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.WorkflowInstanceRelated;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.ExporterIntent;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.JobBatchIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.protocol.intent.MessageStartEventSubscriptionIntent;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.VariableDocumentIntent;
import io.zeebe.protocol.intent.VariableIntent;
import io.zeebe.protocol.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;

@RunWith(Parameterized.class)
public class BlacklistInstanceTest {

  @ClassRule public static ZeebeStateRule zeebeStateRule = new ZeebeStateRule();
  private static final AtomicLong KEY_GENERATOR = new AtomicLong(0);

  @Parameter(0)
  public ValueType recordValueType;

  @Parameter(1)
  public Intent recordIntent;

  @Parameter(2)
  public boolean expectedToBlacklist;

  private DelegatingEventProcessor delegatingEventProcessor;

  @Parameters(name = "{0} {1} should blacklist instance {2}")
  public static Object[][] parameters() {
    return new Object[][] {
      ////////////////////////////////////////
      ////////////// DEPLOYMENTS /////////////
      ////////////////////////////////////////
      {ValueType.DEPLOYMENT, DeploymentIntent.CREATE, false},
      {ValueType.DEPLOYMENT, DeploymentIntent.CREATED, false},
      {ValueType.DEPLOYMENT, DeploymentIntent.DISTRIBUTE, false},
      {ValueType.DEPLOYMENT, DeploymentIntent.DISTRIBUTED, false},

      ////////////////////////////////////////
      /////////////// EXPORTER ///////////////
      ////////////////////////////////////////
      {ValueType.EXPORTER, ExporterIntent.EXPORTED, false},

      ////////////////////////////////////////
      ////////////// INCIDENTS ///////////////
      ////////////////////////////////////////
      {ValueType.INCIDENT, IncidentIntent.CREATE, true},
      {ValueType.INCIDENT, IncidentIntent.CREATED, true},
      {ValueType.INCIDENT, IncidentIntent.RESOLVED, true},

      // USER COMMAND
      {ValueType.INCIDENT, IncidentIntent.RESOLVE, false},

      ////////////////////////////////////////
      ////////////// JOB BATCH ///////////////
      ////////////////////////////////////////
      {ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE, false},
      {ValueType.JOB_BATCH, JobBatchIntent.ACTIVATED, false},

      ////////////////////////////////////////
      //////////////// JOBS //////////////////
      ////////////////////////////////////////
      {ValueType.JOB, JobIntent.CREATE, true},
      {ValueType.JOB, JobIntent.CREATED, true},
      {ValueType.JOB, JobIntent.ACTIVATED, true},
      {ValueType.JOB, JobIntent.COMPLETED, true},
      {ValueType.JOB, JobIntent.TIME_OUT, true},
      {ValueType.JOB, JobIntent.TIMED_OUT, true},
      {ValueType.JOB, JobIntent.FAILED, true},
      {ValueType.JOB, JobIntent.RETRIES_UPDATED, true},
      {ValueType.JOB, JobIntent.CANCEL, true},
      {ValueType.JOB, JobIntent.CANCELED, true},

      // USER COMMAND
      {ValueType.JOB, JobIntent.COMPLETE, false},
      {ValueType.JOB, JobIntent.FAIL, false},
      {ValueType.JOB, JobIntent.UPDATE_RETRIES, false},

      ////////////////////////////////////////
      ////////////// MESSAGES ////////////////
      ////////////////////////////////////////
      {ValueType.MESSAGE, MessageIntent.PUBLISH, false},
      {ValueType.MESSAGE, MessageIntent.PUBLISHED, false},
      {ValueType.MESSAGE, MessageIntent.DELETE, false},
      {ValueType.MESSAGE, MessageIntent.DELETED, false},

      ////////////////////////////////////////
      ////////// MSG START EVENT SUB /////////
      ////////////////////////////////////////
      {ValueType.MESSAGE_START_EVENT_SUBSCRIPTION, MessageStartEventSubscriptionIntent.OPEN, false},
      {
        ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
        MessageStartEventSubscriptionIntent.OPENED,
        false
      },
      {
        ValueType.MESSAGE_START_EVENT_SUBSCRIPTION, MessageStartEventSubscriptionIntent.CLOSE, false
      },
      {
        ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
        MessageStartEventSubscriptionIntent.CLOSED,
        false
      },

      ////////////////////////////////////////
      /////////////// MSG SUB ////////////////
      ////////////////////////////////////////
      {ValueType.MESSAGE_SUBSCRIPTION, MessageSubscriptionIntent.OPEN, true},
      {ValueType.MESSAGE_SUBSCRIPTION, MessageSubscriptionIntent.OPENED, true},
      {ValueType.MESSAGE_SUBSCRIPTION, MessageSubscriptionIntent.CORRELATE, true},
      {ValueType.MESSAGE_SUBSCRIPTION, MessageSubscriptionIntent.CORRELATED, true},
      {ValueType.MESSAGE_SUBSCRIPTION, MessageSubscriptionIntent.CLOSE, true},
      {ValueType.MESSAGE_SUBSCRIPTION, MessageSubscriptionIntent.CLOSED, true},

      ////////////////////////////////////////
      //////////////// TIMERS ////////////////
      ////////////////////////////////////////
      {ValueType.TIMER, TimerIntent.CREATE, true},
      {ValueType.TIMER, TimerIntent.CREATED, true},
      {ValueType.TIMER, TimerIntent.TRIGGER, true},
      {ValueType.TIMER, TimerIntent.TRIGGERED, true},
      {ValueType.TIMER, TimerIntent.CANCEL, true},
      {ValueType.TIMER, TimerIntent.CANCELED, true},

      ////////////////////////////////////////
      /////////////// VAR DOC ////////////////
      ////////////////////////////////////////
      {ValueType.VARIABLE_DOCUMENT, VariableDocumentIntent.UPDATE, false},
      {ValueType.VARIABLE_DOCUMENT, VariableDocumentIntent.UPDATED, false},

      ////////////////////////////////////////
      /////////////// VARIABLE ///////////////
      ////////////////////////////////////////
      {ValueType.VARIABLE, VariableIntent.CREATED, false},
      {ValueType.VARIABLE, VariableIntent.UPDATED, false},

      ////////////////////////////////////////
      ////////// WORKFLOW INSTANCE ///////////
      ////////////////////////////////////////
      {ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, true},
      {ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_ACTIVATING, true},
      {ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_ACTIVATED, true},
      {ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_COMPLETING, true},
      {ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_COMPLETED, true},
      {ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_TERMINATING, true},
      {ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_TERMINATED, true},
      {ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.EVENT_OCCURRED, true},

      // USER COMMAND
      {ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CANCEL, false},

      ////////////////////////////////////////
      //////// WORKFLOW INSTANCE CRE /////////
      ////////////////////////////////////////
      {ValueType.WORKFLOW_INSTANCE_CREATION, WorkflowInstanceCreationIntent.CREATE, false},
      {ValueType.WORKFLOW_INSTANCE_CREATION, WorkflowInstanceCreationIntent.CREATED, true},

      ////////////////////////////////////////
      //////// WORKFLOW INSTANCE SUB /////////
      ////////////////////////////////////////
      {ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION, WorkflowInstanceSubscriptionIntent.OPEN, true},
      {ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION, WorkflowInstanceSubscriptionIntent.OPENED, true},
      {
        ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION, WorkflowInstanceSubscriptionIntent.CORRELATE, true
      },
      {
        ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION,
        WorkflowInstanceSubscriptionIntent.CORRELATED,
        true
      },
      {ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION, WorkflowInstanceSubscriptionIntent.CLOSE, true},
      {ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION, WorkflowInstanceSubscriptionIntent.CLOSED, true}
    };
  }

  @Mock CommandResponseWriter responseWriter;

  @Mock LogStream logStream;

  @Mock TypedStreamWriterImpl typedStreamWriter;

  private long workflowInstanceKey;

  @Before
  public void setup() {
    initMocks(this);
    delegatingEventProcessor =
        new DelegatingEventProcessor(
            0, responseWriter, logStream, typedStreamWriter, zeebeStateRule.getZeebeState());
    workflowInstanceKey = KEY_GENERATOR.getAndIncrement();
  }

  @Test
  public void shouldBlacklist() {
    // given
    final AtomicBoolean processed = new AtomicBoolean(false);
    final TypedRecordProcessor processor =
        new TypedRecordProcessor() {
          @Override
          public void processRecord(
              long position,
              TypedRecord record,
              TypedResponseWriter responseWriter,
              TypedStreamWriter streamWriter,
              Consumer sideEffect) {
            processed.set(true);
          }
        };

    final RecordMetadata metadata = new RecordMetadata();
    metadata.intent(recordIntent);
    metadata.valueType(recordValueType);
    final TypedEventImpl typedEvent = new TypedEventImpl();
    final LoggedEvent loggedEvent = mock(LoggedEvent.class);
    when(loggedEvent.getPosition()).thenReturn(1024L);

    typedEvent.wrap(loggedEvent, metadata, new Value());
    delegatingEventProcessor.wrap(processor, typedEvent, 1024);

    // when
    delegatingEventProcessor.onError(new Exception("expected"));

    // then
    metadata.intent(WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    metadata.valueType(ValueType.WORKFLOW_INSTANCE);
    typedEvent.wrap(null, metadata, new Value());
    assertThat(zeebeStateRule.getZeebeState().isOnBlacklist(typedEvent))
        .isEqualTo(expectedToBlacklist);

    delegatingEventProcessor.wrap(processor, typedEvent, 1025);
    delegatingEventProcessor.processEvent();
    assertThat(processed.get()).isEqualTo(!expectedToBlacklist);
  }

  private final class Value extends UnpackedObject implements WorkflowInstanceRelated {

    @Override
    public long getWorkflowInstanceKey() {
      return workflowInstanceKey;
    }
  }
}
