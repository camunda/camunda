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
package io.zeebe.test.util.record;

import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.DeploymentRecordValue;
import io.zeebe.exporter.api.record.value.deployment.DeployedWorkflow;
import io.zeebe.exporter.api.record.value.deployment.DeploymentResource;
import java.util.List;
import java.util.stream.Stream;

public class DeploymentRecordStream
    extends ExporterRecordStream<DeploymentRecordValue, DeploymentRecordStream> {

  public DeploymentRecordStream(final Stream<Record<DeploymentRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected DeploymentRecordStream supply(
      final Stream<Record<DeploymentRecordValue>> wrappedStream) {
    return new DeploymentRecordStream(wrappedStream);
  }

  public DeploymentRecordStream withResources(final List<DeploymentResource> resources) {
    return valueFilter(v -> resources.equals(v.getResources()));
  }

  public DeploymentRecordStream withResource(final DeploymentResource resource) {
    return valueFilter(v -> v.getResources().contains(resource));
  }

  public DeploymentRecordStream withDeployedWorkflows(
      final List<DeployedWorkflow> deployedWorkflows) {
    return valueFilter(v -> deployedWorkflows.equals(v.getDeployedWorkflows()));
  }

  public DeploymentRecordStream withDeployedWorkflow(final DeployedWorkflow deployedWorkflow) {
    return valueFilter(v -> v.getDeployedWorkflows().contains(deployedWorkflow));
  }
}
