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

import io.camunda.client.api.command.CreateDocumentCommandStep1;
import io.camunda.client.api.command.CreateDocumentCommandStep1.CreateDocumentCommandStep2;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.spring.client.jobhandling.DocumentContext;
import io.camunda.spring.client.jobhandling.DocumentContext.DocumentEntry.AbstractDocumentEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ResultDocumentContext implements DocumentContext, ResultDocumentContextBuilder {
  private final List<Function<CreateDocumentCommandStep1, CreateDocumentCommandStep2>>
      documentBuilders = new ArrayList<>();
  private final List<ResultDocumentEntry> documentEntries = new ArrayList<>();

  @Override
  public List<DocumentEntry> getDocuments() {
    return documentEntries.stream().map(DocumentEntry.class::cast).toList();
  }

  @Override
  public DocumentContext build() {
    return this;
  }

  @Override
  public ResultDocumentContextBuilder addDocument(
      final Function<CreateDocumentCommandStep1, CreateDocumentCommandStep2> documentBuilder) {
    documentBuilders.add(documentBuilder);
    return this;
  }

  public void processDocumentBuilders(final JobClient jobClient) {
    new ArrayList<>(documentBuilders)
        .stream()
            .map(
                documentBuilder -> {
                  final CreateDocumentCommandStep1 step1 = jobClient.newCreateDocumentCommand();
                  final CreateDocumentCommandStep2 step2 = documentBuilder.apply(step1);
                  final DocumentReferenceResponse response = step2.execute();
                  documentBuilders.remove(documentBuilder);
                  return response;
                })
            .forEach(ref -> documentEntries.add(new ResultDocumentEntry(ref, jobClient)));
  }

  public static class ResultDocumentEntry extends AbstractDocumentEntry {

    public ResultDocumentEntry(
        final DocumentReferenceResponse documentReference, final JobClient jobClient) {
      super(documentReference, jobClient);
    }
  }
}
