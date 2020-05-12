package io.zeebe.gateway.impl.probes.liveness;

import io.zeebe.gateway.impl.probes.health.AbstractDelayedHealthIndicatorProperties;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "management.health.liveness.gatewayclusterawareness")
public class LivenessGatewayClusterAwarenessHealthIndicatorProperties
    extends AbstractDelayedHealthIndicatorProperties {

  @Override
  protected Duration getDefaultMaxDowntime() {
    return Duration.ofMinutes(5);
  }
}
