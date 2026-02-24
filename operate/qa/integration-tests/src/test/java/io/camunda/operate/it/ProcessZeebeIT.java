/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.webapp.reader.ProcessReader;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.schema.entities.ProcessEntity;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

public class ProcessZeebeIT extends OperateZeebeAbstractIT {

  @Autowired private ProcessReader processReader;

  @MockitoBean private PermissionsService permissionsService;

  @Override
  @Before
  public void before() {
    super.before();
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.hasPermissionForProcess(any(), any())).thenReturn(true);
  }

  @Test
  public void testProcessCreated() {
    // when
    final Long processDefinitionKey = deployProcess("demoProcess_v_1.bpmn");

    // then
    final ProcessEntity processEntity = processReader.getProcess(processDefinitionKey);
    assertThat(processEntity.getKey()).isEqualTo(processDefinitionKey);
    assertThat(processEntity.getBpmnProcessId()).isEqualTo("demoProcess");
    assertThat(processEntity.getVersion()).isEqualTo(1);
    assertThat(processEntity.getBpmnXml()).isNotEmpty();
    assertThat(processEntity.getName()).isEqualTo("Demo process");
    assertThat(processEntity.getVersionTag()).isEqualTo("demo-tag_v1");
  }
}
