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
package io.zeebe.logstreams.processor;

import static org.mockito.Mockito.spy;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbString;
import io.zeebe.db.impl.DefaultColumnFamily;
import io.zeebe.logstreams.impl.LoggedEventImpl;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RecordingStreamProcessor implements StreamProcessor {

  public static final String LAST_PROCESSED_EVENT = "lastProcessedEvent";
  private final List<LoggedEvent> events = new ArrayList<>();
  private final AtomicInteger processedEvents = new AtomicInteger(0);
  private final AtomicInteger failedEvents = new AtomicInteger(0);

  private final EventProcessor eventProcessor =
      spy(
          new EventProcessor() {
            public void processEvent() {
              processedEvents.incrementAndGet();
              valueInstance.wrapLong(events.get(events.size() - 1).getPosition());
              lastProcessedPositionColumnFamily.put(keyInstance, valueInstance);
            }

            @Override
            public void onError(Throwable exception) {
              failedEvents.incrementAndGet();
            }
          });
  private final ActorFuture<Void> openFuture;

  private StreamProcessorContext context = null;
  private long failedEventPosition;
  private final DbString keyInstance;
  private final ColumnFamily<DbString, DbLong> lastProcessedPositionColumnFamily;
  private final DbLong valueInstance;

  public RecordingStreamProcessor(ZeebeDb zeebeDb, ActorFuture<Void> openFuture) {
    keyInstance = new DbString();
    keyInstance.wrapString(LAST_PROCESSED_EVENT);
    valueInstance = new DbLong();
    lastProcessedPositionColumnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), keyInstance, valueInstance);
    this.openFuture = openFuture;
  }

  @Override
  public void onOpen(StreamProcessorContext context) {
    this.context = context;
    openFuture.complete(null);
  }

  @Override
  public EventProcessor onEvent(LoggedEvent event) {
    final LoggedEventImpl e = (LoggedEventImpl) event;

    final LoggedEventImpl copy = new LoggedEventImpl();
    copy.wrap(BufferUtil.cloneBuffer(e.getBuffer()), e.getFragmentOffset());

    events.add(copy);

    if (eventProcessor == null) {
      processedEvents.incrementAndGet();
    }

    return eventProcessor;
  }

  @Override
  public long getFailedPosition(LoggedEvent currentEvent) {
    if (currentEvent.getPosition() == failedEventPosition) {
      return failedEventPosition;
    }
    return -1;
  }

  @Override
  public long getPositionToRecoveryFrom() {
    final DbLong value = lastProcessedPositionColumnFamily.get(keyInstance);
    return value == null ? NO_EVENTS_PROCESSED : value.getValue();
  }

  public static RecordingStreamProcessor createSpy(ZeebeDb db, ActorFuture<Void> openFuture) {
    return spy(new RecordingStreamProcessor(db, openFuture));
  }

  public void suspend() {
    context.suspendController();
  }

  public void resume() {
    context.getActorControl().call(context::resumeController);
  }

  public List<LoggedEvent> getEvents() {
    return events;
  }

  public int getProcessedEventCount() {
    return processedEvents.get();
  }

  public EventProcessor getEventProcessorSpy() {
    return eventProcessor;
  }

  public StreamProcessorContext getContext() {
    return context;
  }

  public int getProcessingFailedCount() {
    return failedEvents.get();
  }

  public void setFailedEventPosition(long failedEventPosition) {
    this.failedEventPosition = failedEventPosition;
  }
}
