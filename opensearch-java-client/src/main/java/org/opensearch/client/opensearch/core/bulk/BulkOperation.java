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

package org.opensearch.client.opensearch.core.bulk;

import org.opensearch.client.json.JsonEnum;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.NdJsonpSerializable;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import org.opensearch.client.util.TaggedUnion;
import org.opensearch.client.util.TaggedUnionUtils;
import jakarta.json.stream.JsonGenerator;

import java.util.Iterator;
import java.util.function.Function;

// typedef: _global.bulk.OperationContainer



public class BulkOperation implements TaggedUnion<BulkOperation.Kind, Object>, NdJsonpSerializable, JsonpSerializable {

	/**
	 * {@link BulkOperation} variant kinds.
	 */
	/**
	 * {@link BulkOperation} variant kinds.
	 */

	public enum Kind implements JsonEnum {
		Index("index"),

		Create("create"),

		Update("update"),

		Delete("delete"),

		;

		private final String jsonValue;

		Kind(String jsonValue) {
			this.jsonValue = jsonValue;
		}

		public String jsonValue() {
			return this.jsonValue;
		}

	}

	private final Kind _kind;
	private final Object _value;

	@Override
	public final Kind _kind() {
		return _kind;
	}

	@Override
	public final Object _get() {
		return _value;
	}

	public BulkOperation(BulkOperationVariant value) {

		this._kind = ApiTypeHelper.requireNonNull(value._bulkOperationKind(), this, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(value, this, "<variant value>");

	}

	private BulkOperation(Builder builder) {

		this._kind = ApiTypeHelper.requireNonNull(builder._kind, builder, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(builder._value, builder, "<variant value>");

	}

	public static BulkOperation of(Function<Builder, ObjectBuilder<BulkOperation>> fn) {
		return fn.apply(new Builder()).build();
	}

	@Override
	public Iterator<?> _serializables() {
		return TaggedUnionUtils.ndJsonIterator(this);
	}

	/**
	 * Is this variant instance of kind {@code index}?
	 */
	public boolean isIndex() {
		return _kind == Kind.Index;
	}

	/**
	 * Get the {@code index} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code index} kind.
	 */
	public <TDocument> IndexOperation<TDocument> index() {
		return TaggedUnionUtils.get(this, Kind.Index);
	}

	/**
	 * Is this variant instance of kind {@code create}?
	 */
	public boolean isCreate() {
		return _kind == Kind.Create;
	}

	/**
	 * Get the {@code create} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code create} kind.
	 */
	public <TDocument> CreateOperation<TDocument> create() {
		return TaggedUnionUtils.get(this, Kind.Create);
	}

	/**
	 * Is this variant instance of kind {@code update}?
	 */
	public boolean isUpdate() {
		return _kind == Kind.Update;
	}

	/**
	 * Get the {@code update} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code update} kind.
	 */
	public <TDocument> UpdateOperation<TDocument> update() {
		return TaggedUnionUtils.get(this, Kind.Update);
	}

	/**
	 * Is this variant instance of kind {@code delete}?
	 */
	public boolean isDelete() {
		return _kind == Kind.Delete;
	}

	/**
	 * Get the {@code delete} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code delete} kind.
	 */
	public DeleteOperation delete() {
		return TaggedUnionUtils.get(this, Kind.Delete);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {

		generator.writeStartObject();

		generator.writeKey(_kind.jsonValue());
		if (_value instanceof JsonpSerializable) {
			((JsonpSerializable) _value).serialize(generator, mapper);
		}

		generator.writeEnd();

	}

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<BulkOperation> {
		private Kind _kind;
		private Object _value;

		public <TDocument> ObjectBuilder<BulkOperation> index(IndexOperation<TDocument> v) {
			this._kind = Kind.Index;
			this._value = v;
			return this;
		}

		public <TDocument> ObjectBuilder<BulkOperation> index(
				Function<IndexOperation.Builder<TDocument>, ObjectBuilder<IndexOperation<TDocument>>> fn) {
			return this.index(fn.apply(new IndexOperation.Builder<TDocument>()).build());
		}

		public <TDocument> ObjectBuilder<BulkOperation> create(CreateOperation<TDocument> v) {
			this._kind = Kind.Create;
			this._value = v;
			return this;
		}

		public <TDocument> ObjectBuilder<BulkOperation> create(
				Function<CreateOperation.Builder<TDocument>, ObjectBuilder<CreateOperation<TDocument>>> fn) {
			return this.create(fn.apply(new CreateOperation.Builder<TDocument>()).build());
		}

		public <TDocument> ObjectBuilder<BulkOperation> update(UpdateOperation<TDocument> v) {
			this._kind = Kind.Update;
			this._value = v;
			return this;
		}

		public <TDocument> ObjectBuilder<BulkOperation> update(
				Function<UpdateOperation.Builder<TDocument>, ObjectBuilder<UpdateOperation<TDocument>>> fn) {
			return this.update(fn.apply(new UpdateOperation.Builder<TDocument>()).build());
		}

		public ObjectBuilder<BulkOperation> delete(DeleteOperation v) {
			this._kind = Kind.Delete;
			this._value = v;
			return this;
		}

		public ObjectBuilder<BulkOperation> delete(
				Function<DeleteOperation.Builder, ObjectBuilder<DeleteOperation>> fn) {
			return this.delete(fn.apply(new DeleteOperation.Builder()).build());
		}

		public BulkOperation build() {
			_checkSingleUse();
			return new BulkOperation(this);
		}

	}

}
