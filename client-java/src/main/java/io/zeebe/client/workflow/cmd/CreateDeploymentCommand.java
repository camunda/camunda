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

import org.camunda.bpm.model.bpmn.BpmnModelInstance;

import io.zeebe.client.cmd.Request;
import io.zeebe.client.event.DeploymentEvent;

public interface CreateDeploymentCommand extends Request<DeploymentEvent>
{
    /**
     * Add the given workflow XML as bytes to the deployment.
     */
    CreateDeploymentCommand resourceBytes(byte[] resourceBytes);

    /**
     * Add the given workflow XML as String to the deployment. The charset parameter should match the
     * XML header's charset if defined.
     */
    CreateDeploymentCommand resourceString(String resourceString, Charset charset);

    /**
     * Convenience method for invoking {@link #resourceString(String, Charset)} with {@link StandardCharsets#UTF_8}.
     */
    CreateDeploymentCommand resourceStringUtf8(String resourceString);

    /**
     * Add the given workflow XML as stream to the deployment.
     */
    CreateDeploymentCommand resourceStream(InputStream resourceStream);

    /**
     * Add the given workflow XML as classpath resource to the deployment.
     */
    CreateDeploymentCommand resourceFromClasspath(String classpathResource);

    /**
     * Add the given workflow XML as file to the deployment.
     */
    CreateDeploymentCommand resourceFile(String filename);

    /**
     * Add the given workflow as model instance to the deployment.
     */
    CreateDeploymentCommand bpmnModelInstance(BpmnModelInstance modelInstance);
}
