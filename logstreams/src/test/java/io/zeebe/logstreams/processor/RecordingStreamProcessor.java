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

import io.zeebe.db.ZeebeDb;
import io.zeebe.logstreams.impl.LoggedEventImpl;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RecordingStreamProcessor implements StreamProcessor {
  private final List<LoggedEvent> events = new ArrayList<>();
  private final AtomicInteger processedEvents = new AtomicInteger(0);
  private final ZeebeDb zeebeDb;
  private final EventProcessor eventProcessor =
      spy(
          new EventProcessor() {
            public void processEvent() {
              processedEvents.incrementAndGet();
            };
          });

  private StreamProcessorContext context = null;

  public RecordingStreamProcessor(ZeebeDb zeebeDb) {
    this.zeebeDb = zeebeDb;
  }

  @Override
  public void onOpen(StreamProcessorContext context) {
    this.context = context;
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

  public static RecordingStreamProcessor createSpy(ZeebeDb db) {
    return spy(new RecordingStreamProcessor(db));
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
}
