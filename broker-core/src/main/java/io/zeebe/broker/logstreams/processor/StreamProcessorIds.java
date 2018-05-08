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
package io.zeebe.broker.logstreams.processor;

public class StreamProcessorIds
{
    // a stream processor partitionId should be unique to distinguish event producers

    public static final int JOB_QUEUE_STREAM_PROCESSOR_ID = 10;

    public static final int JOB_LOCK_STREAM_PROCESSOR_ID = 20;

    public static final int JOB_EXPIRE_LOCK_STREAM_PROCESSOR_ID = 30;

    public static final int TOPIC_SUBSCRIPTION_PUSH_PROCESSOR_ID = 40;

    public static final int TOPIC_SUBSCRIPTION_MANAGEMENT_PROCESSOR_ID = 50;

    public static final int DEPLOYMENT_PROCESSOR_ID = 60;

    public static final int WORKFLOW_INSTANCE_PROCESSOR_ID = 70;

    public static final int INCIDENT_PROCESSOR_ID = 80;

    public static final int SYSTEM_CREATE_TOPIC_PROCESSOR_ID = 1000;
    public static final int SYSTEM_COLLECT_PARTITION_PROCESSOR_ID = 1001;
    public static final int SYSTEM_ID_PROCESSOR_ID = 1002;

    public static final int CLUSTER_TOPIC_STATE = 2000;
}
