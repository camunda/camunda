/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest;

import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("dev")
@Configuration
public class SwaggerConfig {
  @Bean
  public GroupedOpenApi internalAPI() {
    return GroupedOpenApi.builder()
        .group("internal-api")
        .addOpenApiCustomizer(openApi -> openApi.info(getInternalAPIInfo()))
        .pathsToMatch("/api/*")
        .build();
  }

  private Info getInternalAPIInfo() {
    return new Info()
        .title("Operate Internal API")
        .description("For internal use only.")
        .contact(new Contact()
            .url("https://www.camunda.com"))
        .license(new License()
            .name("License")
            .url("https://docs.camunda.io/docs/reference/licenses/"));
  }
}

