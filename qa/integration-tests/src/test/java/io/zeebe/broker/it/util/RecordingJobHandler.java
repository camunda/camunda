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
package io.zeebe.broker.it.util;

import java.util.*;

import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.subscription.JobHandler;

public class RecordingJobHandler implements JobHandler
{
    protected List<JobEvent> handledJobs = Collections.synchronizedList(new ArrayList<>());
    protected int nextJobHandler = 0;
    protected final JobHandler[] jobHandlers;

    public RecordingJobHandler()
    {
        this((controller, job) ->
        {
            // do nothing
        });
    }

    public RecordingJobHandler(JobHandler... jobHandlers)
    {
        this.jobHandlers = jobHandlers;
    }

    @Override
    public void handle(JobClient client, JobEvent job)
    {
        final JobHandler handler = jobHandlers[nextJobHandler];
        nextJobHandler = Math.min(nextJobHandler + 1, jobHandlers.length - 1);

        try
        {
            handler.handle(client, job);
        }
        finally
        {
            handledJobs.add(job);
        }
    }

    public List<JobEvent> getHandledJobs()
    {
        return handledJobs;
    }

    public void clear()
    {
        handledJobs.clear();
    }

}
