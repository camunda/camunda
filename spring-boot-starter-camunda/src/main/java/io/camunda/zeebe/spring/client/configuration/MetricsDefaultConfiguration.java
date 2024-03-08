package io.camunda.zeebe.spring.client.configuration;

import io.camunda.zeebe.spring.client.metrics.DefaultNoopMetricsRecorder;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

public class MetricsDefaultConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public MetricsRecorder noopMetricsRecorder() {
    return new DefaultNoopMetricsRecorder();
  }
}
