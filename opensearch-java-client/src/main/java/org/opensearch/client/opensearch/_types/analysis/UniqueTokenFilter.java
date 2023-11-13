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

package org.opensearch.client.opensearch._types.analysis;

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.analysis.UniqueTokenFilter

@JsonpDeserializable
public class UniqueTokenFilter extends TokenFilterBase implements TokenFilterDefinitionVariant {
	@Nullable
	private final Boolean onlyOnSamePosition;

	// ---------------------------------------------------------------------------------------------

	private UniqueTokenFilter(Builder builder) {
		super(builder);

		this.onlyOnSamePosition = builder.onlyOnSamePosition;

	}

	public static UniqueTokenFilter of(Function<Builder, ObjectBuilder<UniqueTokenFilter>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * TokenFilterDefinition variant kind.
	 */
	@Override
	public TokenFilterDefinition.Kind _tokenFilterDefinitionKind() {
		return TokenFilterDefinition.Kind.Unique;
	}

	/**
	 * API name: {@code only_on_same_position}
	 */
	@Nullable
	public final Boolean onlyOnSamePosition() {
		return this.onlyOnSamePosition;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.write("type", "unique");
		super.serializeInternal(generator, mapper);
		if (this.onlyOnSamePosition != null) {
			generator.writeKey("only_on_same_position");
			generator.write(this.onlyOnSamePosition);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link UniqueTokenFilter}.
	 */

	public static class Builder extends TokenFilterBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<UniqueTokenFilter> {
		@Nullable
		private Boolean onlyOnSamePosition;

		/**
		 * API name: {@code only_on_same_position}
		 */
		public final Builder onlyOnSamePosition(@Nullable Boolean value) {
			this.onlyOnSamePosition = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link UniqueTokenFilter}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public UniqueTokenFilter build() {
			_checkSingleUse();

			return new UniqueTokenFilter(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link UniqueTokenFilter}
	 */
	public static final JsonpDeserializer<UniqueTokenFilter> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, UniqueTokenFilter::setupUniqueTokenFilterDeserializer);

	protected static void setupUniqueTokenFilterDeserializer(ObjectDeserializer<UniqueTokenFilter.Builder> op) {
		setupTokenFilterBaseDeserializer(op);
		op.add(Builder::onlyOnSamePosition, JsonpDeserializer.booleanDeserializer(), "only_on_same_position");

		op.ignore("type");
	}

}
