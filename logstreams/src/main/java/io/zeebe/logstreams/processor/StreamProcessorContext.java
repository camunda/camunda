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

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.ActorScheduler;
import java.time.Duration;

public class StreamProcessorContext {
  protected int id;
  protected String name;

  protected boolean isReadOnlyProcessor;

  protected LogStream logStream;

  protected LogStreamReader logStreamReader;
  protected LogStreamRecordWriter logStreamWriter;

  protected Duration snapshotPeriod;
  protected SnapshotController snapshotController;

  protected ActorScheduler actorScheduler;
  private ActorControl actorControl;

  protected EventFilter eventFilter;

  private Runnable suspendRunnable;
  private Runnable resumeRunnable;

  public LogStream getLogStream() {
    return logStream;
  }

  public void setLogStream(LogStream logstream) {
    this.logStream = logstream;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public ActorScheduler getActorScheduler() {
    return actorScheduler;
  }

  public void setActorScheduler(ActorScheduler actorScheduler) {
    this.actorScheduler = actorScheduler;
  }

  public void setLogStreamReader(LogStreamReader logStreamReader) {
    this.logStreamReader = logStreamReader;
  }

  public LogStreamReader getLogStreamReader() {
    return logStreamReader;
  }

  public LogStreamRecordWriter getLogStreamWriter() {
    return logStreamWriter;
  }

  public void setLogStreamWriter(LogStreamRecordWriter logStreamWriter) {
    this.logStreamWriter = logStreamWriter;
  }

  public Duration getSnapshotPeriod() {
    return snapshotPeriod;
  }

  public void setSnapshotPeriod(Duration snapshotPeriod) {
    this.snapshotPeriod = snapshotPeriod;
  }

  public SnapshotController getSnapshotController() {
    return snapshotController;
  }

  public void setSnapshotController(SnapshotController snapshotController) {
    this.snapshotController = snapshotController;
  }

  public void setEventFilter(EventFilter eventFilter) {
    this.eventFilter = eventFilter;
  }

  public EventFilter getEventFilter() {
    return eventFilter;
  }

  public void setReadOnly(boolean readOnly) {
    this.isReadOnlyProcessor = readOnly;
  }

  public boolean isReadOnlyProcessor() {
    return isReadOnlyProcessor;
  }

  public ActorControl getActorControl() {
    return actorControl;
  }

  public void setActorControl(ActorControl actorControl) {
    this.actorControl = actorControl;
  }

  public Runnable getSuspendRunnable() {
    return suspendRunnable;
  }

  public void setSuspendRunnable(Runnable suspendRunnable) {
    this.suspendRunnable = suspendRunnable;
  }

  public void suspendController() {
    suspendRunnable.run();
  }

  public Runnable getResumeRunnable() {
    return resumeRunnable;
  }

  public void setResumeRunnable(Runnable resumeRunnable) {
    this.resumeRunnable = resumeRunnable;
  }

  public void resumeController() {
    resumeRunnable.run();
  }

  private StreamProcessorFactory streamProcessorFactory;

  public void setStreamProcessorFactory(StreamProcessorFactory streamProcessorFactory) {
    this.streamProcessorFactory = streamProcessorFactory;
  }

  public StreamProcessorFactory getStreamProcessorFactory() {
    return streamProcessorFactory;
  }
}
