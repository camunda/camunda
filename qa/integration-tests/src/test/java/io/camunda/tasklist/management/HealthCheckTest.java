/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.management;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.tasklist.Application;
import io.camunda.tasklist.es.ElasticsearchConnector;
import io.camunda.tasklist.es.ElasticsearchTask;
import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.management.HealthCheckTest.AddManagementPropertiesInitializer;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.tasklist.webapp.security.ElasticsearchSessionRepository;
import io.camunda.tasklist.webapp.security.WebSecurityConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hamcrest.core.StringContains;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@AutoConfigureObservability(tracing = false)
@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      TasklistProperties.class,
      TestApplicationWithNoBeans.class,
      ElsIndicesHealthIndicator.class,
      WebSecurityConfig.class,
      ElasticsearchSessionRepository.class,
      RetryElasticsearchClient.class,
      ElasticsearchTask.class,
      TasklistProperties.class,
      ElasticsearchConnector.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = AddManagementPropertiesInitializer.class)
public class HealthCheckTest {

  @Autowired private WebApplicationContext context;

  @MockBean private ElsIndicesHealthIndicator probes;

  private MockMvc mockMvc;

  @Before
  public void setupMockMvc() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  public void testReady() throws Exception {
    given(probes.getHealth(anyBoolean())).willReturn(Health.up().build());
    mockMvc
        .perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"UP\"}"));
    verify(probes, times(1)).getHealth(anyBoolean());
  }

  @Test
  public void testHealth() throws Exception {
    mockMvc
        .perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"UP\"}"));
    verifyNoInteractions(probes);
  }

  @Test
  public void testReadyStateIsNotOK() throws Exception {
    given(probes.getHealth(anyBoolean())).willReturn(Health.down().build());
    mockMvc
        .perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"UP\"}"));

    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isServiceUnavailable());
    verify(probes, times(1)).getHealth(anyBoolean());
  }

  @Test
  public void testMetrics() throws Exception {
    mockMvc
        .perform(get("/actuator/prometheus"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    StringContains.containsString(
                        "# HELP jvm_memory_used_bytes The amount of used memory\n"
                            + "# TYPE jvm_memory_used_bytes gauge")));
  }

  public static class AddManagementPropertiesInitializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
      final Map<String, Object> map = Application.getManagementProperties();
      final List<String> properties = new ArrayList<>();
      map.forEach(
          (key, value) -> {
            // not clear how to connect mockMvc to management port
            if (!key.contains("port")) {
              properties.add(key + "=" + value);
            }
          });
      TestPropertyValues.of(properties).applyTo(applicationContext.getEnvironment());
    }
  }
}
