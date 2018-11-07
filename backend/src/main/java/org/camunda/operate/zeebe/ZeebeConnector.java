package org.camunda.operate.zeebe;

import org.camunda.operate.data.AbstractDataGenerator;
import org.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import io.zeebe.client.ZeebeClient;

@Configuration
public class ZeebeConnector {

  private static final Logger logger = LoggerFactory.getLogger(ZeebeConnector.class);

  @Autowired
  private OperateProperties operateProperties;

  @Bean //will be closed automatically
  public ZeebeClient zeebeClient() {

    final String brokerContactPoint = operateProperties.getZeebe().getBrokerContactPoint();

    return ZeebeClient
      .newClientBuilder()
      .brokerContactPoint(brokerContactPoint)
      .build();
  }

}
