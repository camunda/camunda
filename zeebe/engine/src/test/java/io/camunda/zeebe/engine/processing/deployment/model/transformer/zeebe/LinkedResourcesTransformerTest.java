/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerTask;
import io.camunda.zeebe.engine.processing.deployment.model.element.LinkedResource;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeLinkedResource;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeLinkedResources;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LinkedResourcesTransformerTest {
  @Test
  void shouldNotAddLinkedResourcesIfNoneProvided() {
    // given
    final var process = new ExecutableJobWorkerTask("process");

    // when
    final ZeebeLinkedResources linkedResources = Mockito.mock(ZeebeLinkedResources.class);
    new LinkedResourcesTransformer().transform(process, linkedResources);

    // then
    Assertions.assertThat(process.getJobWorkerProperties().getLinkedResources()).isEmpty();
  }

  @Test
  void shouldAddProvidedResources() {
    // given
    final var serviceTask = new ExecutableJobWorkerTask("serviceTask");

    // when
    final ZeebeLinkedResources linkedResources = Mockito.mock(ZeebeLinkedResources.class);
    final ZeebeLinkedResource resource = Mockito.mock(ZeebeLinkedResource.class);
    Mockito.when(resource.getResourceId()).thenReturn("id");
    Mockito.when(resource.getBindingType()).thenReturn(ZeebeBindingType.deployment);
    Mockito.when(resource.getLinkName()).thenReturn("linkName");
    Mockito.when(resource.getResourceType()).thenReturn("RPA");
    Mockito.when(linkedResources.getLinkedResources()).thenReturn(List.of(resource));

    new LinkedResourcesTransformer().transform(serviceTask, linkedResources);

    // then
    Assertions.assertThat(serviceTask.getJobWorkerProperties().getLinkedResources()).hasSize(1);

    final LinkedResource linkedResource =
        serviceTask.getJobWorkerProperties().getLinkedResources().get(0);
    Assertions.assertThat(linkedResource)
        .hasFieldOrPropertyWithValue("resourceId", "id")
        .hasFieldOrPropertyWithValue("linkName", "linkName")
        .hasFieldOrPropertyWithValue("resourceType", "RPA")
        .hasFieldOrPropertyWithValue("bindingType", ZeebeBindingType.deployment);
  }
}
