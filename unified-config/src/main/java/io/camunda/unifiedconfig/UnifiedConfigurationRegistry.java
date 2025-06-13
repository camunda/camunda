package io.camunda.unifiedconfig;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(UnifiedConfiguration.class)
public class UnifiedConfigurationRegistry {
  @Autowired UnifiedConfiguration config;
  @Autowired Environment env;

  @PostConstruct
  public void init() {
    // This is a bit of a hack.
    // At the moment, I'm low on better ideas on how to do this.
    // It is wired only when this object is instantiated with Spring. Otherwise it's null,
    // therefore, we need to IoC it, if we want to use it in tests for deprecated properties.
    FallbackConfig.environment = env;

    System.out.println("Breakpoint here and check the object config");
    config.printFullConfigurationAsYaml();
  }
}
