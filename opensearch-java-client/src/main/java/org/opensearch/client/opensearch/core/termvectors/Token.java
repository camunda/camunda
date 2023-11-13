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

package org.opensearch.client.opensearch.core.termvectors;

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
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _global.termvectors.Token


@JsonpDeserializable
public class Token implements JsonpSerializable {
	@Nullable
	private final Integer endOffset;

	@Nullable
	private final String payload;

	private final int position;

	@Nullable
	private final Integer startOffset;

	// ---------------------------------------------------------------------------------------------

	private Token(Builder builder) {

		this.endOffset = builder.endOffset;
		this.payload = builder.payload;
		this.position = ApiTypeHelper.requireNonNull(builder.position, this, "position");
		this.startOffset = builder.startOffset;

	}

	public static Token of(Function<Builder, ObjectBuilder<Token>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code end_offset}
	 */
	@Nullable
	public final Integer endOffset() {
		return this.endOffset;
	}

	/**
	 * API name: {@code payload}
	 */
	@Nullable
	public final String payload() {
		return this.payload;
	}

	/**
	 * Required - API name: {@code position}
	 */
	public final int position() {
		return this.position;
	}

	/**
	 * API name: {@code start_offset}
	 */
	@Nullable
	public final Integer startOffset() {
		return this.startOffset;
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

		if (this.endOffset != null) {
			generator.writeKey("end_offset");
			generator.write(this.endOffset);

		}
		if (this.payload != null) {
			generator.writeKey("payload");
			generator.write(this.payload);

		}
		generator.writeKey("position");
		generator.write(this.position);

		if (this.startOffset != null) {
			generator.writeKey("start_offset");
			generator.write(this.startOffset);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link Token}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Token> {
		@Nullable
		private Integer endOffset;

		@Nullable
		private String payload;

		private Integer position;

		@Nullable
		private Integer startOffset;

		/**
		 * API name: {@code end_offset}
		 */
		public final Builder endOffset(@Nullable Integer value) {
			this.endOffset = value;
			return this;
		}

		/**
		 * API name: {@code payload}
		 */
		public final Builder payload(@Nullable String value) {
			this.payload = value;
			return this;
		}

		/**
		 * Required - API name: {@code position}
		 */
		public final Builder position(int value) {
			this.position = value;
			return this;
		}

		/**
		 * API name: {@code start_offset}
		 */
		public final Builder startOffset(@Nullable Integer value) {
			this.startOffset = value;
			return this;
		}

		/**
		 * Builds a {@link Token}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public Token build() {
			_checkSingleUse();

			return new Token(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link Token}
	 */
	public static final JsonpDeserializer<Token> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			Token::setupTokenDeserializer);

	protected static void setupTokenDeserializer(ObjectDeserializer<Token.Builder> op) {

		op.add(Builder::endOffset, JsonpDeserializer.integerDeserializer(), "end_offset");
		op.add(Builder::payload, JsonpDeserializer.stringDeserializer(), "payload");
		op.add(Builder::position, JsonpDeserializer.integerDeserializer(), "position");
		op.add(Builder::startOffset, JsonpDeserializer.integerDeserializer(), "start_offset");

	}

}
