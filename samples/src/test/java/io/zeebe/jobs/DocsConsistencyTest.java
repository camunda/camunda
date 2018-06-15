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
package io.zeebe.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;

import io.zeebe.example.cluster.TopologyViewer;
import io.zeebe.example.data.HandlePayloadAsPojo;
import io.zeebe.example.job.JobWorkerCreator;
import io.zeebe.example.topic.TopicCreator;
import io.zeebe.example.topic.TopicSubscriber;
import io.zeebe.example.workflow.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DocsConsistencyTest
{
    @Parameters
    public static Collection<Object[]> data()
    {
        return Arrays.asList(new Object[][]
        {
            { TopologyViewer.class, "io.zeebe.example.cluster.TopologyViewer" },
            { JobWorkerCreator.class, "io.zeebe.example.job.JobWorkerCreator" },
            { TopicCreator.class, "io.zeebe.example.topic.TopicCreator" },
            { TopicSubscriber.class, "io.zeebe.example.topic.TopicSubscriber" },
            { DeploymentViewer.class, "io.zeebe.example.workflow.DeploymentViewer" },
            { NonBlockingWorkflowInstanceCreator.class, "io.zeebe.example.workflow.NonBlockingWorkflowInstanceCreator" },
            { WorkflowDeployer.class, "io.zeebe.example.workflow.WorkflowDeployer" },
            { WorkflowInstanceCreator.class, "io.zeebe.example.workflow.WorkflowInstanceCreator" },
            { HandlePayloadAsPojo.class, "io.zeebe.example.data.HandlePayloadAsPojo" },
        });
    }

    @Parameter(0)
    public Class<?> exampleClass;

    @Parameter(1)
    public String expectedClassName;

    @Test
    public void todo()
    {
        assertThat(exampleClass.getName())
            .withFailMessage("This class's source code is referenced from the java-client-example docs. " +
                    "Make sure to adapt them as well.")
            .isEqualTo(expectedClassName);
    }
}
