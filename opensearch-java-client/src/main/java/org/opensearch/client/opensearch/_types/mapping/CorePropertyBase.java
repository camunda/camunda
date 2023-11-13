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

import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import jakarta.json.stream.JsonGenerator;
import java.util.List;
import javax.annotation.Nullable;

// typedef: _types.mapping.CorePropertyBase



public abstract class CorePropertyBase extends PropertyBase {
	private final List<String> copyTo;

	@Nullable
	private final String similarity;

	@Nullable
	private final Boolean store;

	// ---------------------------------------------------------------------------------------------

	protected CorePropertyBase(AbstractBuilder<?> builder) {
		super(builder);

		this.copyTo = ApiTypeHelper.unmodifiable(builder.copyTo);
		this.similarity = builder.similarity;
		this.store = builder.store;

	}

	/**
	 * API name: {@code copy_to}
	 */
	public final List<String> copyTo() {
		return this.copyTo;
	}

	/**
	 * API name: {@code similarity}
	 */
	@Nullable
	public final String similarity() {
		return this.similarity;
	}

	/**
	 * API name: {@code store}
	 */
	@Nullable
	public final Boolean store() {
		return this.store;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		if (ApiTypeHelper.isDefined(this.copyTo)) {
			generator.writeKey("copy_to");
			generator.writeStartArray();
			for (String item0 : this.copyTo) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (this.similarity != null) {
			generator.writeKey("similarity");
			generator.write(this.similarity);

		}
		if (this.store != null) {
			generator.writeKey("store");
			generator.write(this.store);

		}

	}

	protected abstract static class AbstractBuilder<BuilderT extends AbstractBuilder<BuilderT>>
			extends
				PropertyBase.AbstractBuilder<BuilderT> {
		@Nullable
		private List<String> copyTo;

		@Nullable
		private String similarity;

		@Nullable
		private Boolean store;

		/**
		 * API name: {@code copy_to}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>copyTo</code>.
		 */
		public final BuilderT copyTo(List<String> list) {
			this.copyTo = _listAddAll(this.copyTo, list);
			return self();
		}

		/**
		 * API name: {@code copy_to}
		 * <p>
		 * Adds one or more values to <code>copyTo</code>.
		 */
		public final BuilderT copyTo(String value, String... values) {
			this.copyTo = _listAdd(this.copyTo, value, values);
			return self();
		}

		/**
		 * API name: {@code similarity}
		 */
		public final BuilderT similarity(@Nullable String value) {
			this.similarity = value;
			return self();
		}

		/**
		 * API name: {@code store}
		 */
		public final BuilderT store(@Nullable Boolean value) {
			this.store = value;
			return self();
		}

	}

	// ---------------------------------------------------------------------------------------------
	protected static <BuilderT extends AbstractBuilder<BuilderT>> void setupCorePropertyBaseDeserializer(
			ObjectDeserializer<BuilderT> op) {
		PropertyBase.setupPropertyBaseDeserializer(op);
		op.add(AbstractBuilder::copyTo, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"copy_to");
		op.add(AbstractBuilder::similarity, JsonpDeserializer.stringDeserializer(), "similarity");
		op.add(AbstractBuilder::store, JsonpDeserializer.booleanDeserializer(), "store");

	}

}
