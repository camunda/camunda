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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.DocumentReferenceBatchResponse;
import io.camunda.spring.client.jobhandling.DocumentContext;
import io.camunda.spring.client.jobhandling.result.DocumentResultProcessorFailureHandlingStrategy.FailureHandlingContext;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

public class DefaultResultProcessor implements ResultProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultResultProcessor.class);
  private final CamundaClient camundaClient;
  private final DocumentResultProcessorFailureHandlingStrategy exceptionHandlingStrategy;

  public DefaultResultProcessor(
      final CamundaClient camundaClient,
      final DocumentResultProcessorFailureHandlingStrategy exceptionHandlingStrategy) {
    this.camundaClient = camundaClient;
    this.exceptionHandlingStrategy = exceptionHandlingStrategy;
  }

  @Override
  public Object process(final ResultProcessorContext context) {
    return handleResult(context.getResult(), context.getJob());
  }

  protected Object handleResult(final Object result, final ActivatedJob activatedJob) {
    if (result == null) {
      return null;
    }
    return handleDocuments(result, activatedJob);
  }

  protected Object handleDocuments(final Object result, final ActivatedJob activatedJob) {
    if (result instanceof String || result instanceof InputStream) {
      return result;
    }
    if (result instanceof Map) {
      final Map<String, Object> resultMap = (Map<String, Object>) result;
      handleDocumentsForResultMap(resultMap, activatedJob);
      return result;
    }
    // result is object
    final Class<?> clazz = result.getClass();
    if (hasDocumentField(clazz)) {
      handleDocumentsForResultObject(result, activatedJob);
      return result;
    } else {
      return result;
    }
  }

  private boolean hasDocumentField(final Class<?> clazz) {
    return Arrays.stream(clazz.getDeclaredFields()).anyMatch(this::isDocumentField);
  }

  private boolean isDocumentField(final Field field) {
    return isDocumentContext(field.getType());
  }

  private boolean isDocumentContext(final Class<?> type) {
    return type.isAssignableFrom(DocumentContext.class);
  }

  private void handleDocumentsForResultObject(
      final Object result, final ActivatedJob activatedJob) {
    ReflectionUtils.doWithFields(
        result.getClass(),
        field -> {
          ReflectionUtils.makeAccessible(field);
          final Object fieldValue = ReflectionUtils.getField(field, result);
          handleDocumentFieldValue(field.getName(), fieldValue, activatedJob);
        },
        this::isDocumentField);
  }

  private void handleDocumentsForResultMap(
      final Map<String, Object> result, final ActivatedJob activatedJob) {
    final Map<String, Object> replacements = new HashMap<>();
    for (final Entry<String, Object> entry : result.entrySet()) {
      if (entry.getValue() instanceof DocumentContext) {
        handleDocumentFieldValue(entry.getKey(), entry.getValue(), activatedJob);
      }
    }
    result.putAll(replacements);
  }

  private void handleDocumentFieldValue(
      final String key, final Object value, final ActivatedJob activatedJob) {
    if (value instanceof final ResultDocumentContext resultDocumentContext) {
      LOG.debug("Handling submitted document {}", key);
      final DocumentReferenceBatchResponse response =
          resultDocumentContext.processDocumentBuilders(camundaClient);
      if (!response.isSuccessful()) {
        exceptionHandlingStrategy.handleFailure(
            new FailureHandlingContext(
                activatedJob,
                camundaClient,
                response,
                resultDocumentContext.getFailedDocumentBuilders()));
      }
    }
  }
}
