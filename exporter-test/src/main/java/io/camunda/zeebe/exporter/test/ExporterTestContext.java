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
package io.camunda.zeebe.exporter.test;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.camunda.zeebe.exporter.api.context.Configuration;
import io.camunda.zeebe.exporter.api.context.Context;
import java.util.Objects;
import net.jcip.annotations.NotThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A mutable implementation of {@link Context} for testing. The context is passed only during the
 * configuration phase, and any modifications afterwards isn't really used, so there is no real need
 * to make this thread-safe at the moment.
 *
 * <p>This class is meant to be used by {@link ExporterTestHarness}.
 */
@NotThreadSafe
public final class ExporterTestContext implements Context {
  private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(ExporterTestContext.class);
  private static final int DEFAULT_PARTITION_ID = 1;

  private Configuration configuration;
  private RecordFilter recordFilter;
  private int partitionId = DEFAULT_PARTITION_ID;

  @Override
  public Logger getLogger() {
    return DEFAULT_LOGGER;
  }

  @Override
  @Nullable
  @CheckForNull
  public Configuration getConfiguration() {
    return configuration;
  }

  public ExporterTestContext setConfiguration(final Configuration configuration) {
    this.configuration = Objects.requireNonNull(configuration, "must specify a configuration");
    return this;
  }

  @Override
  public void setFilter(final @Nullable RecordFilter filter) {
    recordFilter = filter;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public ExporterTestContext setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  @Nullable
  @CheckForNull
  public RecordFilter getRecordFilter() {
    return recordFilter;
  }
}
