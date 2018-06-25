package org.camunda.operate.zeebe;

import org.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.zeebe.client.ZeebeClient;


@Configuration
public class ZeebeConnector {

  private Logger logger = LoggerFactory.getLogger(ZeebeConnector.class);

  @Autowired
  private OperateProperties operateProperties;

  @Bean //will be closed automatically
  public ZeebeClient zeebeClient() {

    final String brokerContactPoint = operateProperties.getZeebe().getBrokerContactPoint();

    ZeebeClient zeebeClient = ZeebeClient
      .newClientBuilder()
      .brokerContactPoint(brokerContactPoint)
      .build();

    return zeebeClient;
  }

}
