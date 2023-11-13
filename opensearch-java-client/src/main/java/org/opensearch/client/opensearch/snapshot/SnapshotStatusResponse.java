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

// typedef: snapshot.status.Response

@JsonpDeserializable
public class SnapshotStatusResponse implements JsonpSerializable {
	private final List<Status> snapshots;

	// ---------------------------------------------------------------------------------------------

	private SnapshotStatusResponse(Builder builder) {

		this.snapshots = ApiTypeHelper.unmodifiableRequired(builder.snapshots, this, "snapshots");

	}

	public static SnapshotStatusResponse of(Function<Builder, ObjectBuilder<SnapshotStatusResponse>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code snapshots}
	 */
	public final List<Status> snapshots() {
		return this.snapshots;
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

		if (ApiTypeHelper.isDefined(this.snapshots)) {
			generator.writeKey("snapshots");
			generator.writeStartArray();
			for (Status item0 : this.snapshots) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link SnapshotStatusResponse}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<SnapshotStatusResponse> {
		private List<Status> snapshots;

		/**
		 * Required - API name: {@code snapshots}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>snapshots</code>.
		 */
		public final Builder snapshots(List<Status> list) {
			this.snapshots = _listAddAll(this.snapshots, list);
			return this;
		}

		/**
		 * Required - API name: {@code snapshots}
		 * <p>
		 * Adds one or more values to <code>snapshots</code>.
		 */
		public final Builder snapshots(Status value, Status... values) {
			this.snapshots = _listAdd(this.snapshots, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code snapshots}
		 * <p>
		 * Adds a value to <code>snapshots</code> using a builder lambda.
		 */
		public final Builder snapshots(Function<Status.Builder, ObjectBuilder<Status>> fn) {
			return snapshots(fn.apply(new Status.Builder()).build());
		}

		/**
		 * Builds a {@link SnapshotStatusResponse}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public SnapshotStatusResponse build() {
			_checkSingleUse();

			return new SnapshotStatusResponse(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link SnapshotStatusResponse}
	 */
	public static final JsonpDeserializer<SnapshotStatusResponse> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, SnapshotStatusResponse::setupSnapshotStatusResponseDeserializer);

	protected static void setupSnapshotStatusResponseDeserializer(
			ObjectDeserializer<SnapshotStatusResponse.Builder> op) {

		op.add(Builder::snapshots, JsonpDeserializer.arrayDeserializer(Status._DESERIALIZER), "snapshots");

	}

}
