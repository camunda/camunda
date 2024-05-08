package io.camunda.tasklist;

import graphql.kickstart.autoconfigure.annotations.GraphQLAnnotationsAutoConfiguration;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.gateway.Gateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.context.annotation.Profile;

/**
 * Entry point for the Tasklist modules by using the the {@link
 * io.camunda.application.Profile#TASKLIST} profile, so that the appropriate Tasklist application
 * properties are applied.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = "io.camunda.tasklist",
    excludeFilters = {
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.tasklist\\.zeebeimport\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.tasklist\\.webapp\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.tasklist\\.archiver\\..*")
    },
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@EnableAutoConfiguration(
    exclude = {
      ElasticsearchClientAutoConfiguration.class,
      GraphQLAnnotationsAutoConfiguration.class
    })
@Profile("tasklist")
public class TasklistModuleConfiguration {

  // if present, then it will ensure
  // that the broker is started first
  @Autowired(required = false)
  private Broker broker;

  // if present, then it will ensure
  // that the gateway is started first
  @Autowired(required = false)
  private Gateway gateway;
}
