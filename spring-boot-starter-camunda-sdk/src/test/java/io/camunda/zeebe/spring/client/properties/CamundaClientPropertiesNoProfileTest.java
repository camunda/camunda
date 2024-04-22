package io.camunda.zeebe.spring.client.properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = CamundaClientPropertiesTestConfig.class)
public class CamundaClientPropertiesNoProfileTest {
  @Autowired CamundaClientProperties camundaClientProperties;

  @Test
  void shouldWork() {}
}
