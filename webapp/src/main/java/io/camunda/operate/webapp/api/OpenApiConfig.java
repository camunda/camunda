/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api;

import io.camunda.operate.webapp.security.OperateURIs;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

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
        .addOpenApiCustomizer(openApi ->
            openApi.info(getPublicAPIInfo())
                .getComponents()
                  .addSecuritySchemes("cookie",
                        new SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .in(SecurityScheme.In.COOKIE)
                            .name(OperateURIs.COOKIE_JSESSIONID))
                    .addSecuritySchemes("bearer-key",
                        new SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")))
        .packagesToScan("io.camunda.operate.webapp.api."+version)
        .pathsToMatch("/" + version + "/**")
        .build();
  }

  private Info getPublicAPIInfo() {
    return new Info()
        .title("Operate Public API")
        .version("1.0.0")
        .description("To access active and completed process instances in Operate for monitoring and troubleshooting")
        .contact(new Contact()
            .url("https://www.camunda.com"))
        .license(new License()
            .name("License")
            .url("https://docs.camunda.io/docs/reference/licenses/"));
  }

}
