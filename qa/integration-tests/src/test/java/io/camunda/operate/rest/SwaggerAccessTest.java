/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.operate.util.OperateIntegrationTest;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;

import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    classes = {TestApplicationWithNoBeans.class}
)
@ActiveProfiles({ "dev", "test"})
public class SwaggerAccessTest extends OperateIntegrationTest {

  @Test
  public void shouldReturnSwaggerUI() throws Exception {
    // when
    MockHttpServletRequestBuilder request = get("/swagger-ui/index.html");
    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/html"))
        .andReturn();
    // then
    assertThat(mvcResult.getResponse().getContentAsString()).contains("Swagger UI","swagger-ui");
  }

}

