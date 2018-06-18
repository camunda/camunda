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

import io.zeebe.util.DurationUtil;
import java.time.Duration;

public class MetricsCfg implements ConfigurationEntry {
  private String reportingInterval = "5s";
  private String file = "metrics/zeebe.prom";

  @Override
  public void init(BrokerCfg brokerCfg, String brokerBase) {
    file = ConfigurationUtil.toAbsolutePath(file, brokerBase);
  }

  public Duration getReportingIntervalDuration() {
    return DurationUtil.parse(reportingInterval);
  }

  public String getReportingInterval() {
    return reportingInterval;
  }

  public void setReportingInterval(String reportingInterval) {
    this.reportingInterval = reportingInterval;
  }

  public String getFile() {
    return file;
  }

  public void setFile(String metricsFile) {
    this.file = metricsFile;
  }
}
