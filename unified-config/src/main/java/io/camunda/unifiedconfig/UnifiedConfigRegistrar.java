package io.camunda.unifiedconfig;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers unified configuration properties for the application.
 */
@Configuration
@EnableConfigurationProperties(CamundaConfiguration.class)
public class UnifiedConfigRegistrar {
  // Intentionally left blank: this class enables configuration properties
}
