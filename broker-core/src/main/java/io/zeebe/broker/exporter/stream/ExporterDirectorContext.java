/*
 * Zeebe Broker Core
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
package io.zeebe.broker.exporter.stream;

import io.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.zeebe.db.ZeebeDb;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import java.time.Duration;
import java.util.Collection;

public class ExporterDirectorContext {

  private int id;
  private String name;

  private LogStream logStream;
  private LogStreamReader logStreamReader;

  private Collection<ExporterDescriptor> descriptors;

  private Duration snapshotPeriod;
  private ZeebeDb zeebeDb;
  private int maxSnapshots;

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public LogStream getLogStream() {
    return logStream;
  }

  public LogStreamReader getLogStreamReader() {
    return logStreamReader;
  }

  public Collection<ExporterDescriptor> getDescriptors() {
    return descriptors;
  }

  public Duration getSnapshotPeriod() {
    return snapshotPeriod;
  }

  public ZeebeDb getZeebeDb() {
    return zeebeDb;
  }

  public int getMaxSnapshots() {
    return maxSnapshots;
  }

  public ExporterDirectorContext id(int id) {
    this.id = id;
    return this;
  }

  public ExporterDirectorContext name(String name) {
    this.name = name;
    return this;
  }

  public ExporterDirectorContext logStream(LogStream logStream) {
    this.logStream = logStream;
    return this;
  }

  public ExporterDirectorContext logStreamReader(LogStreamReader logStreamReader) {
    this.logStreamReader = logStreamReader;
    return this;
  }

  public ExporterDirectorContext descriptors(Collection<ExporterDescriptor> descriptors) {
    this.descriptors = descriptors;
    return this;
  }

  public ExporterDirectorContext snapshotPeriod(Duration snapshotPeriod) {
    this.snapshotPeriod = snapshotPeriod;
    return this;
  }

  public ExporterDirectorContext zeebeDb(ZeebeDb zeebeDb) {
    this.zeebeDb = zeebeDb;
    return this;
  }

  public ExporterDirectorContext maxSnapshots(int maxSnapshots) {
    this.maxSnapshots = maxSnapshots;
    return this;
  }
}
