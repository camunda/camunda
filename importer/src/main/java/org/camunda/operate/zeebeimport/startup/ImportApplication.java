/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.startup;

import javax.annotation.PostConstruct;
import org.camunda.operate.zeebeimport.ZeebeImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(basePackages = "org.camunda.operate",
    excludeFilters = {
        @ComponentScan.Filter(type= FilterType.REGEX,pattern="org\\.camunda\\.operate\\.webapp\\..*")
    })
@EnableAutoConfiguration(exclude = { SecurityAutoConfiguration.class })
public class ImportApplication {

  private static final Logger logger = LoggerFactory.getLogger(ImportApplication.class);

  @Autowired
  private ZeebeImporter zeebeImporter;

  public static void main(String[] args) throws Exception {
    //To ensure that debug logging performed using java.util.logging is routed into Log4j 2
    System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");

    final SpringApplication springApplication = new SpringApplication(ImportApplication.class);
    springApplication.setAddCommandLineProperties(true);
    springApplication.run(args);
  }

  @PostConstruct
  public void startImport() {
    zeebeImporter.startImportingData();
  }

//  @Bean(name = "dataGenerator")
//  @ConditionalOnMissingBean
//  public DataGenerator stubDataGenerator() {
//    logger.debug("Create Data generator stub");
//    return DataGenerator.DO_NOTHING;
//  }

}
