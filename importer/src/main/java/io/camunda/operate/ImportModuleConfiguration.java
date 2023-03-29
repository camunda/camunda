/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

@Configuration
@ComponentScan(basePackages = "io.camunda.operate.zeebeimport", nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@ConditionalOnProperty(name = "camunda.operate.importerEnabled", havingValue = "true", matchIfMissing = true)
public class ImportModuleConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(ImportModuleConfiguration.class);

  @PostConstruct
  public void logModule(){
    logger.info("Starting module: importer");
  }

}
