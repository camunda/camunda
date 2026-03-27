/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.container.volume;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateVolumeCmd;
import com.github.dockerjava.api.command.CreateVolumeResponse;
import com.github.dockerjava.api.command.RemoveVolumeCmd;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.SELContext;
import com.github.dockerjava.api.model.Volume;
import io.camunda.container.CamundaContainer;
import io.camunda.container.TinyContainer;
import io.camunda.container.cluster.BrokerNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.ResourceReaper;

public class CamundaVolume implements AutoCloseable {

  private final String name;
  private final DockerClient client;

  public CamundaVolume(final String name, final DockerClient client) {
    this.name = name;
    this.client = client;
  }

  public String getName() {
    return name;
  }

  public static CamundaVolume newCamundaVolume() {
    return newCamundaVolume(UnaryOperator.identity());
  }

  public static CamundaVolume newCamundaVolume(final UnaryOperator<CreateVolumeCmd> configurator) {
    final DockerClient client = DockerClientFactory.instance().client();
    final Map<String, String> labels = new HashMap<>();
    labels.putAll(DockerClientFactory.DEFAULT_LABELS);
    //noinspection deprecation
    labels.putAll(ResourceReaper.instance().getLabels());

    try (final CreateVolumeCmd command = client.createVolumeCmd().withLabels(labels)) {
      final CreateVolumeResponse response = configurator.apply(command).exec();
      return new CamundaVolume(response.getName(), client);
    }
  }

  /**
   * Returns the volume as a bind which can be used when creating new containers.
   *
   * @param mountPath the path where to mount the volume in the container
   * @return a bind which can be used when creating a container
   */
  public Bind asBind(final String mountPath) {
    return new Bind(name, new Volume(mountPath), AccessMode.rw, SELContext.none);
  }

  /**
   * Convenience method which mounts the volume to a Zeebe broker's data folder.
   *
   * @param command the create command of the Zeebe broker container
   */
  public void attachVolumeToContainer(final CreateContainerCmd command) {
    attachVolumeToContainer(command, CamundaContainer.DEFAULT_CAMUNDA_DATA_PATH);
  }

  public void attachVolumeToLegacyContainer(final CreateContainerCmd command) {
    attachVolumeToContainer(command, "/usr/local/zeebe/data");
  }

  /**
   * Convenience method which mounts the volume to a Zeebe broker's data folder.
   *
   * @param command the create command of the Zeebe broker container
   */
  public void attachVolumeToContainer(final CreateContainerCmd command, final String mountPath) {
    final HostConfig hostConfig = Objects.requireNonNull(command.getHostConfig());
    final Bind[] binds = hostConfig.getBinds();
    final Bind[] newBinds = new Bind[binds.length + 1];

    System.arraycopy(binds, 0, newBinds, 0, binds.length);
    newBinds[binds.length] = asBind(mountPath);

    command.withHostConfig(hostConfig.withBinds(newBinds));
  }

  /**
   * Removes the volume from Docker.
   *
   * @throws com.github.dockerjava.api.exception.NotFoundException if no such volume exists
   * @throws com.github.dockerjava.api.exception.ConflictException if the volume is currently in use
   */
  @Override
  public void close() {
    try (final RemoveVolumeCmd command = client.removeVolumeCmd(name)) {
      command.exec();
    }
  }

  /**
   * Convenience method to extract the data from this volume, whether it is or isn't already
   * attached to a container. This will start a tiny container which will only serves to extract the
   * data of this volume.
   *
   * <p>If it's already attached to a container, consider using the {@link
   * ContainerArchive#builder()} directly.
   *
   * @param destination the destination to extract the contents of this volume to
   */
  public void extract(final Path destination) throws IOException, InterruptedException {
    extract(destination, UnaryOperator.identity());
  }

  /**
   * Convenience method to extract the data from this volume, whether it is or isn't already
   * attached to a container. This will start a tiny container which will only serves to extract the
   * data of this volume.
   *
   * <p>If it's already attached to a container, consider using the {@link
   * ContainerArchive#builder()} directly.
   *
   * @param destination the destination to extract the contents of this volume to
   * @param modifier an operator which takes in a pre-configured builder and can modify it
   */
  public void extract(final Path destination, final UnaryOperator<ContainerArchiveBuilder> modifier)
      throws IOException, InterruptedException {
    try (final TinyContainer container = new TinyContainer()) {
      container.withCreateContainerCmdModifier(this::attachVolumeToContainer);
      container.start();
      /* Replace Busybox's TAR pkg with GNU one */
      container.execInContainer("apk", "add", "tar");
      final ContainerArchiveBuilder builder = ContainerArchive.builder().withContainer(container);
      final ContainerArchive archive = modifier.apply(builder).build();

      archive.extract(destination);
    }
  }

  /** Returns a new default managed volume */
  public static CamundaVolume newVolume() {
    return newVolume(UnaryOperator.identity());
  }

  /**
   * @param configurator a function which can optionally configure more of the volume
   * @return a new managed volume using the given Docker client to create it
   */
  public static CamundaVolume newVolume(final UnaryOperator<CreateVolumeCmd> configurator) {
    final DockerClient client = DockerClientFactory.instance().client();
    final Map<String, String> labels = new HashMap<>();
    labels.putAll(DockerClientFactory.DEFAULT_LABELS);
    //noinspection deprecation
    labels.putAll(ResourceReaper.instance().getLabels());

    try (final CreateVolumeCmd command = client.createVolumeCmd().withLabels(labels)) {
      final CreateVolumeResponse response = configurator.apply(command).exec();
      return new CamundaVolume(response.getName(), client);
    }
  }

  public <T extends GenericContainer<T> & BrokerNode<T>> void attach(final T container) {
    container.withCreateContainerCmdModifier(this::attachVolumeToContainer);
  }

  public <T extends GenericContainer<T> & BrokerNode<T>> void attachZeebe(final T container) {
    container.withCreateContainerCmdModifier(this::attachVolumeToLegacyContainer);
  }
}
