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

import io.camunda.client.DocumentUtil;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.spring.client.annotation.value.DocumentValue.ParameterType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentResolver implements ParameterResolver {
  private static final Logger LOG = LoggerFactory.getLogger(DocumentResolver.class);
  private final String variableName;
  private final boolean optional;
  private final ParameterType parameterType;

  public DocumentResolver(
      final String variableName, final boolean optional, final ParameterType parameterType) {
    this.variableName = variableName;
    this.optional = optional;
    this.parameterType = parameterType;
  }

  @Override
  public Object resolve(final JobClient jobClient, final ActivatedJob job) {
    LOG.debug("Resolving documents for reference {}", variableName);
    final Object variableValue = getVariable(job);
    if (variableValue == null) {
      if (optional) {
        LOG.debug(
            "No documents found for reference {} and they are optional, returning null",
            variableName);
        return null;
      } else {
        throw new IllegalStateException(
            "Document reference " + variableName + " is mandatory, but no value was found");
      }
    }
    final List<DocumentReferenceResponse> documentReferences =
        DocumentUtil.createDocumentContext(jobClient, variableValue);
    return switch (parameterType) {
      case LIST -> documentReferences;
      case CONTEXT -> new ParameterDocumentContext(documentReferences, jobClient, optional);
      case SINGLE -> singleDocumentReference(documentReferences);
    };
  }

  private DocumentReferenceResponse singleDocumentReference(
      final List<DocumentReferenceResponse> documentReferences) {
    if (optional) {
      return documentReferences.stream().findFirst().orElse(null);
    } else {
      if (documentReferences.isEmpty()) {
        throw new IllegalStateException(
            "No document references available for variable " + variableName);
      } else if (documentReferences.size() > 1) {
        LOG.warn(
            "Multiple document references for variable {}, returning only first", variableName);
      }
      return documentReferences.get(0);
    }
  }

  protected Object getVariable(final ActivatedJob job) {
    return job.getVariablesAsMap().get(variableName);
  }
}
