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
package io.camunda.operate.webapp.elasticsearch;

import static io.camunda.operate.schema.indices.OperateWebSessionIndex.ATTRIBUTES;
import static io.camunda.operate.schema.indices.OperateWebSessionIndex.CREATION_TIME;
import static io.camunda.operate.schema.indices.OperateWebSessionIndex.ID;
import static io.camunda.operate.schema.indices.OperateWebSessionIndex.LAST_ACCESSED_TIME;
import static io.camunda.operate.schema.indices.OperateWebSessionIndex.MAX_INACTIVE_INTERVAL_IN_SECONDS;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.schema.indices.OperateWebSessionIndex;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import io.camunda.operate.webapp.security.OperateSession;
import io.camunda.operate.webapp.security.SessionRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.elasticsearch.action.search.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchSessionRepository implements SessionRepository {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchSessionRepository.class);

  private final RetryElasticsearchClient retryElasticsearchClient;

  private final GenericConversionService conversionService;

  private final OperateWebSessionIndex operateWebSessionIndex;

  private final HttpServletRequest request;

  public ElasticsearchSessionRepository(
      final RetryElasticsearchClient retryElasticsearchClient,
      final GenericConversionService conversionService,
      final OperateWebSessionIndex operateWebSessionIndex,
      final HttpServletRequest request) {
    this.retryElasticsearchClient = retryElasticsearchClient;
    this.conversionService = conversionService;
    this.operateWebSessionIndex = operateWebSessionIndex;
    this.request = request;
  }

  @PostConstruct
  private void setUp() {
    setupConverter();
  }

  private void setupConverter() {
    conversionService.addConverter(Object.class, byte[].class, new SerializingConverter());
    conversionService.addConverter(byte[].class, Object.class, new DeserializingConverter());
  }

  @Override
  public List<String> getExpiredSessionIds() {
    final SearchRequest searchRequest =
        new SearchRequest(operateWebSessionIndex.getFullQualifiedName());
    final List<String> result = new ArrayList<>();

    retryElasticsearchClient.doWithEachSearchResult(
        searchRequest,
        sh -> {
          final Map<String, Object> document = sh.getSourceAsMap();
          final Optional<OperateSession> maybeSession = documentToSession(document);
          if (maybeSession.isPresent()) {
            final OperateSession session = maybeSession.get();
            LOGGER.debug("Check if session {} is expired: {}", session, session.isExpired());
            if (session.isExpired()) {
              result.add(session.getId());
            }
          } else {
            // need to delete entry in Elasticsearch in case of failing restore session
            result.add(getSessionIdFrom(document));
          }
        });

    return result;
  }

  @Override
  public void save(final OperateSession session) {
    retryElasticsearchClient.createOrUpdateDocument(
        operateWebSessionIndex.getFullQualifiedName(), session.getId(), sessionToDocument(session));
  }

  @Override
  public Optional<OperateSession> findById(final String id) {
    try {
      final Optional<Map<String, Object>> maybeDocument =
          Optional.ofNullable(
              retryElasticsearchClient.getDocument(
                  operateWebSessionIndex.getFullQualifiedName(), id));
      return maybeDocument.flatMap(this::documentToSession);
    } catch (final Exception e) {
      return Optional.empty();
    }
  }

  @Override
  public void deleteById(final String id) {
    retryElasticsearchClient.deleteDocument(operateWebSessionIndex.getFullQualifiedName(), id);
  }

  private byte[] serialize(final Object object) {
    return (byte[])
        conversionService.convert(
            object, TypeDescriptor.valueOf(Object.class), TypeDescriptor.valueOf(byte[].class));
  }

  private Object deserialize(final byte[] bytes) {
    return conversionService.convert(
        bytes, TypeDescriptor.valueOf(byte[].class), TypeDescriptor.valueOf(Object.class));
  }

  private Map<String, Object> sessionToDocument(final OperateSession session) {
    final Map<String, byte[]> attributes = new HashMap<>();
    session
        .getAttributeNames()
        .forEach(name -> attributes.put(name, serialize(session.getAttribute(name))));
    return Map.of(
        ID, session.getId(),
        CREATION_TIME, session.getCreationTime().toEpochMilli(),
        LAST_ACCESSED_TIME, session.getLastAccessedTime().toEpochMilli(),
        MAX_INACTIVE_INTERVAL_IN_SECONDS, session.getMaxInactiveInterval().getSeconds(),
        ATTRIBUTES, attributes);
  }

  private String getSessionIdFrom(final Map<String, Object> document) {
    return (String) document.get(ID);
  }

  private Optional<OperateSession> documentToSession(final Map<String, Object> document) {
    try {
      final String sessionId = getSessionIdFrom(document);
      final OperateSession session = new OperateSession(sessionId);
      session.setCreationTime(getInstantFor(document.get(CREATION_TIME)));
      session.setLastAccessedTime(getInstantFor(document.get(LAST_ACCESSED_TIME)));
      session.setMaxInactiveInterval(
          getDurationFor(document.get(MAX_INACTIVE_INTERVAL_IN_SECONDS)));

      setPollingFor(session);

      final Object attributesObject = document.get(ATTRIBUTES);
      if (attributesObject != null
          && attributesObject.getClass().isInstance(new HashMap<String, String>())) {
        final Map<String, String> attributes = (Map<String, String>) document.get(ATTRIBUTES);
        attributes
            .keySet()
            .forEach(
                name ->
                    session.setAttribute(
                        name, deserialize(Base64.getDecoder().decode(attributes.get(name)))));
      }
      return Optional.of(session);
    } catch (final Exception e) {
      LOGGER.error("Could not restore session.", e);
      return Optional.empty();
    }
  }

  private void setPollingFor(final OperateSession session) {
    try {
      if (request != null && request.getHeader(POLLING_HEADER) != null) {
        LOGGER.info("Set session polling to true");
        session.setPolling(true);
      }
    } catch (final Exception e) {
      LOGGER.debug(
          "Expected Exception: is not possible to access request as currently this is not on a request context");
    }
  }

  private Instant getInstantFor(final Object object) {
    if (object == null) {
      return null;
    }
    if (object instanceof final Long instantAsLong) {
      return Instant.ofEpochMilli(instantAsLong);
    }
    return null;
  }

  private Duration getDurationFor(final Object object) {
    if (object == null) {
      return null;
    }
    if (object instanceof final Integer durationAsInteger) {
      return Duration.ofSeconds(durationAsInteger);
    }
    return null;
  }
}
