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

import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

public class ContainerFactory {

  public ElasticsearchContainer createElasticsearchContainer(
      final String imageName, final String imageVersion) {
    return new ElasticsearchContainer(asDockerImageName(imageName, imageVersion));
  }

  public CamundaContainer createCamundaContainer(
      final String imageName, final String imageVersion) {
    return new CamundaContainer(asDockerImageName(imageName, imageVersion));
  }

  public ConnectorsContainer createConnectorsContainer(
      final String imageName, final String imageVersion) {
    return new ConnectorsContainer(asDockerImageName(imageName, imageVersion));
  }

  private static DockerImageName asDockerImageName(
      final String imageName, final String imageVersion) {
    return DockerImageName.parse(imageName).withTag(imageVersion);
  }
}
