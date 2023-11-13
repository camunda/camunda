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
 *	 http://www.apache.org/licenses/LICENSE-2.0
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

package org.opensearch.client.opensearch.core.bulk;

import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializer;
import org.opensearch.client.json.NdJsonpSerializable;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _global.bulk.UpdateOperation



public class UpdateOperation<TDocument> extends BulkOperationBase implements NdJsonpSerializable, BulkOperationVariant {
	private final UpdateOperationData<TDocument> data;

	@Nullable
	private final Boolean requireAlias;

	@Nullable
	private final Integer retryOnConflict;

	@Nullable
	private final JsonpSerializer<TDocument> tDocumentSerializer;

	// ---------------------------------------------------------------------------------------------

	private UpdateOperation(Builder<TDocument> builder) {
		super(builder);
		this.data = ApiTypeHelper.requireNonNull(builder.data, this, "data");
		this.requireAlias = builder.requireAlias;
		this.retryOnConflict = builder.retryOnConflict;
		this.tDocumentSerializer = builder.tDocumentSerializer;

	}

	public static <TDocument> UpdateOperation<TDocument> of(
			Function<Builder<TDocument>, ObjectBuilder<UpdateOperation<TDocument>>> fn) {
		return fn.apply(new Builder<>()).build();
	}

	/**
	 * BulkOperation variant kind.
	 */
	@Override
	public BulkOperation.Kind _bulkOperationKind() {
		return BulkOperation.Kind.Update;
	}

	@Override
	public Iterator<?> _serializables() {
		return Arrays.asList(this, this.data).iterator();
	}

	/**
	 * API name: {@code require_alias}
	 */
	@Nullable
	public final Boolean requireAlias() {
		return this.requireAlias;
	}

	/**
	 * API name: {@code retry_on_conflict}
	 */
	@Nullable
	public final Integer retryOnConflict() {
		return this.retryOnConflict;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		if (this.requireAlias != null) {
			generator.writeKey("require_alias");
			generator.write(this.requireAlias);

		}
		if (this.retryOnConflict != null) {
			generator.writeKey("retry_on_conflict");
			generator.write(this.retryOnConflict);

		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link UpdateOperation}.
	 */

	public static class Builder<TDocument> extends BulkOperationBase.AbstractBuilder<Builder<TDocument>>
			implements
				ObjectBuilder<UpdateOperation<TDocument>> {

		private UpdateOperationData<TDocument> data;

		@Nullable
		private TDocument document;

		@Nullable
		private Boolean requireAlias;

		@Nullable
		private Integer retryOnConflict;

		@Nullable
		private JsonpSerializer<TDocument> tDocumentSerializer;

		@Nullable
		private Boolean docAsUpsert;

		@Nullable
		private TDocument upsert;
		
		@Nullable
		private Script script;

		/**
		 * API name: {@code document}
		 */
		public final Builder<TDocument> document(TDocument value) {
			this.document = value;
			return this;
		}

		/**
		 * API name: {@code docAsUpsert}
		 */
		public final Builder<TDocument> docAsUpsert(@Nullable Boolean value) {
			this.docAsUpsert = value;
			return this;
		}

		/**
		 * API name: {@code upsert}
		 */
		public final Builder<TDocument> upsert(@Nullable TDocument value) {
			this.upsert = value;
			return this;
		}

		/**
		 * API name: {@code script}
		 */
		public final Builder<TDocument> script(@Nullable Script value) {
			this.script = value;
			return this;
		}

		/**
		 * API name: {@code require_alias}
		 */
		public final Builder<TDocument> requireAlias(@Nullable Boolean value) {
			this.requireAlias = value;
			return this;
		}

		/**
		 * API name: {@code retry_on_conflict}
		 */
		public final Builder<TDocument> retryOnConflict(@Nullable Integer value) {
			this.retryOnConflict = value;
			return this;
		}

		/**
		 * Serializer for TDocument. If not set, an attempt will be made to find a
		 * serializer from the JSON context.
		 */
		public final Builder<TDocument> tDocumentSerializer(@Nullable JsonpSerializer<TDocument> value) {
			this.tDocumentSerializer = value;
			return this;
		}

		@Override
		protected Builder<TDocument> self() {
			return this;
		}

		/**
		 * Builds a {@link UpdateOperation}.
		 *
		 * @throws NullPointerException
		 *			 if some of the required fields are null.
		 */
		public UpdateOperation<TDocument> build() {
			_checkSingleUse();

			data = new UpdateOperationData.Builder<TDocument>()
					.document(document)
					.docAsUpsert(docAsUpsert)
					.script(script)
					.upsert(upsert)
					.tDocumentSerializer(tDocumentSerializer)
					.build();

			return new UpdateOperation<TDocument>(this);
		}
	}

}
