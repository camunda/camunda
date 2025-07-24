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
package io.camunda.spring.client.jobhandling.parameter;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.InternalClientException;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.spring.client.annotation.value.DocumentValue.ParameterType;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentParameterResolver implements ParameterResolver {
  private static final Logger LOG = LoggerFactory.getLogger(DocumentParameterResolver.class);
  private final String variableName;
  private final boolean optional;
  private final ParameterType parameterType;
  private final CamundaClient camundaClient;

  public DocumentParameterResolver(
      final String variableName,
      final boolean optional,
      final ParameterType parameterType,
      final CamundaClient camundaClient) {
    this.variableName = variableName;
    this.optional = optional;
    this.parameterType = parameterType;
    this.camundaClient = camundaClient;
  }

  @Override
  public Object resolve(final JobClient jobClient, final ActivatedJob job) {
    LOG.debug("Resolving document references for variable {}", variableName);
    final List<DocumentReferenceResponse> documentReferences = getDocumentReferences(job);
    return switch (parameterType) {
      case LIST -> documentReferences;
      case CONTEXT -> new ParameterDocumentContext(documentReferences, camundaClient, optional);
      case SINGLE -> singleDocumentReference(documentReferences);
    };
  }

  private DocumentReferenceResponse singleDocumentReference(
      final List<DocumentReferenceResponse> documentReferences) {
    if (optional) {
      return documentReferences.stream()
          .findFirst()
          .orElseGet(
              () -> {
                LOG.debug("Variable {} contains empty list of document references", variableName);
                return null;
              });
    } else {
      if (documentReferences.isEmpty()) {
        throw new IllegalStateException(
            String.format(
                "Variable %s contains empty list of document references and parameter is not optional",
                variableName));
      } else if (documentReferences.size() > 1) {
        LOG.warn(
            "Multiple document references for variable {}, returning only first", variableName);
      }
      return documentReferences.get(0);
    }
  }

  protected List<DocumentReferenceResponse> getDocumentReferences(final ActivatedJob job) {
    try {
      final List<DocumentReferenceResponse> documentReferences =
          job.getDocumentReferences(variableName);
      if (!optional && documentReferences == null) {
        throw new InternalClientException("Document reference variable value is null");
      }
      if (documentReferences == null) {
        return Collections.emptyList();
      }
      return documentReferences;
    } catch (final ClientException e) {
      if (!optional) {
        throw new IllegalStateException(
            String.format("Could not get document references for variable %s", variableName), e);
      }
      if (LOG.isDebugEnabled()) {
        LOG.warn("Could not get document references for variable {}", variableName, e);
      } else {
        LOG.warn(
            "Could not get document references for variable {}, please enable debug log for more details",
            variableName);
      }
      return List.of();
    }
  }
}
