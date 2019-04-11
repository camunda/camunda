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

public class EnvironmentConstants {

  public static final String ENV_NODE_ID = "ZEEBE_NODE_ID";
  public static final String ENV_HOST = "ZEEBE_HOST";
  public static final String ENV_PORT_OFFSET = "ZEEBE_PORT_OFFSET";
  public static final String ENV_INITIAL_CONTACT_POINTS = "ZEEBE_CONTACT_POINTS";
  public static final String ENV_DIRECTORIES = "ZEEBE_DIRECTORIES";
  public static final String ENV_PARTITIONS_COUNT = "ZEEBE_PARTITIONS_COUNT";
  public static final String ENV_REPLICATION_FACTOR = "ZEEBE_REPLICATION_FACTOR";
  public static final String ENV_CLUSTER_SIZE = "ZEEBE_CLUSTER_SIZE";
  public static final String ENV_CLUSTER_NAME = "ZEEBE_CLUSTER_NAME";
  public static final String ENV_EMBED_GATEWAY = "ZEEBE_EMBED_GATEWAY";
  public static final String ENV_METRICS_HTTP_SERVER = "ZEEBE_METRICS_HTTP_SERVER";
}
