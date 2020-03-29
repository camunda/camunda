/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.core;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.discovery.NodeDiscoveryConfig;
import io.atomix.cluster.discovery.NodeDiscoveryProvider;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.ManagedBroadcastService;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.ManagedUnicastService;
import io.atomix.cluster.protocol.GroupMembershipProtocol;
import io.atomix.cluster.protocol.GroupMembershipProtocolConfig;
import io.atomix.core.impl.CoreSerializationService;
import io.atomix.core.profile.Profile;
import io.atomix.core.profile.ProfileConfig;
import io.atomix.core.utils.config.PolymorphicConfigMapper;
import io.atomix.core.utils.config.PolymorphicTypeMapper;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.config.ConfigService;
import io.atomix.primitive.config.PrimitiveConfig;
import io.atomix.primitive.config.impl.DefaultConfigService;
import io.atomix.primitive.impl.DefaultPrimitiveTypeRegistry;
import io.atomix.primitive.partition.ManagedPartitionGroup;
import io.atomix.primitive.partition.ManagedPartitionService;
import io.atomix.primitive.partition.PartitionGroup;
import io.atomix.primitive.partition.PartitionGroupConfig;
import io.atomix.primitive.partition.PartitionService;
import io.atomix.primitive.partition.impl.DefaultPartitionGroupTypeRegistry;
import io.atomix.primitive.partition.impl.DefaultPartitionService;
import io.atomix.primitive.protocol.PrimitiveProtocol;
import io.atomix.primitive.protocol.PrimitiveProtocolConfig;
import io.atomix.primitive.serialization.SerializationService;
import io.atomix.utils.Version;
import io.atomix.utils.concurrent.Futures;
import io.atomix.utils.concurrent.SingleThreadContext;
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.concurrent.Threads;
import io.atomix.utils.config.ConfigMapper;
import io.atomix.utils.config.ConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Primary interface for managing Atomix clusters and operating on distributed primitives.
 *
 * <p>The {@code Atomix} class is the primary interface to all Atomix features. To construct an
 * {@code Atomix} instance, either configure the instance with a configuration file or construct a
 * new instance from an {@link AtomixBuilder}. Builders can be created via various {@link
 * #builder()} methods:
 *
 * <pre>{@code
 * Atomix atomix = Atomix.builder()
 *   .withMemberId("member-1")
 *   .withHost("192.168.10.2")
 *   .build();
 *
 * }</pre>
 *
 * Once an {@code Atomix} instance has been constructed, start the instance by calling {@link
 * #start()}:
 *
 * <pre>{@code
 * atomix.start().join();
 *
 * }</pre>
 *
 * The returned {@link CompletableFuture} will be completed once the node has been bootstrapped and
 * all services are available.
 *
 * <p>The instance can be used to access services for managing the cluster or communicating with
 * other nodes. Additionally, it provides various methods for creating and operating on distributed
 * primitives. Generally, the primitive methods are separated into two types. Primitive getters
 * return multiton instances of a primitive. Primitives created via getters must be pre-configured
 * in the Atomix instance configuration. Alternatively, primitive builders can be used to create and
 * configure primitives in code:
 *
 * <pre>{@code
 * DistributedMap<String, String> map = atomix.mapBuilder("my-map")
 *   .withProtocol(MultiRaftProtocol.builder("raft")
 *     .withReadConsistency(ReadConsistency.SEQUENTIAL)
 *     .build())
 *   .build();
 *
 * }</pre>
 *
 * Custom primitives can be constructed by providing a custom {@link PrimitiveType} and using the
 * {@link #primitiveBuilder(String, PrimitiveType)} method:
 *
 * <pre>{@code
 * MyPrimitive myPrimitive = atomix.primitiveBuilder("my-primitive, MyPrimitiveType.instance())
 *   .withProtocol(MultiRaftProtocol.builder("raft")
 *     .withReadConsistency(ReadConsistency.SEQUENTIAL)
 *     .build())
 *   .build();
 *
 * }</pre>
 */
public class Atomix extends AtomixCluster {

  protected static final Logger LOGGER = LoggerFactory.getLogger(Atomix.class);
  private static final String[] RESOURCES =
      System.getProperty("atomix.config.resources", "atomix").split(",");
  private static final String VERSION_RESOURCE = "VERSION";
  private static final String BUILD;
  private static final Version VERSION;

  static {
    try {
      BUILD =
          Resources.toString(
              checkNotNull(
                  Atomix.class.getClassLoader().getResource(VERSION_RESOURCE),
                  VERSION_RESOURCE + " resource is null"),
              StandardCharsets.UTF_8);
    } catch (final IOException | NullPointerException e) {
      throw new ConfigurationException("Failed to load Atomix version", e);
    }
    VERSION = BUILD.trim().length() > 0 ? Version.from(BUILD.trim().split("\\s+")[0]) : null;
  }

  private final ScheduledExecutorService executorService;
  private final AtomixRegistry registry;
  private final ConfigService config;
  private final SerializationService serializationService;
  private final ManagedPartitionService partitions;
  private final boolean enableShutdownHook;
  private final ThreadContext threadContext = new SingleThreadContext("atomix-%d");
  private volatile Thread shutdownHook = null;

  public Atomix(final String... configFiles) {
    this(Thread.currentThread().getContextClassLoader(), configFiles);
  }

  public Atomix(final ClassLoader classLoader, final String... configFiles) {
    this(classLoader, Stream.of(configFiles).map(File::new).collect(Collectors.toList()));
  }

  public Atomix(final File... configFiles) {
    this(Thread.currentThread().getContextClassLoader(), configFiles);
  }

  public Atomix(final ClassLoader classLoader, final File... configFiles) {
    this(classLoader, Arrays.asList(configFiles));
  }

  public Atomix(final ClassLoader classLoader, final List<File> configFiles) {
    this(
        config(classLoader, configFiles, AtomixRegistry.registry(classLoader)),
        AtomixRegistry.registry(classLoader));
  }

  protected Atomix(final AtomixConfig config, final AtomixRegistry registry) {
    this(config, registry, null, null, null);
  }

  @SuppressWarnings("unchecked")
  protected Atomix(
      final AtomixConfig config,
      final AtomixRegistry registry,
      final ManagedMessagingService messagingService,
      final ManagedUnicastService unicastService,
      final ManagedBroadcastService broadcastService) {
    super(config.getClusterConfig(), VERSION, messagingService, unicastService, broadcastService);
    config
        .getProfiles()
        .forEach(profile -> profile.getType().newProfile(profile).configure(config));
    this.executorService =
        Executors.newScheduledThreadPool(
            Math.max(Math.min(Runtime.getRuntime().availableProcessors() * 2, 8), 4),
            Threads.namedThreads("atomix-primitive-%d", LOGGER));
    this.registry = registry;
    this.config =
        new DefaultConfigService(
            config.getPrimitiveDefaults().values(), config.getPrimitives().values());
    this.serializationService =
        new CoreSerializationService(
            config.isTypeRegistrationRequired(), config.isCompatibleSerialization());
    this.partitions =
        buildPartitionService(config, getMembershipService(), getCommunicationService(), registry);
    this.enableShutdownHook = config.isEnableShutdownHook();
  }

  /**
   * Returns a new Atomix configuration.
   *
   * <p>The configuration will be loaded from {@code atomix.conf}, {@code atomix.json}, or {@code
   * atomix.properties} if located on the classpath.
   *
   * @return a new Atomix configuration
   */
  public static AtomixConfig config() {
    return config(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Returns a new Atomix configuration.
   *
   * <p>The configuration will be loaded from {@code atomix.conf}, {@code atomix.json}, or {@code
   * atomix.properties} if located on the classpath.
   *
   * @param classLoader the class loader
   * @return a new Atomix configuration
   */
  public static AtomixConfig config(final ClassLoader classLoader) {
    return config(classLoader, null, AtomixRegistry.registry(classLoader));
  }

  /**
   * Returns a new Atomix configuration.
   *
   * <p>The configuration will be loaded from {@code atomix.conf}, {@code atomix.json}, or {@code
   * atomix.properties} if located on the classpath.
   *
   * @param registry the Atomix registry
   * @return a new Atomix configuration
   */
  public static AtomixConfig config(final AtomixRegistry registry) {
    return config(Thread.currentThread().getContextClassLoader(), null, registry);
  }

  /**
   * Returns a new Atomix configuration from the given file.
   *
   * <p>The configuration will be loaded from the given file and will fall back to {@code
   * atomix.conf}, {@code atomix.json}, or {@code atomix.properties} if located on the classpath.
   *
   * @param files the file from which to return a new Atomix configuration
   * @return a new Atomix configuration from the given file
   */
  public static AtomixConfig config(final String... files) {
    return config(
        Thread.currentThread().getContextClassLoader(),
        Stream.of(files).map(File::new).collect(Collectors.toList()));
  }

  /**
   * Returns a new Atomix configuration from the given file.
   *
   * <p>The configuration will be loaded from the given file and will fall back to {@code
   * atomix.conf}, {@code atomix.json}, or {@code atomix.properties} if located on the classpath.
   *
   * @param classLoader the class loader
   * @param files the file from which to return a new Atomix configuration
   * @return a new Atomix configuration from the given file
   */
  public static AtomixConfig config(final ClassLoader classLoader, final String... files) {
    return config(
        classLoader,
        Stream.of(files).map(File::new).collect(Collectors.toList()),
        AtomixRegistry.registry(classLoader));
  }

  /**
   * Returns a new Atomix configuration from the given file.
   *
   * <p>The configuration will be loaded from the given file and will fall back to {@code
   * atomix.conf}, {@code atomix.json}, or {@code atomix.properties} if located on the classpath.
   *
   * @param registry the Atomix registry
   * @param files the file from which to return a new Atomix configuration
   * @return a new Atomix configuration from the given file
   */
  public static AtomixConfig config(final AtomixRegistry registry, final String... files) {
    return config(
        Thread.currentThread().getContextClassLoader(),
        Stream.of(files).map(File::new).collect(Collectors.toList()),
        registry);
  }

  /**
   * Returns a new Atomix configuration.
   *
   * <p>The configuration will be loaded from the given file and will fall back to {@code
   * atomix.conf}, {@code atomix.json}, or {@code atomix.properties} if located on the classpath.
   *
   * @param configFiles the Atomix configuration files
   * @return a new Atomix configuration
   */
  public static AtomixConfig config(final File... configFiles) {
    return config(
        Thread.currentThread().getContextClassLoader(),
        Arrays.asList(configFiles),
        AtomixRegistry.registry());
  }

  /**
   * Returns a new Atomix configuration from the given file.
   *
   * <p>The configuration will be loaded from the given file and will fall back to {@code
   * atomix.conf}, {@code atomix.json}, or {@code atomix.properties} if located on the classpath.
   *
   * @param files the file from which to return a new Atomix configuration
   * @return a new Atomix configuration from the given file
   */
  public static AtomixConfig config(final List<File> files) {
    return config(Thread.currentThread().getContextClassLoader(), files);
  }

  /**
   * Returns a new Atomix configuration from the given file.
   *
   * <p>The configuration will be loaded from the given file and will fall back to {@code
   * atomix.conf}, {@code atomix.json}, or {@code atomix.properties} if located on the classpath.
   *
   * @param classLoader the class loader
   * @param files the file from which to return a new Atomix configuration
   * @return a new Atomix configuration from the given file
   */
  public static AtomixConfig config(final ClassLoader classLoader, final List<File> files) {
    return config(classLoader, files, AtomixRegistry.registry(classLoader));
  }

  /**
   * Returns a new Atomix configuration from the given file.
   *
   * <p>The configuration will be loaded from the given file and will fall back to {@code
   * atomix.conf}, {@code atomix.json}, or {@code atomix.properties} if located on the classpath.
   *
   * @param registry the Atomix registry
   * @param files the file from which to return a new Atomix configuration
   * @return a new Atomix configuration from the given file
   */
  public static AtomixConfig config(final AtomixRegistry registry, final List<File> files) {
    return config(Thread.currentThread().getContextClassLoader(), files, registry);
  }

  /**
   * Returns a new Atomix configuration from the given resources.
   *
   * @param classLoader the class loader
   * @param files the files to load
   * @param registry the Atomix registry from which to map types
   * @return a new Atomix configuration from the given resource
   */
  private static AtomixConfig config(
      final ClassLoader classLoader, final List<File> files, final AtomixRegistry registry) {
    final ConfigMapper mapper =
        new PolymorphicConfigMapper(
            classLoader,
            registry,
            new PolymorphicTypeMapper(
                "type", PartitionGroupConfig.class, PartitionGroup.Type.class),
            new PolymorphicTypeMapper("type", PrimitiveConfig.class, PrimitiveType.class),
            new PolymorphicTypeMapper(null, PrimitiveConfig.class, PrimitiveType.class),
            new PolymorphicTypeMapper(
                "type", PrimitiveProtocolConfig.class, PrimitiveProtocol.Type.class),
            new PolymorphicTypeMapper("type", ProfileConfig.class, Profile.Type.class),
            new PolymorphicTypeMapper(
                "type", NodeDiscoveryConfig.class, NodeDiscoveryProvider.Type.class),
            new PolymorphicTypeMapper(
                "type", GroupMembershipProtocolConfig.class, GroupMembershipProtocol.Type.class));
    return mapper.loadFiles(AtomixConfig.class, files, Lists.newArrayList(RESOURCES));
  }

  /**
   * Returns a new Atomix builder.
   *
   * <p>The builder will be initialized with the configuration in {@code atomix.conf}, {@code
   * atomix.json}, or {@code atomix.properties} if located on the classpath.
   *
   * @return a new Atomix builder
   */
  public static AtomixBuilder builder() {
    return builder(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Returns a new Atomix builder.
   *
   * <p>The builder will be initialized with the configuration in {@code atomix.conf}, {@code
   * atomix.json}, or {@code atomix.properties} if located on the classpath.
   *
   * @param classLoader the class loader
   * @return a new Atomix builder
   */
  public static AtomixBuilder builder(final ClassLoader classLoader) {
    final AtomixRegistry registry = AtomixRegistry.registry(classLoader);
    return new AtomixBuilder(config(classLoader, null, registry), registry);
  }

  /**
   * Returns a new Atomix builder.
   *
   * <p>The builder will be initialized with the configuration in the given file and will fall back
   * to {@code atomix.conf}, {@code atomix.json}, or {@code atomix.properties} if located on the
   * classpath.
   *
   * @param registry the AtomixRegistry
   * @return a new Atomix builder
   */
  public static AtomixBuilder builder(final AtomixRegistry registry) {
    return new AtomixBuilder(
        config(Thread.currentThread().getContextClassLoader(), null, registry), registry);
  }

  /**
   * Returns a new Atomix builder.
   *
   * <p>The builder will be initialized with the configuration in the given file and will fall back
   * to {@code atomix.conf}, {@code atomix.json}, or {@code atomix.properties} if located on the
   * classpath.
   *
   * @param config the Atomix configuration
   * @return a new Atomix builder
   */
  public static AtomixBuilder builder(final String config) {
    return builder(config, Thread.currentThread().getContextClassLoader());
  }

  /**
   * Returns a new Atomix builder.
   *
   * <p>The builder will be initialized with the configuration in the given file and will fall back
   * to {@code atomix.conf}, {@code atomix.json}, or {@code atomix.properties} if located on the
   * classpath.
   *
   * @param configFile the Atomix configuration file
   * @param classLoader the class loader
   * @return a new Atomix builder
   */
  public static AtomixBuilder builder(final String configFile, final ClassLoader classLoader) {
    final AtomixRegistry registry = AtomixRegistry.registry(classLoader);
    return new AtomixBuilder(
        config(classLoader, Collections.singletonList(new File(configFile)), registry), registry);
  }

  /**
   * Returns a new Atomix builder.
   *
   * <p>The builder will be initialized with the configuration in the given file and will fall back
   * to {@code atomix.conf}, {@code atomix.json}, or {@code atomix.properties} if located on the
   * classpath.
   *
   * @param configFile the Atomix configuration file
   * @param registry the Atomix registry
   * @return a new Atomix builder
   */
  public static AtomixBuilder builder(final String configFile, final AtomixRegistry registry) {
    return new AtomixBuilder(
        config(
            Thread.currentThread().getContextClassLoader(),
            Collections.singletonList(new File(configFile)),
            registry),
        registry);
  }

  /**
   * Returns a new Atomix builder.
   *
   * <p>The returned builder will be initialized with the provided configuration.
   *
   * @param config the Atomix configuration
   * @return the Atomix builder
   */
  public static AtomixBuilder builder(final AtomixConfig config) {
    return builder(config, Thread.currentThread().getContextClassLoader());
  }

  /**
   * Returns a new Atomix builder.
   *
   * <p>The returned builder will be initialized with the provided configuration.
   *
   * @param config the Atomix configuration
   * @param classLoader the class loader with which to load the Atomix registry
   * @return the Atomix builder
   */
  public static AtomixBuilder builder(final AtomixConfig config, final ClassLoader classLoader) {
    return new AtomixBuilder(config, AtomixRegistry.registry(classLoader));
  }

  /**
   * Returns a new Atomix builder.
   *
   * <p>The returned builder will be initialized with the provided configuration.
   *
   * @param config the Atomix configuration
   * @param registry the Atomix registry
   * @return the Atomix builder
   */
  public static AtomixBuilder builder(final AtomixConfig config, final AtomixRegistry registry) {
    return new AtomixBuilder(config, registry);
  }

  /**
   * Returns the Atomix registry service.
   *
   * <p>The registry contains references to all registered Atomix extensions.
   *
   * @return the Atomix registry service
   */
  public AtomixRegistry getRegistry() {
    return registry;
  }

  /**
   * Returns the core Atomix executor service.
   *
   * @return the core Atomix executor service
   */
  public ScheduledExecutorService getExecutorService() {
    return executorService;
  }

  /**
   * Returns the primitive configuration service.
   *
   * <p>The primitive configuration service provides all pre-defined named primitive configurations.
   *
   * @return the primitive configuration service
   */
  public ConfigService getConfigService() {
    return config;
  }

  /**
   * Returns the primitive serialization service.
   *
   * @return the primitive serialization service
   */
  public SerializationService getSerializationService() {
    return serializationService;
  }

  /**
   * Returns the partition service.
   *
   * <p>The partition service is responsible for managing the lifecycle of primitive partitions and
   * can provide information about active partition groups and partitions in the cluster.
   *
   * @return the partition service
   */
  public PartitionService getPartitionService() {
    return partitions;
  }

  /** Checks that the instance is running. */
  private void checkRunning() {
    checkState(isRunning(), "Atomix instance is not running");
  }

  /**
   * Starts the Atomix instance.
   *
   * <p>The returned future will be completed once this instance completes startup. Note that in
   * order to complete startup, all partitions must be able to form. For Raft partitions, that
   * requires that a majority of the nodes in each partition be started concurrently.
   *
   * @return a future to be completed once the instance has completed startup
   */
  @Override
  public synchronized CompletableFuture<Void> start() {
    if (closeFuture != null) {
      return Futures.exceptionalFuture(
          new IllegalStateException(
              "Atomix instance " + (closeFuture.isDone() ? "shutdown" : "shutting down")));
    }

    LOGGER.info(BUILD);
    return super.start()
        .thenRun(
            () -> {
              if (enableShutdownHook) {
                if (shutdownHook == null) {
                  shutdownHook = new Thread(() -> super.stop().join());
                  Runtime.getRuntime().addShutdownHook(shutdownHook);
                }
              }
            });
  }

  @Override
  protected CompletableFuture<Void> startServices() {
    return super.startServices()
        .thenComposeAsync(v -> partitions.start(), threadContext)
        .thenApply(v -> null);
  }

  @Override
  public synchronized CompletableFuture<Void> stop() {
    if (shutdownHook != null) {
      try {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
        shutdownHook = null;
      } catch (final IllegalStateException e) {
        // JVM shutting down
      }
    }
    return super.stop();
  }

  @Override
  @SuppressWarnings("unchecked")
  protected CompletableFuture<Void> stopServices() {
    return partitions
        .stop()
        .exceptionally(e -> null)
        .thenComposeAsync(v -> super.stopServices(), threadContext);
  }

  @Override
  protected CompletableFuture<Void> completeShutdown() {
    executorService.shutdownNow();
    threadContext.close();
    return super.completeShutdown();
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("partitions", getPartitionService()).toString();
  }

  /** Builds the core partition group. */
  @SuppressWarnings("unchecked")
  private static ManagedPartitionGroup buildSystemPartitionGroup(final AtomixConfig config) {
    final PartitionGroupConfig<?> partitionGroupConfig = config.getManagementGroup();
    if (partitionGroupConfig == null) {
      return null;
    }
    return partitionGroupConfig.getType().newPartitionGroup(partitionGroupConfig);
  }

  /** Builds a partition service. */
  @SuppressWarnings("unchecked")
  private static ManagedPartitionService buildPartitionService(
      final AtomixConfig config,
      final ClusterMembershipService clusterMembershipService,
      final ClusterCommunicationService messagingService,
      final AtomixRegistry registry) {
    final List<ManagedPartitionGroup> partitionGroups = new ArrayList<>();
    for (final PartitionGroupConfig<?> partitionGroupConfig :
        config.getPartitionGroups().values()) {
      partitionGroups.add(partitionGroupConfig.getType().newPartitionGroup(partitionGroupConfig));
    }

    return new DefaultPartitionService(
        clusterMembershipService,
        messagingService,
        new DefaultPrimitiveTypeRegistry(registry.getTypes(PrimitiveType.class)),
        buildSystemPartitionGroup(config),
        partitionGroups,
        new DefaultPartitionGroupTypeRegistry(registry.getTypes(PartitionGroup.Type.class)));
  }
}
