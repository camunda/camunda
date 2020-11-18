/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.testcontainers;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateVolumeCmd;
import com.github.dockerjava.api.command.CreateVolumeResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.testcontainers.DockerClientFactory;

/**
 * A simple wrapper to create Docker volumes which are managed by Testcontainers. The created object
 * labels the volumes with {@link DockerClientFactory#DEFAULT_LABELS} so that the Ryuk container can
 * reap the volumes should our JVM process crash.
 */
public final class ManagedVolume {

  @SuppressWarnings("java:S1075")
  private static final String DEFAULT_ZEEBE_DATA_PATH = "/usr/local/zeebe/data";

  private final String name;

  /** @param name the name of the volume */
  public ManagedVolume(final String name) {
    this.name = name;
  }

  /** @return the name of the volume */
  public String getName() {
    return name;
  }

  /**
   * Returns the volume as a bind which can be used when creating new containers. For example:
   * <code>
   *   new GenericContainer("containerImage").withCreatCmdModifier(cmd -> cmd.withHostConfig(
   *      cmd.getHostConfig().withBinds(managedVolume.asBind("/path/to/mount"))));
   * </code>
   *
   * @param mountPath the path where to mount the volume in the container
   * @return a bind which can be used when creating a container
   */
  public Bind asBind(final String mountPath) {
    return Bind.parse(name + ":" + mountPath);
  }

  /**
   * @return a pre-configured {@link Bind} which mounts this volume to the data folder of a Zeebe *
   *     broker.
   */
  public Bind asZeebeBind() {
    return asBind(DEFAULT_ZEEBE_DATA_PATH);
  }

  /**
   * Convenience method which mounts the volume to a Zeebe broker's data folder.
   *
   * @param command the create command of the Zeebe broker container
   */
  public void attachVolumeToContainer(final CreateContainerCmd command) {
    final HostConfig hostConfig =
        Objects.requireNonNull(command.getHostConfig()).withBinds(asZeebeBind());
    command.withHostConfig(hostConfig);
  }

  /** @return a new default managed volume */
  public static ManagedVolume newVolume() {
    return newVolume(UnaryOperator.identity());
  }

  /**
   * @param configurator a function which can optionally configure more of the volume
   * @return a new managed volume using the given Docker client to create it
   */
  public static ManagedVolume newVolume(final UnaryOperator<CreateVolumeCmd> configurator) {
    final DockerClient client = DockerClientFactory.instance().client();
    try (final CreateVolumeCmd command = client.createVolumeCmd()) {
      final CreateVolumeResponse response =
          configurator.apply(command.withLabels(DockerClientFactory.DEFAULT_LABELS)).exec();
      return new ManagedVolume(response.getName());
    }
  }
}
