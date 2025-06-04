package io.camunda.unifiedconfig;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UnifiedConfiguration.class)
public class UnifiedConfigurationRegistry {
  // Intentionally left blank: this class enables configuration properties

  @Autowired UnifiedConfiguration config;

  @PostConstruct
  public void someRuntimeChecks() {
    System.out.println("Breakpoint here and check the object config");
  }
}
