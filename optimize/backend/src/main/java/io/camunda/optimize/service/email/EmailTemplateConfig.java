/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.email;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

@org.springframework.context.annotation.Configuration
public class EmailTemplateConfig {

  @Bean
  public FreeMarkerConfigurer freemarkerClassLoaderConfig() {
    final Configuration configuration = new Configuration(Configuration.VERSION_2_3_27);
    final TemplateLoader templateLoader =
        new ClassTemplateLoader(this.getClass(), "/emailtemplates");
    configuration.setTemplateLoader(templateLoader);
    final FreeMarkerConfigurer freeMarkerConfigurer = new FreeMarkerConfigurer();
    freeMarkerConfigurer.setConfiguration(configuration);
    return freeMarkerConfigurer;
  }
}
