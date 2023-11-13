/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

//----------------------------------------------------
// THIS CODE IS GENERATED. MANUAL EDITS WILL BE LOST.
//----------------------------------------------------

package org.opensearch.client.opensearch.snapshot;

import org.opensearch.client.opensearch.snapshot.get.SnapshotResponseItem;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;

import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: snapshot.get.Response


@JsonpDeserializable
public class GetSnapshotResponse implements JsonpSerializable {
	private final List<SnapshotResponseItem> responses;

	private final List<SnapshotInfo> snapshots;

	private final int total;

	private final int remaining;

	// ---------------------------------------------------------------------------------------------

	private GetSnapshotResponse(Builder builder) {

		this.responses = ApiTypeHelper.unmodifiable(builder.responses);
		this.snapshots = ApiTypeHelper.unmodifiable(builder.snapshots);
		this.total = ApiTypeHelper.requireNonNull(builder.total, this, "total");
		this.remaining = ApiTypeHelper.requireNonNull(builder.remaining, this, "remaining");

	}

	public static GetSnapshotResponse of(Function<Builder, ObjectBuilder<GetSnapshotResponse>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code responses}
	 */
	public final List<SnapshotResponseItem> responses() {
		return this.responses;
	}

	/**
	 * API name: {@code snapshots}
	 */
	public final List<SnapshotInfo> snapshots() {
		return this.snapshots;
	}

	/**
	 * Required - The total number of snapshots that match the request when ignoring
	 * size limit or after query parameter.
	 * <p>
	 * API name: {@code total}
	 */
	public final int total() {
		return this.total;
	}

	/**
	 * Required - The number of remaining snapshots that were not returned due to
	 * size limits and that can be fetched by additional requests using the next
	 * field value.
	 * <p>
	 * API name: {@code remaining}
	 */
	public final int remaining() {
		return this.remaining;
	}

	/**
	 * Serialize this object to JSON.
	 */
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {
		generator.writeStartObject();
		serializeInternal(generator, mapper);
		generator.writeEnd();
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		if (ApiTypeHelper.isDefined(this.responses)) {
			generator.writeKey("responses");
			generator.writeStartArray();
			for (SnapshotResponseItem item0 : this.responses) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.snapshots)) {
			generator.writeKey("snapshots");
			generator.writeStartArray();
			for (SnapshotInfo item0 : this.snapshots) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		generator.writeKey("total");
		generator.write(this.total);

		generator.writeKey("remaining");
		generator.write(this.remaining);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link GetSnapshotResponse}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<GetSnapshotResponse> {
		@Nullable
		private List<SnapshotResponseItem> responses;

		@Nullable
		private List<SnapshotInfo> snapshots;

		private Integer total;

		private Integer remaining;

		/**
		 * API name: {@code responses}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>responses</code>.
		 */
		public final Builder responses(List<SnapshotResponseItem> list) {
			this.responses = _listAddAll(this.responses, list);
			return this;
		}

		/**
		 * API name: {@code responses}
		 * <p>
		 * Adds one or more values to <code>responses</code>.
		 */
		public final Builder responses(SnapshotResponseItem value, SnapshotResponseItem... values) {
			this.responses = _listAdd(this.responses, value, values);
			return this;
		}

		/**
		 * API name: {@code responses}
		 * <p>
		 * Adds a value to <code>responses</code> using a builder lambda.
		 */
		public final Builder responses(Function<SnapshotResponseItem.Builder, ObjectBuilder<SnapshotResponseItem>> fn) {
			return responses(fn.apply(new SnapshotResponseItem.Builder()).build());
		}

		/**
		 * API name: {@code snapshots}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>snapshots</code>.
		 */
		public final Builder snapshots(List<SnapshotInfo> list) {
			this.snapshots = _listAddAll(this.snapshots, list);
			return this;
		}

		/**
		 * API name: {@code snapshots}
		 * <p>
		 * Adds one or more values to <code>snapshots</code>.
		 */
		public final Builder snapshots(SnapshotInfo value, SnapshotInfo... values) {
			this.snapshots = _listAdd(this.snapshots, value, values);
			return this;
		}

		/**
		 * API name: {@code snapshots}
		 * <p>
		 * Adds a value to <code>snapshots</code> using a builder lambda.
		 */
		public final Builder snapshots(Function<SnapshotInfo.Builder, ObjectBuilder<SnapshotInfo>> fn) {
			return snapshots(fn.apply(new SnapshotInfo.Builder()).build());
		}

		/**
		 * Required - The total number of snapshots that match the request when ignoring
		 * size limit or after query parameter.
		 * <p>
		 * API name: {@code total}
		 */
		public final Builder total(int value) {
			this.total = value;
			return this;
		}

		/**
		 * Required - The number of remaining snapshots that were not returned due to
		 * size limits and that can be fetched by additional requests using the next
		 * field value.
		 * <p>
		 * API name: {@code remaining}
		 */
		public final Builder remaining(int value) {
			this.remaining = value;
			return this;
		}

		/**
		 * Builds a {@link GetSnapshotResponse}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public GetSnapshotResponse build() {
			_checkSingleUse();

			return new GetSnapshotResponse(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link GetSnapshotResponse}
	 */
	public static final JsonpDeserializer<GetSnapshotResponse> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, GetSnapshotResponse::setupGetSnapshotResponseDeserializer);

	protected static void setupGetSnapshotResponseDeserializer(ObjectDeserializer<GetSnapshotResponse.Builder> op) {

		op.add(Builder::responses, JsonpDeserializer.arrayDeserializer(SnapshotResponseItem._DESERIALIZER),
				"responses");
		op.add(Builder::snapshots, JsonpDeserializer.arrayDeserializer(SnapshotInfo._DESERIALIZER), "snapshots");
		op.add(Builder::total, JsonpDeserializer.integerDeserializer(), "total");
		op.add(Builder::remaining, JsonpDeserializer.integerDeserializer(), "remaining");

	}

}
