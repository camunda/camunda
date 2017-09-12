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
package io.zeebe.client.workflow.cmd;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import io.zeebe.client.cmd.Request;
import io.zeebe.client.event.DeploymentEvent;
import io.zeebe.client.event.ResourceType;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;

public interface CreateDeploymentCommand extends Request<DeploymentEvent>
{
    /**
     * Add the given resource to the deployment.
     */
    CreateDeploymentCommand resourceBytes(byte[] resourceBytes, ResourceType resourceType);

    /**
     * Add the given resource to the deployment. The charset must match the
     * encoding of the resource.
     */
    CreateDeploymentCommand resourceString(String resourceString, Charset charset, ResourceType resourceType);

    /**
     * Convenience method for invoking
     * {@link #resourceString(String, Charset, ResourceType)} with
     * {@link StandardCharsets#UTF_8}.
     */
    CreateDeploymentCommand resourceStringUtf8(String resourceString, ResourceType resourceType);

    /**
     * Add the given resource stream to the deployment.
     */
    CreateDeploymentCommand resourceStream(InputStream resourceStream, ResourceType resourceType);

    /**
     * Add the given classpath resource to the deployment. The resource type is
     * detected by the resource name.
     */
    CreateDeploymentCommand resourceFromClasspath(String classpathResource);

    /**
     * Add the given file resource to the deployment. The resource type is
     * detected by the resource name.
     */
    CreateDeploymentCommand resourceFile(String filename);

    /**
     * Add the given workflow model to the deployment.
     */
    CreateDeploymentCommand workflowModel(WorkflowDefinition workflowDefinition);

}
