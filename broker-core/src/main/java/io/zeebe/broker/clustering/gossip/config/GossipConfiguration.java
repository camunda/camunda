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
package io.zeebe.broker.clustering.gossip.config;

import io.zeebe.broker.system.DirectoryConfiguration;

public class GossipConfiguration extends DirectoryConfiguration
{
    private static final String GOSSIP_FILE_NAME_PATTERN = "%sgossip.zeebe";

    public String[] initialContactPoints = new String[0];

    public int peerCapacity = 1000;

    public int peersStorageInterval = 1;

    public int disseminatorCapacity = 16;
    public int disseminationInterval = 1;
    public int disseminationTimeout = 10;

    public int failureDetectionCapacity = 8;
    public int failureDetectionProbeCapacity = 3;
    public int failureDetectorTimeout = 15;

    public int probeCapacity = 1;
    public int probeTimeout = 10;

    public int suspicionTimeout = 10;

    public int numClientChannelMax = disseminatorCapacity + (failureDetectionCapacity * failureDetectionProbeCapacity) + 1;

    @Override
    protected String componentDirectoryName()
    {
        return "gossip";
    }

    public String fileName()
    {
        return String.format(GOSSIP_FILE_NAME_PATTERN, directory);
    }

}
