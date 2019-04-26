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
package io.zeebe.broker.system.configuration;

import io.zeebe.util.Environment;
import java.util.Arrays;
import java.util.List;

public class DataCfg implements ConfigurationEntry {
  public static final String DEFAULT_DIRECTORY = "data";

  // Hint: do not use Collections.singletonList as this does not support replaceAll
  private List<String> directories = Arrays.asList(DEFAULT_DIRECTORY);

  private String defaultLogSegmentSize = "512M";

  private String indexBlockSize = "4M";

  private String snapshotPeriod = "15m";

  private String snapshotReplicationPeriod = "5m";

  private int maxSnapshots = 1;

  @Override
  public void init(BrokerCfg globalConfig, String brokerBase, Environment environment) {
    applyEnvironment(environment);
    directories.replaceAll(d -> ConfigurationUtil.toAbsolutePath(d, brokerBase));
  }

  private void applyEnvironment(final Environment environment) {
    environment.getList(EnvironmentConstants.ENV_DIRECTORIES).ifPresent(v -> directories = v);
  }

  public List<String> getDirectories() {
    return directories;
  }

  public void setDirectories(List<String> directories) {
    this.directories = directories;
  }

  public String getDefaultLogSegmentSize() {
    return defaultLogSegmentSize;
  }

  public void setDefaultLogSegmentSize(String defaultLogSegmentSize) {
    this.defaultLogSegmentSize = defaultLogSegmentSize;
  }

  public String getIndexBlockSize() {
    return indexBlockSize;
  }

  public void setIndexBlockSize(final String indexBlockSize) {
    this.indexBlockSize = indexBlockSize;
  }

  public String getSnapshotPeriod() {
    return snapshotPeriod;
  }

  public void setSnapshotPeriod(final String snapshotPeriod) {
    this.snapshotPeriod = snapshotPeriod;
  }

  public String getSnapshotReplicationPeriod() {
    return snapshotReplicationPeriod;
  }

  public void setSnapshotReplicationPeriod(String snapshotReplicationPeriod) {
    this.snapshotReplicationPeriod = snapshotReplicationPeriod;
  }

  public void setMaxSnapshots(final int maxSnapshots) {
    this.maxSnapshots = maxSnapshots;
  }

  public int getMaxSnapshots() {
    return maxSnapshots;
  }

  @Override
  public String toString() {
    return "DataCfg{"
        + "directories="
        + directories
        + ", defaultLogSegmentSize='"
        + defaultLogSegmentSize
        + '\''
        + ", indexBlockSize='"
        + indexBlockSize
        + '\''
        + ", snapshotPeriod='"
        + snapshotPeriod
        + '\''
        + ", snapshotReplicationPeriod='"
        + snapshotReplicationPeriod
        + '\''
        + ", maxSnapshots='"
        + maxSnapshots
        + '\''
        + '}';
  }
}
