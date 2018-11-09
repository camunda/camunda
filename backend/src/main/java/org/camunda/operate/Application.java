package org.camunda.operate;

import org.camunda.operate.data.AbstractDataGenerator;
import org.camunda.operate.data.develop.DevelopDataGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableAutoConfiguration
public class Application {

  private static final Logger logger = LoggerFactory.getLogger(Application.class);

  public static void main(String[] args) throws Exception {
    final SpringApplication springApplication = new SpringApplication(Application.class);
    springApplication.setAdditionalProfiles("auth");
    springApplication.run(args);
  }

  @Bean(name = "dataGenerator")
  @ConditionalOnMissingBean
  public AbstractDataGenerator stubDataGenerator() {
    logger.debug("Create Data generator stub");
    return new AbstractDataGenerator() {
      @Override
      public void createZeebeData(boolean manuallyCalled) {
        logger.debug("No demo data will be created");
      }
    };
  }

}

