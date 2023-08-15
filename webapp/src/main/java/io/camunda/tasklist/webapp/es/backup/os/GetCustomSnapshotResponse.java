/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es.backup.os;

import jakarta.json.stream.JsonGenerator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch.snapshot.GetSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.SnapshotInfo;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;

/**
 * A custom response designed to address a discrepancy between the expected and actual output
 * formats for snapshots in OpenSearch.
 *
 * <p>Relevant Issue: <a {@see https://github.com/opensearch-project/opensearch-java/issues/420}
 * According to OpenSearch's <a
 * href="https://opensearch.org/docs/2.9/api-reference/snapshots/get-snapshot/">version 2.9
 * documentation</a>, the snapshot information is returned in the following format:
 *
 * <pre>
 *  {
 *    "snapshots" : [...]
 *  }
 * </pre>
 *
 * However, the <a
 * href="https://mvnrepository.com/artifact/org.opensearch.client/opensearch-java/2.6.0">opensearch-java-2.6.0</a>
 * library anticipates the response in the format described in {@link
 * org.opensearch.client.opensearch.snapshot.GetSnapshotResponse}:
 *
 * <pre>
 *  {
 *    "responses" : [...],
 *    "snapshots" : [...],
 *    "total" : 0,
 *    "total" : 0
 *  }
 * </pre>
 *
 * This class serves as a temporary solution to the discrepancy. Once the relevant issue will be
 * resolved, this class may be deprecated or removed and {@link
 * org.opensearch.client.opensearch.snapshot.GetSnapshotResponse} class used instead.
 */
@JsonpDeserializable
final class GetCustomSnapshotResponse implements JsonpSerializable {
  private final List<SnapshotInfo> snapshots;

  // ---------------------------------------------------------------------------------------------

  private GetCustomSnapshotResponse(GetCustomSnapshotResponse.Builder builder) {
    this.snapshots = ApiTypeHelper.unmodifiable(builder.snapshots);
  }

  public static GetCustomSnapshotResponse of(
      Function<Builder, ObjectBuilder<GetCustomSnapshotResponse>> fn) {
    return fn.apply(new GetCustomSnapshotResponse.Builder()).build();
  }

  /** API name: {@code snapshots} */
  public final List<SnapshotInfo> snapshots() {
    return this.snapshots;
  }

  /** Serialize this object to JSON. */
  public void serialize(JsonGenerator generator, JsonpMapper mapper) {
    generator.writeStartObject();
    serializeInternal(generator, mapper);
    generator.writeEnd();
  }

  protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

    if (ApiTypeHelper.isDefined(this.snapshots)) {
      generator.writeKey("snapshots");
      generator.writeStartArray();
      for (SnapshotInfo item0 : this.snapshots) {
        item0.serialize(generator, mapper);
      }
      generator.writeEnd();
    }
  }

  // ---------------------------------------------------------------------------------------------

  /** Builder for {@link GetCustomSnapshotResponse}. */
  public static class Builder extends ObjectBuilderBase
      implements ObjectBuilder<GetCustomSnapshotResponse> {

    @Nullable private List<SnapshotInfo> snapshots;

    /**
     * API name: {@code snapshots}
     *
     * <p>Adds all elements of <code>list</code> to <code>snapshots</code>.
     */
    public final GetCustomSnapshotResponse.Builder snapshots(List<SnapshotInfo> list) {
      this.snapshots = _listAddAll(this.snapshots, list);
      return this;
    }

    /**
     * API name: {@code snapshots}
     *
     * <p>Adds one or more values to <code>snapshots</code>.
     */
    public final GetCustomSnapshotResponse.Builder snapshots(
        SnapshotInfo value, SnapshotInfo... values) {
      this.snapshots = _listAdd(this.snapshots, value, values);
      return this;
    }

    /**
     * API name: {@code snapshots}
     *
     * <p>Adds a value to <code>snapshots</code> using a builder lambda.
     */
    public final GetCustomSnapshotResponse.Builder snapshots(
        Function<SnapshotInfo.Builder, ObjectBuilder<SnapshotInfo>> fn) {
      return snapshots(fn.apply(new SnapshotInfo.Builder()).build());
    }

    /**
     * Builds a {@link GetCustomSnapshotResponse}.
     *
     * @throws NullPointerException if some of the required fields are null.
     */
    public GetCustomSnapshotResponse build() {
      _checkSingleUse();

      return new GetCustomSnapshotResponse(this);
    }
  }

  // ---------------------------------------------------------------------------------------------

  /** Json deserializer for {@link GetCustomSnapshotResponse} */
  public static final JsonpDeserializer<GetCustomSnapshotResponse> _DESERIALIZER =
      ObjectBuilderDeserializer.lazy(
          GetCustomSnapshotResponse.Builder::new,
          GetCustomSnapshotResponse::setupGetSnapshotResponseDeserializer);

  protected static void setupGetSnapshotResponseDeserializer(ObjectDeserializer<Builder> op) {
    op.add(
        GetCustomSnapshotResponse.Builder::snapshots,
        JsonpDeserializer.arrayDeserializer(SnapshotInfo._DESERIALIZER),
        "snapshots");
  }

  public static final Endpoint<GetSnapshotRequest, GetCustomSnapshotResponse, ErrorResponse>
      _ENDPOINT =
          new SimpleEndpoint<>(

              // Request method
              request -> "GET",

              // Request path
              request -> {
                final int _repository = 1 << 0;
                final int _snapshot = 1 << 1;

                int propsSet = 0;

                propsSet |= _repository;
                propsSet |= _snapshot;

                if (propsSet == (_repository | _snapshot)) {
                  StringBuilder buf = new StringBuilder();
                  buf.append("/_snapshot");
                  buf.append("/");
                  SimpleEndpoint.pathEncode(request.repository(), buf);
                  buf.append("/");
                  SimpleEndpoint.pathEncode(
                      request.snapshot().stream().collect(Collectors.joining(",")), buf);
                  return buf.toString();
                }
                throw SimpleEndpoint.noPathTemplateFound("path");
              },

              // Request parameters
              request -> {
                Map<String, String> params = new HashMap<>();
                if (request.masterTimeout() != null) {
                  params.put("master_timeout", request.masterTimeout()._toJsonString());
                }
                if (request.clusterManagerTimeout() != null) {
                  params.put(
                      "cluster_manager_timeout", request.clusterManagerTimeout()._toJsonString());
                }
                if (request.includeRepository() != null) {
                  params.put("include_repository", String.valueOf(request.includeRepository()));
                }
                if (request.ignoreUnavailable() != null) {
                  params.put("ignore_unavailable", String.valueOf(request.ignoreUnavailable()));
                }
                if (request.indexDetails() != null) {
                  params.put("index_details", String.valueOf(request.indexDetails()));
                }
                if (request.human() != null) {
                  params.put("human", String.valueOf(request.human()));
                }
                if (request.verbose() != null) {
                  params.put("verbose", String.valueOf(request.verbose()));
                }
                return params;
              },
              SimpleEndpoint.emptyMap(),
              false,
              GetCustomSnapshotResponse._DESERIALIZER);
}
