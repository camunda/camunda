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

package org.opensearch.client.opensearch._types.query_dsl;

import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.JsonpUtils;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Consumer;

@JsonpDeserializable
public class SpanGapQuery implements SpanQueryVariant, JsonpSerializable {

	private final String field;

	private final int spanWidth;

	@Override
	public SpanQuery.Kind _spanQueryKind() {
		return SpanQuery.Kind.SpanGap;
	}

	private SpanGapQuery(SpanGapQuery.Builder builder) {
		this.field = Objects.requireNonNull(builder.field, "field");
		this.spanWidth = Objects.requireNonNull(builder.spanWidth, "span_width");
	}

	public static SpanGapQuery of(Consumer<SpanGapQuery.Builder> fn) {
		Builder builder = new Builder();
		fn.accept(builder);
		return builder.build();
	}

	public final String field() {
		return this.field;
	}

	public final int spanWidth() {
		return this.spanWidth;
	}

	@Override
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {
		generator.writeStartObject();
		generator.write(this.field, this.spanWidth);
		generator.writeEnd();
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link SpanGapQuery}.
	 */
	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<SpanGapQuery> {

		private String field;
		private Integer spanWidth;

		public final SpanGapQuery.Builder field(String value) {
			this.field = value;
			return this;
		}

		public final SpanGapQuery.Builder spanWidth(int value) {
			this.spanWidth = value;
			return this;
		}

		@Override
		public SpanGapQuery build() {
			_checkSingleUse();
			return new SpanGapQuery(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link SpanGapQuery}
	 */
	public static final JsonpDeserializer<SpanGapQuery> _DESERIALIZER = JsonpDeserializer
			.of(EnumSet.of(JsonParser.Event.START_OBJECT), (parser, mapper) -> {
				JsonpUtils.expectNextEvent(parser, JsonParser.Event.START_OBJECT);
				String name = JsonpUtils.expectKeyName(parser, parser.next());

				JsonpUtils.expectNextEvent(parser, JsonParser.Event.VALUE_NUMBER);
				int spanWidth = parser.getInt();

				JsonpUtils.expectNextEvent(parser, JsonParser.Event.END_OBJECT);

				return new Builder().field(name).spanWidth(spanWidth).build();
			});
}
