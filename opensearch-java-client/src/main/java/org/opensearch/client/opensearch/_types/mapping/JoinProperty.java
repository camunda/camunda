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
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.mapping.JoinProperty


@JsonpDeserializable
public class JoinProperty extends PropertyBase implements PropertyVariant {
	private final Map<String, List<String>> relations;

	// ---------------------------------------------------------------------------------------------

	private JoinProperty(Builder builder) {
		super(builder);

		this.relations = ApiTypeHelper.unmodifiable(builder.relations);

	}

	public static JoinProperty of(Function<Builder, ObjectBuilder<JoinProperty>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Property variant kind.
	 */
	@Override
	public Property.Kind _propertyKind() {
		return Property.Kind.Join;
	}

	/**
	 * API name: {@code relations}
	 */
	public final Map<String, List<String>> relations() {
		return this.relations;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.write("type", "join");
		super.serializeInternal(generator, mapper);
		if (ApiTypeHelper.isDefined(this.relations)) {
			generator.writeKey("relations");
			generator.writeStartObject();
			for (Map.Entry<String, List<String>> item0 : this.relations.entrySet()) {
				generator.writeKey(item0.getKey());
				generator.writeStartArray();
				if (item0.getValue() != null) {
					for (String item1 : item0.getValue()) {
						generator.write(item1);

					}
				}
				generator.writeEnd();

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link JoinProperty}.
	 */

	public static class Builder extends PropertyBase.AbstractBuilder<Builder> implements ObjectBuilder<JoinProperty> {
		@Nullable
		private Map<String, List<String>> relations;

		/**
		 * API name: {@code relations}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>relations</code>.
		 */
		public final Builder relations(Map<String, List<String>> map) {
			this.relations = _mapPutAll(this.relations, map);
			return this;
		}

		/**
		 * API name: {@code relations}
		 * <p>
		 * Adds an entry to <code>relations</code>.
		 */
		public final Builder relations(String key, List<String> value) {
			this.relations = _mapPut(this.relations, key, value);
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link JoinProperty}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public JoinProperty build() {
			_checkSingleUse();

			return new JoinProperty(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link JoinProperty}
	 */
	public static final JsonpDeserializer<JoinProperty> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			JoinProperty::setupJoinPropertyDeserializer);

	protected static void setupJoinPropertyDeserializer(ObjectDeserializer<JoinProperty.Builder> op) {
		PropertyBase.setupPropertyBaseDeserializer(op);
		op.add(Builder::relations, JsonpDeserializer.stringMapDeserializer(
				JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer())), "relations");

		op.ignore("type");
	}

}
