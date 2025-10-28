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
package io.camunda.zeebe.spring.client.annotation.processor;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.spring.client.annotation.Deployment;
import io.camunda.zeebe.spring.client.annotation.value.ZeebeDeploymentValue;
import io.camunda.zeebe.spring.client.bean.ClassInfo;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StringUtils;

public class ZeebeDeploymentAnnotationProcessor extends AbstractZeebeAnnotationProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ZeebeDeploymentAnnotationProcessor.class);

  private static final ResourcePatternResolver RESOURCE_RESOLVER =
      new PathMatchingResourcePatternResolver();

  private final List<ZeebeDeploymentValue> deploymentValues = new ArrayList<>();

  public ZeebeDeploymentAnnotationProcessor() {}

  @Override
  public boolean isApplicableFor(final ClassInfo beanInfo) {
    return beanInfo.hasClassAnnotation(Deployment.class);
  }

  @Override
  public void configureFor(final ClassInfo beanInfo) {
    final Optional<ZeebeDeploymentValue> zeebeDeploymentValue = readAnnotation(beanInfo);
    if (zeebeDeploymentValue.isPresent()) {
      LOGGER.info("Configuring deployment: {}", zeebeDeploymentValue.get());
      deploymentValues.add(zeebeDeploymentValue.get());
    }
  }

  @Override
  public void start(final ZeebeClient client) {
    deploymentValues.forEach(
        deployment -> {
          final DeployResourceCommandStep1 deployResourceCommand =
              client.newDeployResourceCommand();

          final DeploymentEvent deploymentResult =
              deployment.getResources().stream()
                  .flatMap(resource -> Stream.of(getResources(resource)))
                  .distinct()
                  .map(
                      resource -> {
                        try (final InputStream inputStream = resource.getInputStream()) {
                          return deployResourceCommand.addResourceStream(
                              inputStream, resource.getFilename());
                        } catch (final IOException e) {
                          throw new RuntimeException(e.getMessage());
                        }
                      })
                  .filter(Objects::nonNull)
                  .peek(
                      deploy -> {
                        if (deployment.getTenantId() != null) {
                          deploy.tenantId(deployment.getTenantId());
                        }
                      })
                  .reduce((first, second) -> second)
                  .orElseThrow(
                      () ->
                          new IllegalArgumentException("Requires at least one resource to deploy"))
                  .send()
                  .join();

          LOGGER.info(
              "Deployed: {}",
              Stream.concat(
                      deploymentResult.getDecisionRequirements().stream()
                          .map(
                              wf ->
                                  String.format(
                                      "<%s:%d>",
                                      wf.getDmnDecisionRequirementsId(), wf.getVersion())),
                      deploymentResult.getProcesses().stream()
                          .map(
                              wf ->
                                  String.format("<%s:%d>", wf.getBpmnProcessId(), wf.getVersion())))
                  .collect(Collectors.joining(",")));
        });
  }

  @Override
  public void stop(final ZeebeClient client) {
    // noop for deployment
  }

  public Optional<ZeebeDeploymentValue> readAnnotation(final ClassInfo beanInfo) {
    final Optional<Deployment> annotation = beanInfo.getAnnotation(Deployment.class);
    if (annotation.isEmpty()) {
      return Optional.empty();
    } else {
      final List<String> resources =
          Arrays.stream(annotation.get().resources()).collect(Collectors.toList());
      final String tenantId =
          StringUtils.hasText(annotation.get().tenantId()) ? annotation.get().tenantId() : null;
      ZeebeDeploymentValue.builder()
          .beanInfo(beanInfo)
          .resources(resources)
          .tenantId(tenantId)
          .build();
      return Optional.of(
          ZeebeDeploymentValue.builder().beanInfo(beanInfo).resources(resources).build());
    }
  }

  public Resource[] getResources(final String resources) {
    try {
      return RESOURCE_RESOLVER.getResources(resources);
    } catch (final IOException e) {
      return new Resource[0];
    }
  }
}
