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

import static io.camunda.operate.util.ExceptionHelper.withOperateRuntimeException;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OpensearchProperties;
import io.camunda.operate.store.BatchRequest;
import java.io.IOException;
import java.util.List;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.slf4j.Logger;
import org.springframework.beans.factory.BeanFactory;

public class OpenSearchBatchOperations extends OpenSearchSyncOperation {
  private final BeanFactory beanFactory;
  private final OpensearchProperties opensearchProperties;

  public OpenSearchBatchOperations(
      Logger logger,
      OpenSearchClient openSearchClient,
      BeanFactory beanFactory,
      OpensearchProperties opensearchProperties) {
    super(logger, openSearchClient);
    this.beanFactory = beanFactory;
    this.opensearchProperties = opensearchProperties;
  }

  private void processBulkRequest(OpenSearchClient osClient, BulkRequest bulkRequest)
      throws PersistenceException {
    if (!bulkRequest.operations().isEmpty()) {
      try {
        logger.debug("************* FLUSH BULK START *************");
        final BulkResponse bulkItemResponses = osClient.bulk(bulkRequest);
        final List<BulkResponseItem> items = bulkItemResponses.items();
        for (BulkResponseItem responseItem : items) {
          if (responseItem.error() != null) {
            // TODO check how to log the error for OpenSearch;
            logger.error(
                String.format(
                    "%s failed for type [%s] and id [%s]: %s",
                    responseItem.operationType(),
                    responseItem.index(),
                    responseItem.id(),
                    responseItem.error().reason()),
                "error on OpenSearch BulkRequest");
            throw new PersistenceException(
                "Operation failed: " + responseItem.error().reason(),
                new OperateRuntimeException(responseItem.error().reason()),
                Integer.valueOf(responseItem.id()));
          }
        }
        logger.debug("************* FLUSH BULK FINISH *************");
      } catch (IOException ex) {
        throw new PersistenceException(
            "Error when processing bulk request against OpenSearch: " + ex.getMessage(), ex);
      }
    }
  }

  private BulkRequest validateIndex(BulkRequest bulkRequest) {
    for (BulkOperation operation : bulkRequest.operations()) {
      String index = null;
      if (operation.isIndex()) {
        index = operation.index().index();
      } else if (operation.isCreate()) {
        index = operation.create().index();
      } else if (operation.isUpdate()) {
        index = operation.update().index();
      } else if (operation.isDelete()) {
        index = operation.delete().index();
      }
      if (index == null) {
        if (opensearchProperties.isBulkRequestIgnoreNullIndex()) {}
      }
    }

    return bulkRequest;
  }

  public void bulk(BulkRequest.Builder bulkRequestBuilder) {
    bulk(bulkRequestBuilder.build());
  }

  public void bulk(BulkRequest bulkRequest) {
    withOperateRuntimeException(
        () -> {
          processBulkRequest(openSearchClient, bulkRequest);
          return null;
        });
  }

  public BatchRequest newBatchRequest() {
    return beanFactory.getBean(BatchRequest.class);
  }
}
