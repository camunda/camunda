/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.system;

import io.zeebe.broker.Broker;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.system.configuration.*;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.impl.ServiceContainerImpl;
import io.zeebe.util.metrics.MetricsManager;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;

public class SystemContext implements AutoCloseable {
  public static final Logger LOG = Loggers.SYSTEM_LOGGER;
  public static final String BROKER_ID_LOG_PROPERTY = "broker-id";
  public static final int CLOSE_TIMEOUT = 10;

  protected ServiceContainer serviceContainer;

  protected final List<Component> components = new ArrayList<>();

  protected final BrokerCfg brokerCfg;

  protected final List<ActorFuture<?>> requiredStartActions = new ArrayList<>();

  protected Map<String, String> diagnosticContext;
  protected ActorScheduler scheduler;

  private MetricsManager metricsManager;

  public SystemContext(String configFileLocation, String basePath, ActorClock clock) {
    if (!Paths.get(configFileLocation).isAbsolute()) {
      configFileLocation =
          Paths.get(basePath, configFileLocation).normalize().toAbsolutePath().toString();
    }

    brokerCfg = new TomlConfigurationReader().read(configFileLocation);

    initSystemContext(clock, basePath);
  }

  public SystemContext(InputStream configStream, String basePath, ActorClock clock) {
    brokerCfg = new TomlConfigurationReader().read(configStream);

    initSystemContext(clock, basePath);
  }

  public SystemContext(BrokerCfg brokerCfg, String basePath, ActorClock clock) {
    this.brokerCfg = brokerCfg;

    initSystemContext(clock, basePath);
  }

  private void initSystemContext(ActorClock clock, String basePath) {
    LOG.debug("Initializing configuration with base path {}", basePath);

    brokerCfg.init(basePath);

    final SocketBindingCfg clientApiCfg = brokerCfg.getNetwork().getClient();
    final String brokerId = String.format("%s:%d", clientApiCfg.getHost(), clientApiCfg.getPort());

    this.diagnosticContext = Collections.singletonMap(BROKER_ID_LOG_PROPERTY, brokerId);

    // TODO: submit diagnosticContext to actor scheduler once supported
    this.metricsManager = initMetricsManager(brokerId);
    this.scheduler = initScheduler(clock, brokerId);
    this.serviceContainer = new ServiceContainerImpl(this.scheduler);
    this.scheduler.start();

    initBrokerInfoMetric();
  }

  private MetricsManager initMetricsManager(String brokerId) {
    final Map<String, String> globalLabels = new HashMap<>();
    globalLabels.put("cluster", "zeebe");
    globalLabels.put("node", brokerId);
    return new MetricsManager("zb_", globalLabels);
  }

  private void initBrokerInfoMetric() {
    // one-shot metric to submit metadata
    metricsManager
        .newMetric("broker_info")
        .type("counter")
        .label("version", Broker.VERSION)
        .create()
        .incrementOrdered();
  }

  private ActorScheduler initScheduler(ActorClock clock, String brokerId) {
    final ThreadsCfg cfg = brokerCfg.getThreads();

    final int cpuThreads = cfg.getCpuThreadCount();
    final int ioThreads = cfg.getIoThreadCount();

    Loggers.SYSTEM_LOGGER.info(
        "Scheduler configuration: Threads{cpu-bound: {}, io-bound: {}}.", cpuThreads, ioThreads);

    return ActorScheduler.newActorScheduler()
        .setActorClock(clock)
        .setMetricsManager(metricsManager)
        .setCpuBoundActorThreadCount(cpuThreads)
        .setIoBoundActorThreadCount(ioThreads)
        .setSchedulerName(brokerId)
        .build();
  }

  public ActorScheduler getScheduler() {
    return scheduler;
  }

  public ServiceContainer getServiceContainer() {
    return serviceContainer;
  }

  public void addComponent(Component component) {
    this.components.add(component);
  }

  public List<Component> getComponents() {
    return components;
  }

  public void init() {
    serviceContainer.start();

    for (Component brokerComponent : components) {
      try {
        brokerComponent.init(this);
      } catch (RuntimeException e) {
        close();
        throw e;
      }
    }

    try {
      for (ActorFuture<?> requiredStartAction : requiredStartActions) {
        requiredStartAction.get(20, TimeUnit.SECONDS);
      }
    } catch (Exception e) {
      LOG.error("Could not start broker", e);
      close();
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    LOG.info("Closing...");

    try {
      serviceContainer.close(CLOSE_TIMEOUT, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      LOG.error("Failed to close broker within {} seconds.", CLOSE_TIMEOUT, e);
    } catch (ExecutionException | InterruptedException e) {
      LOG.error("Exception while closing broker", e);
    } finally {
      try {
        scheduler.stop().get(CLOSE_TIMEOUT, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        LOG.error("Failed to close scheduler within {} seconds", CLOSE_TIMEOUT, e);
      } catch (ExecutionException | InterruptedException e) {
        LOG.error("Exception while closing scheduler", e);
      }
    }
  }

  public BrokerCfg getBrokerConfiguration() {
    return brokerCfg;
  }

  public void addRequiredStartAction(ActorFuture<?> future) {
    requiredStartActions.add(future);
  }

  public Map<String, String> getDiagnosticContext() {
    return diagnosticContext;
  }
}
