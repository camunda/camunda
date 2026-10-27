/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.container.cluster;

import io.camunda.container.volume.CamundaVolume;
import org.testcontainers.containers.GenericContainer;

public interface BrokerNode<T extends GenericContainer<T> & BrokerNode<T>> extends ClusterNode<T> {

  /**
   * Returns the address to access the command API of this node from within the same container
   * network as this node's.
   *
   * @return the internal command address
   */
  default String getInternalCommandAddress() {
    return getInternalAddress(CamundaPort.COMMAND.getPort());
  }

  /**
   * Returns the address the command API of this broker outside its container network.
   *
   * @return the external command API address
   */
  default String getExternalCommandAddress() {
    return getExternalAddress(CamundaPort.COMMAND.getPort());
  }

  /**
   * Allows reuse of the broker data across restarts by attaching the data folder of the broker to a
   * {@link CamundaVolume}.
   *
   * <p>NOTE: the container itself does not manage the given resource, so you should keep track of
   * it and close it if need be. In the case of {@link CamundaVolume}, the implementation is aware
   * of the Testcontainers resource reaper, such that if your JVM crashes, the volume will
   * eventually be reaped anyway.
   *
   * <p>For example, if you want to test updating a broker, you could do the following:
   *
   * <pre>{@code
   * final DockerImageName oldImage = DockerImageName.parse("camunda/camunda:8.7.0");
   * final DockerImageName newImage = DockerImageName.parse("camunda/camunda:8.8.0");
   * final CamundaVolume volume = new CamundaVolume();
   * final BrokerContainer broker = new BrokerContainer(oldImage)
   *    .withCamundaData(volume);
   *
   * // do stuff on the broker, then stop it
   * broker.stop();
   * broker.setDockerImage(newImage);
   * broker.start();
   *
   * // verify state is correct after update
   *
   * }</pre>
   *
   * @param data the data implementation to use
   * @return this container for chaining
   */
  default T withCamundaData(final CamundaVolume data) {
    data.attach(self());
    return self();
  }

  /**
   * Attach the volume to the legacy /usr/local/zeebe/data directory, used from the standalone Zeebe
   * images (camunda/zeebe)
   *
   * @param data the data implementation to use
   * @return this container for chaining
   */
  default T withZeebeData(final CamundaVolume data) {
    data.attachZeebe(self());
    return self();
  }
}
