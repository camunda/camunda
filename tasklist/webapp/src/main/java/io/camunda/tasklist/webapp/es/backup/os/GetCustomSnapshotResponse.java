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
