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
package io.zeebe.client.impl;

import io.grpc.Metadata;
import io.grpc.SecurityLevel;
import io.grpc.Status;
import io.zeebe.client.CredentialsProvider;
import java.io.IOException;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ZeebeCallCredentials extends io.grpc.CallCredentials {
  private static final Logger LOG = LoggerFactory.getLogger(ZeebeCallCredentials.class);

  private final CredentialsProvider credentialsProvider;

  ZeebeCallCredentials(final CredentialsProvider credentialsProvider) {
    this.credentialsProvider = credentialsProvider;
  }

  @Override
  public void applyRequestMetadata(
      final RequestInfo requestInfo, final Executor appExecutor, final MetadataApplier applier) {
    if (requestInfo.getSecurityLevel().ordinal() < SecurityLevel.PRIVACY_AND_INTEGRITY.ordinal()) {
      LOG.warn(
          "The request's security level does not guarantee that the credentials will be confidential.");
    }

    try {
      final Metadata headers = new Metadata();
      credentialsProvider.applyCredentials(headers);
      applier.apply(headers);
    } catch (IOException e) {
      applier.fail(Status.CANCELLED.withCause(e));
    }
  }

  @Override
  public void thisUsesUnstableApi() {
    // This method's purpose is to make it clearer to implementors that the API is unstable.
    // Therefore, it's never called.
  }
}
