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
package io.camunda.client.spring.annotation.processor;

import static io.camunda.client.annotation.AnnotationUtil.getDeploymentValues;
import static io.camunda.client.annotation.AnnotationUtil.isDeployment;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.value.DeploymentValue;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2;
import io.camunda.client.api.response.Decision;
import io.camunda.client.api.response.DecisionRequirements;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.Form;
import io.camunda.client.api.response.Process;
import io.camunda.client.bean.BeanInfo;
import io.camunda.client.spring.event.CamundaPostDeploymentSpringEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
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
  public boolean isApplicableFor(final BeanInfo beanInfo) {
    return isDeployment(beanInfo);
  }

  @Override
  public void configureFor(final BeanInfo beanInfo) {
    final List<DeploymentValue> zeebeDeploymentValue = getDeploymentValues(beanInfo);
    if (!zeebeDeploymentValue.isEmpty()) {
      LOGGER.debug("Configuring deployments: {}", zeebeDeploymentValue);
      deploymentValues.addAll(zeebeDeploymentValue);
    }
  }

  @Override
  public void start(final CamundaClient client) {
    if (deploymentValues.isEmpty()) {
      return;
    }
    final List<DeploymentEvent> deploymentEvents =
        deploymentValues.stream().map(d -> deploy(client, d)).toList();
    publisher.publishEvent(new CamundaPostDeploymentSpringEvent(this, deploymentEvents));
  }

  @Override
  public void stop(final CamundaClient client) {
    // noop for deployment
  }

  private DeploymentEvent deploy(
      final CamundaClient camundaClient, final DeploymentValue deploymentValue) {
    final String tenantId = deploymentValue.getTenantId();
    final List<Resource> resources =
        deploymentValue.getResources().stream()
            .flatMap(r -> Arrays.stream(getResources(r)))
            .distinct()
            .toList();
    if (resources.isEmpty()) {
      throw new IllegalArgumentException("No resources found to deploy");
    }
    final DeployResourceCommandStep1 command = camundaClient.newDeployResourceCommand();
    DeployResourceCommandStep2 commandStep2 = null;
    for (final Resource resource : resources) {
      try (final InputStream inputStream = resource.getInputStream()) {
        if (commandStep2 == null) {
          commandStep2 = command.addResourceStream(inputStream, resource.getFilename());
        } else {
          commandStep2 = commandStep2.addResourceStream(inputStream, resource.getFilename());
        }
      } catch (final IOException e) {
        throw new RuntimeException("Error reading resource: " + e.getMessage(), e);
      }
    }
    if (tenantId != null) {
      commandStep2.tenantId(tenantId);
    }
    final DeploymentEvent deploymentEvent = commandStep2.execute();
    logDeployment(
        "Processes",
        deploymentEvent.getProcesses(),
        Process::getBpmnProcessId,
        Process::getVersion);
    logDeployment(
        "Decision Requirements",
        deploymentEvent.getDecisionRequirements(),
        DecisionRequirements::getDmnDecisionRequirementsId,
        DecisionRequirements::getVersion);
    logDeployment(
        "Decisions",
        deploymentEvent.getDecisions(),
        Decision::getDmnDecisionId,
        Decision::getVersion);
    logDeployment("Forms", deploymentEvent.getForm(), Form::getFormId, Form::getVersion);
    logDeployment(
        "Resources",
        deploymentEvent.getResource(),
        io.camunda.client.api.response.Resource::getResourceId,
        io.camunda.client.api.response.Resource::getVersion);
    return deploymentEvent;
  }

  private <T> void logDeployment(
      final String resourceName,
      final List<T> deployed,
      final Function<T, String> idRef,
      final Function<T, Integer> versionRef) {
    if (deployed == null || deployed.isEmpty()) {
      return;
    }
    LOGGER.info(
        "Deployed {}: {}",
        resourceName,
        deployed.stream()
            .map(d -> String.format("<%s:%d>", idRef.apply(d), versionRef.apply(d)))
            .collect(Collectors.joining(",")));
  }

  public Resource[] getResources(final String resources) {
    try {
      return RESOURCE_RESOLVER.getResources(resources);
    } catch (final IOException e) {
      return new Resource[0];
    }
  }
}
