/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate;

import io.camunda.operate.data.DataGenerator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.core.env.ConfigurableEnvironment;

@SpringBootApplication
@ComponentScan(
    basePackages = "io.camunda.operate",
    excludeFilters = {
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.operate\\.zeebeimport\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.operate\\.webapp\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.operate\\.archiver\\..*")
    },
    // use fully qualified names as bean name, as we have classes with same names for different
    // versions of importer
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@EnableAutoConfiguration
public class Application {

  public static final String OPERATE_STATIC_RESOURCES_LOCATION =
      "classpath:/META-INF/resources/operate/";
  public static final String SPRING_THYMELEAF_PREFIX_KEY = "spring.thymeleaf.prefix";
  public static final String SPRING_THYMELEAF_PREFIX_VALUE = OPERATE_STATIC_RESOURCES_LOCATION;
  private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

  public static void main(final String[] args) {

    // To ensure that debug logging performed using java.util.logging is routed into Log4j 2
    System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    // Workaround for https://github.com/spring-projects/spring-boot/issues/26627
    System.setProperty(
        "spring.config.location",
        "optional:classpath:/,optional:classpath:/config/,optional:file:./,optional:file:./config/");
    // Hack for the moment to allow serving static resources in Operate.
    // Must be removed with the single application.
    System.setProperty("spring.web.resources.add-mappings", "true");
    System.setProperty("spring.web.resources.static-locations", OPERATE_STATIC_RESOURCES_LOCATION);
    final SpringApplication springApplication = new SpringApplication(Application.class);
    springApplication.setAddCommandLineProperties(true);
    springApplication.addListeners(new ApplicationErrorListener());
    setDefaultProperties(springApplication);
    setDefaultAuthProfile(springApplication);
    springApplication.run(args);
  }

  private static void setDefaultAuthProfile(final SpringApplication springApplication) {
    springApplication.addInitializers(
        configurableApplicationContext -> {
          final ConfigurableEnvironment env = configurableApplicationContext.getEnvironment();
          final Set<String> activeProfiles = Set.of(env.getActiveProfiles());
          if (OperateProfileService.AUTH_PROFILES.stream().noneMatch(activeProfiles::contains)) {
            env.addActiveProfile(OperateProfileService.DEFAULT_AUTH);
          }
        });
  }

  private static void setDefaultProperties(final SpringApplication springApplication) {
    final Map<String, Object> defaultsProperties = new HashMap<>();
    defaultsProperties.putAll(getWebProperties());
    defaultsProperties.putAll(getManagementProperties());
    springApplication.setDefaultProperties(defaultsProperties);
  }

  private static Map<String, Object> getWebProperties() {
    return Map.of(
        "server.servlet.session.cookie.name",
        "OPERATE-SESSION",
        "spring.thymeleaf.check-template-location",
        "true",
        SPRING_THYMELEAF_PREFIX_KEY,
        SPRING_THYMELEAF_PREFIX_VALUE,
        "spring.mvc.pathmatch.matching-strategy",
        "ANT_PATH_MATCHER",
        // Return error messages for all endpoints by default, except for Internal API.
        // Internal API error handling is defined in InternalAPIErrorController.
        "server.error.include-message",
        "always");
  }

  public static Map<String, Object> getManagementProperties() {
    return Map.of(
        // disable default health indicators:
        // https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-health-indicators
        "management.health.defaults.enabled", "false",

        // enable Kubernetes health groups:
        // https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-kubernetes-probes
        "management.endpoint.health.probes.enabled", "true",

        // enable health check and metrics endpoints
        "management.endpoints.web.exposure.include",
            "health, prometheus, loggers, usage-metrics, backups",

        // add custom check to standard readiness check
        "management.endpoint.health.group.readiness.include", "readinessState,indicesCheck");
  }

  @Bean(name = "dataGenerator")
  @ConditionalOnMissingBean
  public DataGenerator stubDataGenerator() {
    LOGGER.debug("Create Data generator stub");
    return DataGenerator.DO_NOTHING;
  }

  public static class ApplicationErrorListener
      implements ApplicationListener<ApplicationFailedEvent> {

    @Override
    public void onApplicationEvent(final ApplicationFailedEvent event) {
      event.getApplicationContext().close();
      System.exit(-1);
    }
  }
}
