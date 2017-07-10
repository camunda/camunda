/**
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
package io.zeebe.broker.workflow.graph.model.metadata;

import org.agrona.DirectBuffer;

public class TaskMetadata
{
    private DirectBuffer taskType;
    private int retries;

    private TaskHeader[] headers;

    public TaskHeader[] getHeaders()
    {
        return headers;
    }

    public void setHeaders(TaskHeader[] headers)
    {
        this.headers = headers;
    }

    public DirectBuffer getTaskType()
    {
        return taskType;
    }

    public void setTaskType(DirectBuffer taskype)
    {
        this.taskType = taskype;
    }

    public int getRetries()
    {
        return retries;
    }

    public void setRetries(int retries)
    {
        this.retries = retries;
    }

    public static class TaskHeader
    {
        private String key;
        private String value;

        public TaskHeader(String key, String value)
        {
            this.key = key;
            this.value = value;
        }

        public String getKey()
        {
            return key;
        }

        public void setkey(String name)
        {
            this.key = name;
        }

        public String getValue()
        {
            return value;
        }

        public void setValue(String value)
        {
            this.value = value;
        }
    }

}
