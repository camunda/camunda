/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers.internal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.webapp.api.rest.v1.controllers.PublicProcessController;
import io.camunda.webapps.schema.entities.ProcessEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
public class PublicProcessControllerTest {

  private MockMvc mockMvc;

  @Mock private ProcessStore processStore;

  @InjectMocks private PublicProcessController instance;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(instance).build();
  }

  @Test
  void testPublicPage() throws Exception {
    final ProcessEntity expectedProcessEntity = new ProcessEntity();
    expectedProcessEntity.setName("processEntity");
    expectedProcessEntity.setBpmnProcessId("bpmnProcessId");
    when(processStore.getProcessByBpmnProcessId("test")).thenReturn(expectedProcessEntity);
    mockMvc
        .perform(MockMvcRequestBuilders.get("/tasklist/new/test"))
        .andExpect(status().isOk())
        .andExpect(model().size(3))
        .andExpect(model().attribute("title", "processEntity"))
        .andExpect(model().attribute("ogImage", "http://localhost/public-start-form-og-image.jpg"))
        .andExpect(model().attribute("ogUrl", "http://localhost/tasklist/new/test"))
        .andExpect(forwardedUrl("/tasklist/index.html"));
  }
}
