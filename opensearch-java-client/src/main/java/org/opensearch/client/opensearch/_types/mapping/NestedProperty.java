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

package org.opensearch.client.opensearch._types.mapping;

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.mapping.NestedProperty


@JsonpDeserializable
public class NestedProperty extends CorePropertyBase implements PropertyVariant {
	@Nullable
	private final Boolean enabled;

	@Nullable
	private final Boolean includeInParent;

	@Nullable
	private final Boolean includeInRoot;

	// ---------------------------------------------------------------------------------------------

	private NestedProperty(Builder builder) {
		super(builder);

		this.enabled = builder.enabled;
		this.includeInParent = builder.includeInParent;
		this.includeInRoot = builder.includeInRoot;

	}

	public static NestedProperty of(Function<Builder, ObjectBuilder<NestedProperty>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Property variant kind.
	 */
	@Override
	public Property.Kind _propertyKind() {
		return Property.Kind.Nested;
	}

	/**
	 * API name: {@code enabled}
	 */
	@Nullable
	public final Boolean enabled() {
		return this.enabled;
	}

	/**
	 * API name: {@code include_in_parent}
	 */
	@Nullable
	public final Boolean includeInParent() {
		return this.includeInParent;
	}

	/**
	 * API name: {@code include_in_root}
	 */
	@Nullable
	public final Boolean includeInRoot() {
		return this.includeInRoot;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.write("type", "nested");
		super.serializeInternal(generator, mapper);
		if (this.enabled != null) {
			generator.writeKey("enabled");
			generator.write(this.enabled);

		}
		if (this.includeInParent != null) {
			generator.writeKey("include_in_parent");
			generator.write(this.includeInParent);

		}
		if (this.includeInRoot != null) {
			generator.writeKey("include_in_root");
			generator.write(this.includeInRoot);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link NestedProperty}.
	 */

	public static class Builder extends CorePropertyBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<NestedProperty> {
		@Nullable
		private Boolean enabled;

		@Nullable
		private Boolean includeInParent;

		@Nullable
		private Boolean includeInRoot;

		/**
		 * API name: {@code enabled}
		 */
		public final Builder enabled(@Nullable Boolean value) {
			this.enabled = value;
			return this;
		}

		/**
		 * API name: {@code include_in_parent}
		 */
		public final Builder includeInParent(@Nullable Boolean value) {
			this.includeInParent = value;
			return this;
		}

		/**
		 * API name: {@code include_in_root}
		 */
		public final Builder includeInRoot(@Nullable Boolean value) {
			this.includeInRoot = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link NestedProperty}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public NestedProperty build() {
			_checkSingleUse();

			return new NestedProperty(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link NestedProperty}
	 */
	public static final JsonpDeserializer<NestedProperty> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			NestedProperty::setupNestedPropertyDeserializer);

	protected static void setupNestedPropertyDeserializer(ObjectDeserializer<NestedProperty.Builder> op) {
		setupCorePropertyBaseDeserializer(op);
		op.add(Builder::enabled, JsonpDeserializer.booleanDeserializer(), "enabled");
		op.add(Builder::includeInParent, JsonpDeserializer.booleanDeserializer(), "include_in_parent");
		op.add(Builder::includeInRoot, JsonpDeserializer.booleanDeserializer(), "include_in_root");

		op.ignore("type");
	}

}
