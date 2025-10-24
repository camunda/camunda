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
package io.camunda.client;

/**
 * @deprecated since 8.8 for removal in 8.10, replaced by {@link ClientProperties}. Please see the
 *     <a
 *     href="https://docs.camunda.io/docs/8.8/apis-tools/migration-manuals/migrate-to-camunda-java-client/">Camunda
 *     Java Client migration guide</a>.
 */
@Deprecated
public final class LegacyZeebeClientProperties {

  public static final String APPLY_ENVIRONMENT_VARIABLES_OVERRIDES =
      "zeebe.client.applyEnvironmentVariableOverrides";

  @Deprecated public static final String GATEWAY_ADDRESS = "zeebe.client.gateway.address";

  public static final String PREFER_REST_OVER_GRPC = "zeebe.client.gateway.preferRestOverGrpc";

  public static final String REST_ADDRESS = "zeebe.client.gateway.rest.address";

  public static final String GRPC_ADDRESS = "zeebe.client.gateway.grpc.address";

  public static final String DEFAULT_TENANT_ID = "zeebe.client.tenantId";

  public static final String DEFAULT_JOB_WORKER_TENANT_IDS = "zeebe.client.worker.tenantIds";

  public static final String JOB_WORKER_EXECUTION_THREADS = "zeebe.client.worker.threads";

  public static final String JOB_WORKER_MAX_JOBS_ACTIVE = "zeebe.client.worker.maxJobsActive";

  public static final String DEFAULT_JOB_WORKER_NAME = "zeebe.client.worker.name";

  public static final String DEFAULT_JOB_TIMEOUT = "zeebe.client.job.timeout";

  public static final String DEFAULT_JOB_POLL_INTERVAL = "zeebe.client.job.pollinterval";

  public static final String DEFAULT_MESSAGE_TIME_TO_LIVE = "zeebe.client.message.timeToLive";

  public static final String DEFAULT_REQUEST_TIMEOUT = "zeebe.client.requestTimeout";

  public static final String DEFAULT_REQUEST_TIMEOUT_OFFSET = "camunda.client.requestTimeoutOffset";

  public static final String USE_PLAINTEXT_CONNECTION = "zeebe.client.security.plaintext";

  public static final String CA_CERTIFICATE_PATH = "zeebe.client.security.certpath";

  public static final String KEEP_ALIVE = "zeebe.client.keepalive";

  public static final String OVERRIDE_AUTHORITY = "zeebe.client.overrideauthority";

  public static final String MAX_MESSAGE_SIZE = "zeebe.client.maxMessageSize";

  public static final String MAX_METADATA_SIZE = "zeebe.client.maxMetadataSize";

  public static final String CLOUD_CLUSTER_ID = "zeebe.client.cloud.clusterId";

  public static final String CLOUD_CLIENT_ID = "zeebe.client.cloud.clientId";

  public static final String CLOUD_CLIENT_SECRET = "zeebe.client.cloud.secret";

  public static final String CLOUD_REGION = "zeebe.client.cloud.region";

  public static final String STREAM_ENABLED = "zeebe.client.worker.stream.enabled";

  public static final String USE_DEFAULT_RETRY_POLICY = "zeebe.client.useDefaultRetryPolicy";

  private LegacyZeebeClientProperties() {}
}
