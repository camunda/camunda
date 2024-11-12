/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.post.PostImportAction;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
@Configuration
public class ZeebePostImporter {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebePostImporter.class);

  @Autowired
  @Qualifier("postImportThreadPoolScheduler")
  private ThreadPoolTaskScheduler postImportScheduler;

  @Autowired private PartitionHolder partitionHolder;

  @Autowired private BeanFactory beanFactory;

  private final List<PostImportAction> postImportActions = new ArrayList<>();

  public void initPostImporters() {
    postImportActions.clear();
    partitionHolder.getPartitionIds().stream()
        .forEach(
            p -> {
              final PostImportAction postImportAction =
                  beanFactory.getBean(PostImportAction.class, p);
              postImportActions.add(postImportAction);
            });
  }

  public void start() {
    LOGGER.info("INIT: Init post importers...");
    initPostImporters();
    postImportActions.forEach(action -> postImportScheduler.submit(action));
  }

  public List<PostImportAction> getPostImportActions() {
    return postImportActions;
  }
}
