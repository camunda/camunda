package io.zeebe.gateway.impl.probes.liveness;

import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.system.DiskSpaceHealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnEnabledHealthIndicator("diskspace")
@AutoConfigureBefore(HealthContributorAutoConfiguration.class)
@EnableConfigurationProperties(LivenessDiskSpaceHealthIndicatorProperties.class)
public class LivenessDiskSpaceHealthIndicatorAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "livenessDiskSpaceHealthIndicator")
  public DiskSpaceHealthIndicator livenessDiskSpaceHealthIndicator(
      LivenessDiskSpaceHealthIndicatorProperties properties) {
    return new DiskSpaceHealthIndicator(properties.getPath(), properties.getThreshold());
  }
}
