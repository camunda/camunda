package io.camunda.unifiedconfig;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers unified configuration properties for the application.
 */
@Configuration
@EnableConfigurationProperties(CamundaConfiguration.class)
public class UnifiedConfigRegistrar {
  // Intentionally left blank: this class enables configuration properties

  @Autowired private CamundaConfiguration config;

  @PostConstruct
  public void someRuntimeChecks() {
    System.out.println("Breakpoint here and check the object config");
  }
}
