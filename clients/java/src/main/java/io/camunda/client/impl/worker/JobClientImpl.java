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
package io.camunda.client.impl.worker;

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider.StatusCode;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.ActivateJobsCommandStep1;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.CreateDocumentBatchCommandStep1;
import io.camunda.client.api.command.CreateDocumentCommandStep1;
import io.camunda.client.api.command.CreateDocumentLinkCommandStep1;
import io.camunda.client.api.command.DeleteDocumentCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1;
import io.camunda.client.api.command.StreamJobsCommandStep1;
import io.camunda.client.api.command.ThrowErrorCommandStep1;
import io.camunda.client.api.fetch.DocumentContentGetRequest;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.impl.command.ActivateJobsCommandImpl;
import io.camunda.client.impl.command.CompleteJobCommandImpl;
import io.camunda.client.impl.command.CreateDocumentBatchCommandImpl;
import io.camunda.client.impl.command.CreateDocumentCommandImpl;
import io.camunda.client.impl.command.CreateDocumentLinkCommandImpl;
import io.camunda.client.impl.command.DeleteDocumentCommandImpl;
import io.camunda.client.impl.command.FailJobCommandImpl;
import io.camunda.client.impl.command.StreamJobsCommandImpl;
import io.camunda.client.impl.command.ThrowErrorCommandImpl;
import io.camunda.client.impl.fetch.DocumentContentGetRequestImpl;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import java.util.function.Predicate;

public final class JobClientImpl implements JobClient {

  private final GatewayStub asyncStub;
  private final HttpClient httpClient;
  private final CamundaClientConfiguration config;
  private final JsonMapper jsonMapper;
  private final Predicate<StatusCode> retryPredicate;

  public JobClientImpl(
      final GatewayStub asyncStub,
      final HttpClient httpClient,
      final CamundaClientConfiguration config,
      final JsonMapper jsonMapper,
      final Predicate<StatusCode> retryPredicate) {
    this.asyncStub = asyncStub;
    this.httpClient = httpClient;
    this.config = config;
    this.jsonMapper = jsonMapper;
    this.retryPredicate = retryPredicate;
  }

  @Override
  public CamundaClientConfiguration getConfiguration() {
    return config;
  }

  @Override
  public CompleteJobCommandStep1 newCompleteCommand(final long jobKey) {
    return new CompleteJobCommandImpl(
        asyncStub,
        jsonMapper,
        jobKey,
        config.getDefaultRequestTimeout(),
        retryPredicate,
        httpClient,
        config.preferRestOverGrpc());
  }

  @Override
  public CompleteJobCommandStep1 newCompleteCommand(final ActivatedJob job) {
    return newCompleteCommand(job.getKey());
  }

  @Override
  public FailJobCommandStep1 newFailCommand(final long jobKey) {
    return new FailJobCommandImpl(
        asyncStub,
        jsonMapper,
        jobKey,
        config.getDefaultRequestTimeout(),
        retryPredicate,
        httpClient,
        config.preferRestOverGrpc());
  }

  @Override
  public FailJobCommandStep1 newFailCommand(final ActivatedJob job) {
    return newFailCommand(job.getKey());
  }

  @Override
  public ThrowErrorCommandStep1 newThrowErrorCommand(final long jobKey) {
    return new ThrowErrorCommandImpl(
        asyncStub,
        jsonMapper,
        jobKey,
        config.getDefaultRequestTimeout(),
        retryPredicate,
        httpClient,
        config.preferRestOverGrpc());
  }

  @Override
  public ThrowErrorCommandStep1 newThrowErrorCommand(final ActivatedJob job) {
    return newThrowErrorCommand(job.getKey());
  }

  @Override
  public ActivateJobsCommandStep1 newActivateJobsCommand() {
    return new ActivateJobsCommandImpl(asyncStub, httpClient, config, jsonMapper, retryPredicate);
  }

  @Override
  public StreamJobsCommandStep1 newStreamJobsCommand() {
    return new StreamJobsCommandImpl(asyncStub, jsonMapper, retryPredicate, config);
  }

  @Override
  public CreateDocumentCommandStep1 newCreateDocumentCommand() {
    return new CreateDocumentCommandImpl(jsonMapper, httpClient, config);
  }

  @Override
  public CreateDocumentBatchCommandStep1 newCreateDocumentBatchCommand() {
    return new CreateDocumentBatchCommandImpl(jsonMapper, httpClient, config);
  }

  @Override
  public DocumentContentGetRequest newDocumentContentGetRequest(final String documentId) {
    return new DocumentContentGetRequestImpl(httpClient, documentId, null, null, config);
  }

  @Override
  public DocumentContentGetRequest newDocumentContentGetRequest(
      final DocumentReferenceResponse documentReference) {
    return new DocumentContentGetRequestImpl(
        httpClient,
        documentReference.getDocumentId(),
        documentReference.getStoreId(),
        documentReference.getContentHash(),
        config);
  }

  @Override
  public CreateDocumentLinkCommandStep1 newCreateDocumentLinkCommand(final String documentId) {
    return new CreateDocumentLinkCommandImpl(
        documentId, null, null, jsonMapper, httpClient, config);
  }

  @Override
  public CreateDocumentLinkCommandStep1 newCreateDocumentLinkCommand(
      final DocumentReferenceResponse documentReference) {
    return new CreateDocumentLinkCommandImpl(
        documentReference.getDocumentId(),
        documentReference.getStoreId(),
        documentReference.getContentHash(),
        jsonMapper,
        httpClient,
        config);
  }

  @Override
  public DeleteDocumentCommandStep1 newDeleteDocumentCommand(final String documentId) {
    return new DeleteDocumentCommandImpl(documentId, null, httpClient, config);
  }

  @Override
  public DeleteDocumentCommandStep1 newDeleteDocumentCommand(
      final DocumentReferenceResponse documentReference) {
    return new DeleteDocumentCommandImpl(
        documentReference.getDocumentId(), documentReference.getStoreId(), httpClient, config);
  }
}
