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
package io.camunda.spring.client.jobhandling.result;

import io.camunda.client.api.command.CreateDocumentBatchCommandStep1;
import io.camunda.client.api.command.CreateDocumentBatchCommandStep1.CreateDocumentBatchCommandStep2;
import io.camunda.client.api.response.DocumentReferenceBatchResponse;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.spring.client.jobhandling.DocumentContext;
import io.camunda.spring.client.jobhandling.DocumentContext.DocumentEntry.AbstractDocumentEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResultDocumentContext implements DocumentContext, ResultDocumentContextBuilder {
  private static final Logger LOG = LoggerFactory.getLogger(ResultDocumentContext.class);
  private final Map<
          String, Function<CreateDocumentBatchCommandStep2, CreateDocumentBatchCommandStep2>>
      documentBuilders = new HashMap<>();
  private String storeId;
  private DocumentReferenceBatchResponse response;
  private JobClient jobClient;

  @Override
  public List<DocumentEntry> getDocuments() {
    if (response == null) {
      LOG.warn("Result document context has not been processed yet, returning empty list");
      return List.of();
    }
    if (!response.isSuccessful()) {
      LOG.warn(
          "Result document context has been processed with failures, returning only successful documents {}",
          response.getFailedDocuments());
    }
    return response.getCreatedDocuments().stream()
        .map(
            documentReferenceResponse ->
                new ResultDocumentEntry(documentReferenceResponse, jobClient))
        .map(DocumentEntry.class::cast)
        .toList();
  }

  public Map<String, Function<CreateDocumentBatchCommandStep2, CreateDocumentBatchCommandStep2>>
      getFailedDocumentBuilders() {
    if (response == null) {
      LOG.warn("Result document context has not been processed yet, returning empty map");
      return Map.of();
    }
    return response.getFailedDocuments().stream()
        .map(detail -> Map.entry(detail.getFileName(), documentBuilders.get(detail.getFileName())))
        .filter(e -> e.getValue() != null)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @Override
  public DocumentContext build() {
    return this;
  }

  @Override
  public ResultDocumentContextBuilder storeId(final String storeId) {
    this.storeId = storeId;
    return this;
  }

  @Override
  public ResultDocumentContextBuilder addDocument(
      final String fileName,
      final Function<CreateDocumentBatchCommandStep2, CreateDocumentBatchCommandStep2>
          documentBuilder) {
    documentBuilders.put(fileName, documentBuilder);
    return this;
  }

  public DocumentReferenceBatchResponse processDocumentBuilders(final JobClient jobClient) {
    if (response != null) {
      LOG.debug("Result document context has already been processed, returning previous response");
    } else {
      this.jobClient = jobClient;
      final CreateDocumentBatchCommandStep1 documentBatchCommand =
          jobClient.newCreateDocumentBatchCommand();
      if (storeId != null) {
        documentBatchCommand.storeId(storeId);
      }
      documentBuilders.forEach(
          (fileName, documentBuilder) -> {
            final CreateDocumentBatchCommandStep2 addDocumentCommand =
                documentBatchCommand.addDocument();
            addDocumentCommand.fileName(fileName);
            documentBuilder.apply(addDocumentCommand);
            addDocumentCommand.done();
          });
      response = documentBatchCommand.execute();
    }
    return response;
  }

  public static class ResultDocumentEntry extends AbstractDocumentEntry {

    public ResultDocumentEntry(
        final DocumentReferenceResponse documentReference, final JobClient jobClient) {
      super(documentReference, jobClient);
    }
  }
}
