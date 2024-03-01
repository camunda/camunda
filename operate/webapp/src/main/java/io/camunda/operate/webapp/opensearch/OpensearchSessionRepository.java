/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.webapp.opensearch;

import static io.camunda.operate.store.opensearch.dsl.RequestDSL.indexRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static java.util.function.UnaryOperator.identity;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.schema.indices.OperateWebSessionIndex;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.security.OperateSession;
import io.camunda.operate.webapp.security.SessionRepository;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchSessionRepository implements SessionRepository {
  private static final Logger logger = LoggerFactory.getLogger(OpensearchSessionRepository.class);

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Autowired private GenericConversionService conversionService;

  @Autowired private OperateWebSessionIndex operateWebSessionIndex;

  @PostConstruct
  private void setUp() {
    setupConverter();
  }

  private void setupConverter() {
    conversionService.addConverter(Object.class, byte[].class, new SerializingConverter());
    conversionService.addConverter(byte[].class, Object.class, new DeserializingConverter());
  }

  private SessionEntity toSessionEntity(OperateSession session) {
    Map<String, String> attributes =
        session.getAttributeNames().stream()
            .collect(Collectors.toMap(identity(), name -> serialize(session.getAttribute(name))));

    return new SessionEntity(
        session.getId(),
        session.getCreationTime().toEpochMilli(),
        session.getLastAccessedTime().toEpochMilli(),
        session.getMaxInactiveInterval().getSeconds(),
        attributes);
  }

  private String serialize(Object object) {
    return new String(
        Base64.getEncoder()
            .encode(
                (byte[])
                    conversionService.convert(
                        object,
                        TypeDescriptor.valueOf(Object.class),
                        TypeDescriptor.valueOf(byte[].class))));
  }

  private OperateSession toOperateSession(SessionEntity sessionEntity) {
    OperateSession session = new OperateSession(sessionEntity.id());
    session.setCreationTime(nullable(sessionEntity.creationTime, Instant::ofEpochMilli));
    session.setLastAccessedTime(nullable(sessionEntity.lastAccessedTime, Instant::ofEpochMilli));
    session.setMaxInactiveInterval(
        nullable(sessionEntity.maxInactiveIntervalInSeconds, Duration::ofSeconds));

    if (sessionEntity.attributes() != null) {
      sessionEntity
          .attributes()
          .forEach((key, value) -> session.setAttribute(key, deserialize(value)));
    }

    return session;
  }

  private Object deserialize(String s) {
    byte[] bytes = Base64.getDecoder().decode(s.getBytes());
    return conversionService.convert(
        bytes, TypeDescriptor.valueOf(byte[].class), TypeDescriptor.valueOf(Object.class));
  }

  private <A, R> R nullable(final A a, Function<A, R> f) {
    return a == null ? null : f.apply(a);
  }

  @Override
  public List<String> getExpiredSessionIds() {
    return richOpenSearchClient
        .doc()
        .scrollValues(
            searchRequestBuilder(operateWebSessionIndex.getFullQualifiedName()),
            SessionEntity.class)
        .stream()
        .map(this::toOperateSession)
        .filter(OperateSession::isExpired)
        .map(OperateSession::getId)
        .toList();
  }

  @Override
  public void save(OperateSession session) {
    var requestBuilder =
        indexRequestBuilder(operateWebSessionIndex.getFullQualifiedName())
            .id(session.getId())
            .document(toSessionEntity(session));

    richOpenSearchClient.doc().indexWithRetries(requestBuilder);
  }

  @Override
  public Optional<OperateSession> findById(final String id) {
    try {
      return richOpenSearchClient
          .doc()
          .getWithRetries(operateWebSessionIndex.getFullQualifiedName(), id, SessionEntity.class)
          .map(this::toOperateSession);
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  @Override
  public void deleteById(String id) {
    richOpenSearchClient.doc().deleteWithRetries(operateWebSessionIndex.getFullQualifiedName(), id);
  }

  private record SessionEntity(
      String id,
      Long creationTime,
      Long lastAccessedTime,
      Long maxInactiveIntervalInSeconds,
      Map<String, String> attributes) {}
}
