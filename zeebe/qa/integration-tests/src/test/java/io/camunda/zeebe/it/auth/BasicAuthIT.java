/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.postgresql.hostchooser.HostRequirement.any;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.application.sources.DefaultObjectMapperConfiguration;
import io.camunda.service.UserServices;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.broker.BrokerModuleConfiguration;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserWithPasswordRequest;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
    classes = {
      CommonsModuleConfiguration.class,
      BrokerModuleConfiguration.class,
      DefaultObjectMapperConfiguration.class
    },
    properties = {"spring.profiles.active=broker,auth-basic"})
@WebAppConfiguration
public class BasicAuthIT {

  @MockBean UserServices userService;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private WebApplicationContext webAppContext;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = webAppContextSetup(webAppContext).build();
    when(userService.withAuthentication(any(Authentication.class))).thenReturn(userService);
    when(userService.createUser(any(), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new UserRecord()));
  }

  @Test
  void basicAuthWithValidCredentials() throws Exception {
    final CamundaUserWithPasswordRequest dto = new CamundaUserWithPasswordRequest();
    dto.setUsername("demo");
    dto.setPassword("password");
    dto.setName("Demo");
    dto.setEmail("demo@e.c");

    final MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.post("/v2/users")
            .accept("application/json")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(dto));
    final MvcResult mvcResult = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    Assertions.assertEquals(mvcResult.getResponse().getContentAsString(), "");
  }

  //  @Test
  //  void basicAuthWithNoCredentials() {
  //    final CamundaUserWithPasswordRequest dto = new CamundaUserWithPasswordRequest();
  //    dto.setUsername("demo");
  //    dto.setPassword("password");
  //    dto.setName("Demo");
  //    dto.setEmail("demo@e.c");
  //    client.post().uri("/v2/users").bodyValue(dto).exchange().expectStatus().isUnauthorized();
  //  }
  //
  //  @Test
  //  void basicAuthWithBadCredentials() {
  //    final CamundaUserWithPasswordRequest dto = new CamundaUserWithPasswordRequest();
  //    dto.setUsername("demo");
  //    dto.setPassword("password");
  //    dto.setName("Demo");
  //    dto.setEmail("demo@e.c");
  //    client.post().uri("/v2/users").bodyValue(dto).exchange().expectStatus().isUnauthorized();
  //  }
}
