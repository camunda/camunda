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
package io.zeebe.client.impl.clustering;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.zeebe.client.api.commands.BrokerInfo;
import io.zeebe.client.api.commands.Topology;

public class TopologyImpl implements Topology
{
    private List<BrokerInfo> brokers;

    @Override
    public List<BrokerInfo> getBrokers()
    {
        return brokers;
    }

    @JsonDeserialize(contentAs = BrokerInfoImpl.class)
    public void setBrokers(List<BrokerInfo> brokers)
    {
        this.brokers = brokers;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("Topology [brokers=");
        builder.append(brokers);
        builder.append("]");
        return builder.toString();
    }

}
