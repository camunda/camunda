package io.camunda.unifiedconfig;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(UnifiedConfiguration.class)
public class UnifiedConfigurationRegistry {
  private static Environment environment;

  @Autowired UnifiedConfiguration config;
  @Autowired Environment env;

  @PostConstruct
  public void init() {
    environment = env;

    System.out.println("Breakpoint here and check the object config");
    config.printFullConfigurationAsYaml();
  }

  public static String getDeprecatedValue(String breadcrumb) {
    return environment.getProperty(breadcrumb);
  }
}
