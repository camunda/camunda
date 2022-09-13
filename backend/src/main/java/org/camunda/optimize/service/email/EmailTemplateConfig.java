/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.email;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

@org.springframework.context.annotation.Configuration
public class EmailTemplateConfig {

  @Bean
  public FreeMarkerConfigurer freemarkerClassLoaderConfig() {
    Configuration configuration = new Configuration(Configuration.VERSION_2_3_27);
    TemplateLoader templateLoader = new ClassTemplateLoader(this.getClass(), "/emailtemplates");
    configuration.setTemplateLoader(templateLoader);
    FreeMarkerConfigurer freeMarkerConfigurer = new FreeMarkerConfigurer();
    freeMarkerConfigurer.setConfiguration(configuration);
    return freeMarkerConfigurer;
  }

}
