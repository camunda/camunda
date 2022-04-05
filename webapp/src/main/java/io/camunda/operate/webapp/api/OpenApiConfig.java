/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  /**
   * Describes the OpenAPI representation of the whole Operate API (all versions)
   * @return an OpenAPI description built from all available GroupedOpenApi descriptions
   */
  @Bean
  public OpenAPI operateAPI(){
    return  new OpenAPI().info(
        new Info()
            .title("Operate API")
            .description("API for Operate data that can be accessed.")
            .contact(new Contact()
                .url("https://www.camunda.com"))
            .license(new License()
                .name("Camunda Operate License")
                .url("https://camunda.com/legal/terms/cloud-terms-and-conditions/general-terms-and-conditions-for-the-operate-trial-version/"))
        );
  }

  /**
   * Describes and generates one version of Operate API. In this case version 1
   * @return description for API version 1
   */
  @Bean
  public GroupedOpenApi apiV1() {
    return apiDefinitionFor("v1");
  }

  private GroupedOpenApi apiDefinitionFor(final String version) {
    return GroupedOpenApi.builder()
        .group(version)
        .packagesToScan("io.camunda.operate.webapp.api."+version)
        .pathsToMatch("/" + version + "/**")
        .build();
  }

}
