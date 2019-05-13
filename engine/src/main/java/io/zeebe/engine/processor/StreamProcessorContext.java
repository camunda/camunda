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
package io.zeebe.engine.processor;

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

  protected LogStream logStream;

  private LogStreamReader logStreamReader;
  protected LogStreamRecordWriter logStreamWriter;

  protected Duration snapshotPeriod;
  protected SnapshotController snapshotController;

  protected ActorScheduler actorScheduler;
  private ActorControl actorControl;

  private EventFilter eventFilter;

  private Runnable suspendRunnable;
  private Runnable resumeRunnable;
  private int maxSnapshots;
  private boolean deleteDataOnSnapshot;

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

  public void setMaxSnapshots(final int maxSnapshots) {
    this.maxSnapshots = maxSnapshots;
  }

  public int getMaxSnapshots() {
    return maxSnapshots;
  }

  public void setDeleteDataOnSnapshot(final boolean deleteDataOnSnapshot) {
    this.deleteDataOnSnapshot = deleteDataOnSnapshot;
  }

  public boolean getDeleteDataOnSnapshot() {
    return deleteDataOnSnapshot;
  }
}
