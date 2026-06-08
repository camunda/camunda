/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.impl.containers;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.images.RemoteDockerImage;

class ContainerFactoryTest {

  private final ContainerFactory containerFactory = new ContainerFactory();

  @Test
  void shouldAlwaysPullSnapshotAndLatestTags() {
    assertThat(ContainerFactory.shouldAlwaysPullImage("SNAPSHOT")).isTrue();
    assertThat(ContainerFactory.shouldAlwaysPullImage("8.9.0-SNAPSHOT")).isTrue();
    assertThat(ContainerFactory.shouldAlwaysPullImage("latest")).isTrue();
    assertThat(ContainerFactory.shouldAlwaysPullImage("8.9-latest")).isTrue();
    assertThat(ContainerFactory.shouldAlwaysPullImage("8.9.0")).isFalse();
  }

  @Test
  void shouldConfigureAlwaysPullPolicyForSnapshotImages() throws ReflectiveOperationException {
    final CamundaContainer container =
        containerFactory.createCamundaContainer("camunda/camunda", "8.9.0-SNAPSHOT");

    assertThat(getImagePullPolicy(container)).isInstanceOf(PullPolicy.alwaysPull().getClass());
  }

  @Test
  void shouldConfigureDefaultPullPolicyForNonSnapshotImages() throws ReflectiveOperationException {
    final ConnectorsContainer container =
        containerFactory.createConnectorsContainer("camunda/connectors-bundle", "8.9.0");

    assertThat(getImagePullPolicy(container)).isInstanceOf(PullPolicy.defaultPolicy().getClass());
  }

  private static ImagePullPolicy getImagePullPolicy(final GenericContainer<?> container)
      throws ReflectiveOperationException {
    final Field imageField = GenericContainer.class.getDeclaredField("image");
    imageField.setAccessible(true);
    final RemoteDockerImage remoteDockerImage = (RemoteDockerImage) imageField.get(container);

    final Field imagePullPolicyField = RemoteDockerImage.class.getDeclaredField("imagePullPolicy");
    imagePullPolicyField.setAccessible(true);
    return (ImagePullPolicy) imagePullPolicyField.get(remoteDockerImage);
  }
}
