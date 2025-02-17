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
package io.camunda.spring.client.annotation.processor;

import static io.camunda.spring.client.annotation.AnnotationUtil.getDeploymentValue;
import static io.camunda.spring.client.annotation.AnnotationUtil.isDeployment;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.spring.client.annotation.value.DeploymentValue;
import io.camunda.spring.client.bean.ClassInfo;
import io.camunda.spring.client.event.CamundaPostDeploymentEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

public class DeploymentAnnotationProcessor extends AbstractCamundaAnnotationProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeploymentAnnotationProcessor.class);

  private static final ResourcePatternResolver RESOURCE_RESOLVER =
      new PathMatchingResourcePatternResolver();

  private final List<DeploymentValue> deploymentValues = new ArrayList<>();
  private final ApplicationEventPublisher publisher;

  public DeploymentAnnotationProcessor(final ApplicationEventPublisher publisher) {
    this.publisher = publisher;
  }

  @Override
  public boolean isApplicableFor(final ClassInfo beanInfo) {
    return isDeployment(beanInfo);
  }

  @Override
  public void configureFor(final ClassInfo beanInfo) {
    final Optional<DeploymentValue> zeebeDeploymentValue = getDeploymentValue(beanInfo);
    if (zeebeDeploymentValue.isPresent()) {
      LOGGER.info("Configuring deployment: {}", zeebeDeploymentValue.get());
      deploymentValues.add(zeebeDeploymentValue.get());
    }
  }

  @Override
  public void start(final CamundaClient client) {
    final List<Resource> resources =
        deploymentValues.stream()
            .flatMap(d -> d.getResources().stream())
            .flatMap(r -> Arrays.stream(getResources(r)))
            .distinct()
            .toList();
    if (resources.isEmpty()) {
      if (deploymentValues.isEmpty()) {
        return;
      }
      throw new IllegalArgumentException("No resources found to deploy");
    }

    final DeployResourceCommandStep1 command = client.newDeployResourceCommand();
    final DeployResourceCommandStep2 commandStep2 =
        resources.stream()
            .map(
                resource -> {
                  try (final InputStream inputStream = resource.getInputStream()) {
                    return command.addResourceStream(inputStream, resource.getFilename());
                  } catch (final IOException e) {
                    throw new RuntimeException(e.getMessage());
                  }
                })
            .reduce((c1, c2) -> c2)
            .orElseThrow(() -> new IllegalStateException("Unexpected absence of builder"));
    final DeploymentEvent deploymentEvent = commandStep2.send().join();
    LOGGER.info(
        "Deployed: {}",
        Stream.concat(
                deploymentEvent.getDecisionRequirements().stream()
                    .map(
                        wf ->
                            String.format(
                                "<%s:%d>", wf.getDmnDecisionRequirementsId(), wf.getVersion())),
                deploymentEvent.getProcesses().stream()
                    .map(wf -> String.format("<%s:%d>", wf.getBpmnProcessId(), wf.getVersion())))
            .collect(Collectors.joining(",")));
    publisher.publishEvent(new CamundaPostDeploymentEvent(deploymentEvent));
  }

  @Override
  public void stop(final CamundaClient client) {
    // noop for deployment
  }

  public Resource[] getResources(final String resources) {
    try {
      return RESOURCE_RESOLVER.getResources(resources);
    } catch (final IOException e) {
      return new Resource[0];
    }
  }
}
