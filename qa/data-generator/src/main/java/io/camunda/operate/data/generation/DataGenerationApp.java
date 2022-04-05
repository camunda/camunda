/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.data.generation;

import io.camunda.zeebe.client.ZeebeClient;
import java.util.concurrent.ThreadFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootApplication
public class DataGenerationApp implements CommandLineRunner {

  @Autowired
  private DataGenerator dataGenerator;

  @Autowired
  private ResultChecker resultChecker;

  @Autowired
  private Connector connector;

  public static void main(String[] args) {
    SpringApplication.run(DataGenerationApp.class, args);
  }

  @Override
  public void run(String... args) throws Exception {
    dataGenerator.createData();
    resultChecker.assertResults();
    System.exit(0);
  }

  @Bean("dataGeneratorThreadPoolExecutor")
  public ThreadPoolTaskExecutor getDataGeneratorTaskExecutor(DataGeneratorProperties dataGeneratorProperties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadFactory(getThreadFactory());
    executor.setCorePoolSize(dataGeneratorProperties.getThreadCount());
    executor.setMaxPoolSize(dataGeneratorProperties.getThreadCount());
    executor.setQueueCapacity(1);
//    executor.setThreadNamePrefix("data_generator_");
    executor.initialize();
    return executor;
  }

  @Bean
  public ThreadFactory getThreadFactory() {
    return new CustomizableThreadFactory("data_generator_") {
      @Override
      public Thread newThread(final Runnable runnable) {
        Thread thread = new DataGeneratorThread(this.getThreadGroup(), runnable,
            this.nextThreadName(), connector.createZeebeClient());
        thread.setPriority(this.getThreadPriority());
        thread.setDaemon(this.isDaemon());
        return thread;
      }
    };
  }

  public class DataGeneratorThread extends Thread {

    private ZeebeClient zeebeClient;

    public DataGeneratorThread(final ZeebeClient zeebeClient) {
      this.zeebeClient = zeebeClient;
    }

    public DataGeneratorThread(final Runnable target, final ZeebeClient zeebeClient) {
      super(target);
      this.zeebeClient = zeebeClient;
    }

    public DataGeneratorThread(final ThreadGroup group,
        final Runnable target, final ZeebeClient zeebeClient) {
      super(group, target);
      this.zeebeClient = zeebeClient;
    }

    public DataGeneratorThread(final String name,
        final ZeebeClient zeebeClient) {
      super(name);
      this.zeebeClient = zeebeClient;
    }

    public DataGeneratorThread(final ThreadGroup group,
        final String name, final ZeebeClient zeebeClient) {
      super(group, name);
      this.zeebeClient = zeebeClient;
    }

    public DataGeneratorThread(final Runnable target, final String name,
        final ZeebeClient zeebeClient) {
      super(target, name);
      this.zeebeClient = zeebeClient;
    }

    public DataGeneratorThread(final ThreadGroup group,
        final Runnable target, final String name,
        final ZeebeClient zeebeClient) {
      super(group, target, name);
      this.zeebeClient = zeebeClient;
    }

    public DataGeneratorThread(final ThreadGroup group,
        final Runnable target, final String name, final long stackSize,
        final ZeebeClient zeebeClient) {
      super(group, target, name, stackSize);
      this.zeebeClient = zeebeClient;
    }

    public DataGeneratorThread(final ThreadGroup group, final Runnable target, final String name,
        final long stackSize,
        final boolean inheritThreadLocals, final ZeebeClient zeebeClient) {
      super(group, target, name, stackSize, inheritThreadLocals);
      this.zeebeClient = zeebeClient;
    }

    public ZeebeClient getZeebeClient() {
      return zeebeClient;
    }
  }

}
