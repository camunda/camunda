/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.post.IncidentPostImportAction;
import io.camunda.operate.zeebeimport.post.PostImportAction;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
@Configuration
public class ZeebePostImporter {

  private static final Logger logger = LoggerFactory.getLogger(ZeebePostImporter.class);

  @Autowired
  @Qualifier("postImportThreadPoolScheduler")
  private ThreadPoolTaskScheduler postImportScheduler;

  @Autowired
  private PartitionHolder partitionHolder;

  @Autowired
  private BeanFactory beanFactory;

  private List<PostImportAction> postImportActions = new ArrayList<>();

  public void initPostImporters() {
    postImportActions.clear();
    partitionHolder.getPartitionIds().stream().forEach(p ->
        {
          final PostImportAction postImportAction = beanFactory
              .getBean(IncidentPostImportAction.class, p);
          postImportActions.add(postImportAction);
        }
    );
  }

  public void start() {
    logger.info("INIT: Init post importers...");
    initPostImporters();
    postImportActions.forEach(action -> postImportScheduler.submit(action));
  }

  public List<PostImportAction> getPostImportActions() {
    return postImportActions;
  }

  @Bean("postImportThreadPoolScheduler")
  public ThreadPoolTaskScheduler getPostImportTaskScheduler(OperateProperties operateProperties) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(operateProperties.getImporter().getPostImportThreadsCount());
    scheduler.setThreadNamePrefix("postimport_");
    scheduler.initialize();
    return scheduler;
  }


}
