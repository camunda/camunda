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

import static io.camunda.spring.client.annotation.AnnotationUtil.getDocumentValue;
import static io.camunda.spring.client.annotation.AnnotationUtil.getVariableValue;
import static io.camunda.spring.client.annotation.AnnotationUtil.isCustomHeaders;
import static io.camunda.spring.client.annotation.AnnotationUtil.isDocument;
import static io.camunda.spring.client.annotation.AnnotationUtil.isVariable;
import static io.camunda.spring.client.annotation.AnnotationUtil.isVariablesAsType;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.spring.client.annotation.value.DocumentValue;
import io.camunda.spring.client.annotation.value.DocumentValue.ParameterType;
import io.camunda.spring.client.annotation.value.VariableValue;
import io.camunda.spring.client.bean.ParameterInfo;

public class DefaultParameterResolverStrategy implements ParameterResolverStrategy {
  protected final JsonMapper jsonMapper;
  private final io.camunda.zeebe.client.api.worker.JobClient jobClient;

  public DefaultParameterResolverStrategy(
      final JsonMapper jsonMapper, final io.camunda.zeebe.client.api.worker.JobClient jobClient) {
    this.jsonMapper = jsonMapper;
    this.jobClient = jobClient;
  }

  public DefaultParameterResolverStrategy(final JsonMapper jsonMapper) {
    this(jsonMapper, null);
  }

  @Override
  public ParameterResolver createResolver(final ParameterInfo parameterInfo) {
    final Class<?> parameterType = parameterInfo.getParameterInfo().getType();
    // legacy
    if (io.camunda.zeebe.client.api.worker.JobClient.class.isAssignableFrom(parameterType)) {
      if (jobClient != null) {
        return new CompatJobClientParameterResolver(jobClient);
      } else {
        throw new IllegalStateException(
            "Legacy JobClient is required for parameter "
                + parameterInfo
                + " of method "
                + parameterInfo.getMethodInfo());
      }
    }
    if (io.camunda.zeebe.client.api.response.ActivatedJob.class.isAssignableFrom(parameterType)) {
      return new CompatActivatedJobParameterResolver();
    }
    // end legacy
    if (JobClient.class.isAssignableFrom(parameterType)) {
      return new JobClientParameterResolver();
    } else if (ActivatedJob.class.isAssignableFrom(parameterType)) {
      return new ActivatedJobParameterResolver();
    } else if (isVariable(parameterInfo)) {
      // get() can be used safely here as isVariable() verifies that an annotation is present
      final VariableValue variableValue = getVariableValue(parameterInfo).get();
      final String variableName = variableValue.getName();
      final boolean optional = variableValue.isOptional();
      return new VariableParameterResolver(variableName, parameterType, jsonMapper, optional);
    } else if (isVariablesAsType(parameterInfo)) {
      return new VariablesAsTypeParameterResolver(parameterType);
    } else if (isCustomHeaders(parameterInfo)) {
      return new CustomHeadersParameterResolver();
    } else if (isDocument(parameterInfo)) {
      final DocumentValue documentValue = getDocumentValue(parameterInfo).get();
      final String variableName = documentValue.getName();
      final boolean optional = documentValue.isOptional();
      final ParameterType documentParameterType = documentValue.getParameterType();
      return new DocumentParameterResolver(variableName, optional, documentParameterType);
    }
    throw new IllegalStateException(
        "Could not create parameter resolver for parameter " + parameterInfo);
  }
}
