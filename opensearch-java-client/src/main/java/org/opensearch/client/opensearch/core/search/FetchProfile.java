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

package org.opensearch.client.opensearch.core.search;

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

// typedef: _global.search._types.FetchProfile


@JsonpDeserializable
public class FetchProfile implements JsonpSerializable {
	private final String type;

	private final String description;

	private final long timeInNanos;

	private final FetchProfileBreakdown breakdown;

	@Nullable
	private final FetchProfileDebug debug;

	private final List<FetchProfile> children;

	// ---------------------------------------------------------------------------------------------

	private FetchProfile(Builder builder) {

		this.type = ApiTypeHelper.requireNonNull(builder.type, this, "type");
		this.description = ApiTypeHelper.requireNonNull(builder.description, this, "description");
		this.timeInNanos = ApiTypeHelper.requireNonNull(builder.timeInNanos, this, "timeInNanos");
		this.breakdown = ApiTypeHelper.requireNonNull(builder.breakdown, this, "breakdown");
		this.debug = builder.debug;
		this.children = ApiTypeHelper.unmodifiable(builder.children);

	}

	public static FetchProfile of(Function<Builder, ObjectBuilder<FetchProfile>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code type}
	 */
	public final String type() {
		return this.type;
	}

	/**
	 * Required - API name: {@code description}
	 */
	public final String description() {
		return this.description;
	}

	/**
	 * Required - API name: {@code time_in_nanos}
	 */
	public final long timeInNanos() {
		return this.timeInNanos;
	}

	/**
	 * Required - API name: {@code breakdown}
	 */
	public final FetchProfileBreakdown breakdown() {
		return this.breakdown;
	}

	/**
	 * API name: {@code debug}
	 */
	@Nullable
	public final FetchProfileDebug debug() {
		return this.debug;
	}

	/**
	 * API name: {@code children}
	 */
	public final List<FetchProfile> children() {
		return this.children;
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

		generator.writeKey("type");
		generator.write(this.type);

		generator.writeKey("description");
		generator.write(this.description);

		generator.writeKey("time_in_nanos");
		generator.write(this.timeInNanos);

		generator.writeKey("breakdown");
		this.breakdown.serialize(generator, mapper);

		if (this.debug != null) {
			generator.writeKey("debug");
			this.debug.serialize(generator, mapper);

		}
		if (ApiTypeHelper.isDefined(this.children)) {
			generator.writeKey("children");
			generator.writeStartArray();
			for (FetchProfile item0 : this.children) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link FetchProfile}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<FetchProfile> {
		private String type;

		private String description;

		private Long timeInNanos;

		private FetchProfileBreakdown breakdown;

		@Nullable
		private FetchProfileDebug debug;

		@Nullable
		private List<FetchProfile> children;

		/**
		 * Required - API name: {@code type}
		 */
		public final Builder type(String value) {
			this.type = value;
			return this;
		}

		/**
		 * Required - API name: {@code description}
		 */
		public final Builder description(String value) {
			this.description = value;
			return this;
		}

		/**
		 * Required - API name: {@code time_in_nanos}
		 */
		public final Builder timeInNanos(long value) {
			this.timeInNanos = value;
			return this;
		}

		/**
		 * Required - API name: {@code breakdown}
		 */
		public final Builder breakdown(FetchProfileBreakdown value) {
			this.breakdown = value;
			return this;
		}

		/**
		 * Required - API name: {@code breakdown}
		 */
		public final Builder breakdown(
				Function<FetchProfileBreakdown.Builder, ObjectBuilder<FetchProfileBreakdown>> fn) {
			return this.breakdown(fn.apply(new FetchProfileBreakdown.Builder()).build());
		}

		/**
		 * API name: {@code debug}
		 */
		public final Builder debug(@Nullable FetchProfileDebug value) {
			this.debug = value;
			return this;
		}

		/**
		 * API name: {@code debug}
		 */
		public final Builder debug(Function<FetchProfileDebug.Builder, ObjectBuilder<FetchProfileDebug>> fn) {
			return this.debug(fn.apply(new FetchProfileDebug.Builder()).build());
		}

		/**
		 * API name: {@code children}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>children</code>.
		 */
		public final Builder children(List<FetchProfile> list) {
			this.children = _listAddAll(this.children, list);
			return this;
		}

		/**
		 * API name: {@code children}
		 * <p>
		 * Adds one or more values to <code>children</code>.
		 */
		public final Builder children(FetchProfile value, FetchProfile... values) {
			this.children = _listAdd(this.children, value, values);
			return this;
		}

		/**
		 * API name: {@code children}
		 * <p>
		 * Adds a value to <code>children</code> using a builder lambda.
		 */
		public final Builder children(Function<FetchProfile.Builder, ObjectBuilder<FetchProfile>> fn) {
			return children(fn.apply(new FetchProfile.Builder()).build());
		}

		/**
		 * Builds a {@link FetchProfile}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public FetchProfile build() {
			_checkSingleUse();

			return new FetchProfile(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link FetchProfile}
	 */
	public static final JsonpDeserializer<FetchProfile> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			FetchProfile::setupFetchProfileDeserializer);

	protected static void setupFetchProfileDeserializer(ObjectDeserializer<FetchProfile.Builder> op) {

		op.add(Builder::type, JsonpDeserializer.stringDeserializer(), "type");
		op.add(Builder::description, JsonpDeserializer.stringDeserializer(), "description");
		op.add(Builder::timeInNanos, JsonpDeserializer.longDeserializer(), "time_in_nanos");
		op.add(Builder::breakdown, FetchProfileBreakdown._DESERIALIZER, "breakdown");
		op.add(Builder::debug, FetchProfileDebug._DESERIALIZER, "debug");
		op.add(Builder::children, JsonpDeserializer.arrayDeserializer(FetchProfile._DESERIALIZER), "children");

	}

}
