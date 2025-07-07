/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.store.opensearch.client.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.opensearch.client.async.OpenSearchAsyncSnapshotOperations;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class RichOpenSearchClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(RichOpenSearchClient.class);
  final Async async;
  final OpenSearchBatchOperations openSearchBatchOperations;
  final OpenSearchClusterOperations openSearchClusterOperations;
  final OpenSearchDocumentOperations openSearchDocumentOperations;
  final OpenSearchIndexOperations openSearchIndexOperations;
  final OpenSearchPipelineOperations openSearchPipelineOperations;
  final OpenSearchSnapshotOperations openSearchSnapshotOperations;
  final OpenSearchTaskOperations openSearchTaskOperations;
  final OpenSearchTemplateOperations openSearchTemplateOperations;
  final OpenSearchISMOperations openSearchISMOperations;
  BeanFactory beanFactory;
  OpenSearchClient openSearchClient;

  public RichOpenSearchClient(
      BeanFactory beanFactory,
      OpenSearchClient openSearchClient,
      OpenSearchAsyncClient openSearchAsyncClient,
      ObjectMapper objectMapper) {
    this.beanFactory = beanFactory;
    this.openSearchClient = openSearchClient;
    async = new Async(openSearchAsyncClient);
    openSearchBatchOperations =
        new OpenSearchBatchOperations(LOGGER, openSearchClient, beanFactory);
    openSearchClusterOperations = new OpenSearchClusterOperations(LOGGER, openSearchClient);
    openSearchDocumentOperations = new OpenSearchDocumentOperations(LOGGER, openSearchClient);
    openSearchIndexOperations =
        new OpenSearchIndexOperations(LOGGER, openSearchClient, objectMapper);
    openSearchPipelineOperations = new OpenSearchPipelineOperations(LOGGER, openSearchClient);
    openSearchSnapshotOperations = new OpenSearchSnapshotOperations(LOGGER, openSearchClient);
    openSearchTaskOperations = new OpenSearchTaskOperations(LOGGER, openSearchClient);
    openSearchTemplateOperations = new OpenSearchTemplateOperations(LOGGER, openSearchClient);
    openSearchISMOperations = new OpenSearchISMOperations(LOGGER, openSearchClient);
  }

  public Async async() {
    return async;
  }

  public OpenSearchBatchOperations batch() {
    return openSearchBatchOperations;
  }

  public OpenSearchClusterOperations cluster() {
    return openSearchClusterOperations;
  }

  public OpenSearchDocumentOperations doc() {
    return openSearchDocumentOperations;
  }

  public OpenSearchIndexOperations index() {
    return openSearchIndexOperations;
  }

  public OpenSearchISMOperations ism() {
    return openSearchISMOperations;
  }

  public OpenSearchPipelineOperations pipeline() {
    return openSearchPipelineOperations;
  }

  public OpenSearchSnapshotOperations snapshot() {
    return openSearchSnapshotOperations;
  }

  public OpenSearchTaskOperations task() {
    return openSearchTaskOperations;
  }

  public OpenSearchTemplateOperations template() {
    return openSearchTemplateOperations;
  }

  public record AggregationValue(String key, long count) {}

  public class Async {
    final OpenSearchAsyncSnapshotOperations openSearchAsyncSnapshotOperations;

    public Async(final OpenSearchAsyncClient openSearchAsyncClient) {
      openSearchAsyncSnapshotOperations =
          new OpenSearchAsyncSnapshotOperations(LOGGER, openSearchAsyncClient);
    }

    public OpenSearchAsyncSnapshotOperations snapshot() {
      return openSearchAsyncSnapshotOperations;
    }
  }
}
