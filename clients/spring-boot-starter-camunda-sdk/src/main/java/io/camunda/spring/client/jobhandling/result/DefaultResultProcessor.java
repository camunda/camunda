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

import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.spring.client.jobhandling.DocumentContext.DocumentEntry;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

public class DefaultResultProcessor implements ResultProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultResultProcessor.class);
  private final JobClient jobClient;

  public DefaultResultProcessor(final JobClient jobClient) {
    this.jobClient = jobClient;
  }

  @Override
  public Object process(final ResultProcessorContext context) {
    return handleResult(context.getResult());
  }

  protected Object handleResult(final Object result) {
    if (result == null) {
      return null;
    }
    return handleDocuments(result);
  }

  protected Object handleDocuments(final Object result) {
    if (result instanceof String || result instanceof InputStream) {
      return result;
    }
    if (result instanceof Map) {
      final Map<String, Object> resultMap = (Map<String, Object>) result;
      handleDocumentsForResultMap(resultMap);
      return result;
    }
    // result is object
    final Class<?> clazz = result.getClass();
    if (hasDocumentField(clazz)) {
      final Map<String, Object> map = extractFieldsToMap(result);
      handleDocumentsForResultMap(map);
      return jobClient.getConfiguration().getJsonMapper().transform(map, clazz);
    } else {
      return result;
    }
  }

  private boolean hasDocumentField(final Class<?> clazz) {
    return Arrays.stream(clazz.getDeclaredFields())
        .anyMatch(f -> f.getType().equals(ResultDocumentContext.class));
  }

  private Map<String, Object> extractFieldsToMap(final Object result) {
    final Map<String, Object> map = new HashMap<>();
    ReflectionUtils.doWithFields(
        result.getClass(),
        field -> map.put(field.getName(), ReflectionUtils.getField(field, result)));
    return map;
  }

  private void handleDocumentsForResultMap(final Map<String, Object> result) {
    final Map<String, Object> replacements = new HashMap<>();
    for (final Entry<String, Object> entry : result.entrySet()) {
      if (entry.getValue() instanceof final ResultDocumentContext resultDocumentContext) {
        LOG.debug("Handling submitted document {}", entry.getKey());
        resultDocumentContext.processDocumentBuilders(jobClient);
        final List<DocumentReferenceResponse> documentReferences =
            resultDocumentContext.getDocuments().stream()
                .map(DocumentEntry::getDocumentReference)
                .toList();
        replacements.put(entry.getKey(), documentReferences);
      }
    }
    result.putAll(replacements);
  }
}
