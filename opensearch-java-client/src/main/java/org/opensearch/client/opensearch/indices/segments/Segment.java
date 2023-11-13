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

package org.opensearch.client.opensearch.indices.segments;

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
import java.util.Map;
import java.util.function.Function;

// typedef: indices.segments.Segment


@JsonpDeserializable
public class Segment implements JsonpSerializable {
	private final Map<String, String> attributes;

	private final boolean committed;

	private final boolean compound;

	private final long deletedDocs;

	private final int generation;

	private final double memoryInBytes;

	private final boolean search;

	private final double sizeInBytes;

	private final long numDocs;

	private final String version;

	// ---------------------------------------------------------------------------------------------

	private Segment(Builder builder) {

		this.attributes = ApiTypeHelper.unmodifiableRequired(builder.attributes, this, "attributes");
		this.committed = ApiTypeHelper.requireNonNull(builder.committed, this, "committed");
		this.compound = ApiTypeHelper.requireNonNull(builder.compound, this, "compound");
		this.deletedDocs = ApiTypeHelper.requireNonNull(builder.deletedDocs, this, "deletedDocs");
		this.generation = ApiTypeHelper.requireNonNull(builder.generation, this, "generation");
		this.memoryInBytes = ApiTypeHelper.requireNonNull(builder.memoryInBytes, this, "memoryInBytes");
		this.search = ApiTypeHelper.requireNonNull(builder.search, this, "search");
		this.sizeInBytes = ApiTypeHelper.requireNonNull(builder.sizeInBytes, this, "sizeInBytes");
		this.numDocs = ApiTypeHelper.requireNonNull(builder.numDocs, this, "numDocs");
		this.version = ApiTypeHelper.requireNonNull(builder.version, this, "version");

	}

	public static Segment of(Function<Builder, ObjectBuilder<Segment>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code attributes}
	 */
	public final Map<String, String> attributes() {
		return this.attributes;
	}

	/**
	 * Required - API name: {@code committed}
	 */
	public final boolean committed() {
		return this.committed;
	}

	/**
	 * Required - API name: {@code compound}
	 */
	public final boolean compound() {
		return this.compound;
	}

	/**
	 * Required - API name: {@code deleted_docs}
	 */
	public final long deletedDocs() {
		return this.deletedDocs;
	}

	/**
	 * Required - API name: {@code generation}
	 */
	public final int generation() {
		return this.generation;
	}

	/**
	 * Required - API name: {@code memory_in_bytes}
	 */
	public final double memoryInBytes() {
		return this.memoryInBytes;
	}

	/**
	 * Required - API name: {@code search}
	 */
	public final boolean search() {
		return this.search;
	}

	/**
	 * Required - API name: {@code size_in_bytes}
	 */
	public final double sizeInBytes() {
		return this.sizeInBytes;
	}

	/**
	 * Required - API name: {@code num_docs}
	 */
	public final long numDocs() {
		return this.numDocs;
	}

	/**
	 * Required - API name: {@code version}
	 */
	public final String version() {
		return this.version;
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

		if (ApiTypeHelper.isDefined(this.attributes)) {
			generator.writeKey("attributes");
			generator.writeStartObject();
			for (Map.Entry<String, String> item0 : this.attributes.entrySet()) {
				generator.writeKey(item0.getKey());
				generator.write(item0.getValue());

			}
			generator.writeEnd();

		}
		generator.writeKey("committed");
		generator.write(this.committed);

		generator.writeKey("compound");
		generator.write(this.compound);

		generator.writeKey("deleted_docs");
		generator.write(this.deletedDocs);

		generator.writeKey("generation");
		generator.write(this.generation);

		generator.writeKey("memory_in_bytes");
		generator.write(this.memoryInBytes);

		generator.writeKey("search");
		generator.write(this.search);

		generator.writeKey("size_in_bytes");
		generator.write(this.sizeInBytes);

		generator.writeKey("num_docs");
		generator.write(this.numDocs);

		generator.writeKey("version");
		generator.write(this.version);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link Segment}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Segment> {
		private Map<String, String> attributes;

		private Boolean committed;

		private Boolean compound;

		private Long deletedDocs;

		private Integer generation;

		private Double memoryInBytes;

		private Boolean search;

		private Double sizeInBytes;

		private Long numDocs;

		private String version;

		/**
		 * Required - API name: {@code attributes}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>attributes</code>.
		 */
		public final Builder attributes(Map<String, String> map) {
			this.attributes = _mapPutAll(this.attributes, map);
			return this;
		}

		/**
		 * Required - API name: {@code attributes}
		 * <p>
		 * Adds an entry to <code>attributes</code>.
		 */
		public final Builder attributes(String key, String value) {
			this.attributes = _mapPut(this.attributes, key, value);
			return this;
		}

		/**
		 * Required - API name: {@code committed}
		 */
		public final Builder committed(boolean value) {
			this.committed = value;
			return this;
		}

		/**
		 * Required - API name: {@code compound}
		 */
		public final Builder compound(boolean value) {
			this.compound = value;
			return this;
		}

		/**
		 * Required - API name: {@code deleted_docs}
		 */
		public final Builder deletedDocs(long value) {
			this.deletedDocs = value;
			return this;
		}

		/**
		 * Required - API name: {@code generation}
		 */
		public final Builder generation(int value) {
			this.generation = value;
			return this;
		}

		/**
		 * Required - API name: {@code memory_in_bytes}
		 */
		public final Builder memoryInBytes(double value) {
			this.memoryInBytes = value;
			return this;
		}

		/**
		 * Required - API name: {@code search}
		 */
		public final Builder search(boolean value) {
			this.search = value;
			return this;
		}

		/**
		 * Required - API name: {@code size_in_bytes}
		 */
		public final Builder sizeInBytes(double value) {
			this.sizeInBytes = value;
			return this;
		}

		/**
		 * Required - API name: {@code num_docs}
		 */
		public final Builder numDocs(long value) {
			this.numDocs = value;
			return this;
		}

		/**
		 * Required - API name: {@code version}
		 */
		public final Builder version(String value) {
			this.version = value;
			return this;
		}

		/**
		 * Builds a {@link Segment}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public Segment build() {
			_checkSingleUse();

			return new Segment(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link Segment}
	 */
	public static final JsonpDeserializer<Segment> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			Segment::setupSegmentDeserializer);

	protected static void setupSegmentDeserializer(ObjectDeserializer<Segment.Builder> op) {

		op.add(Builder::attributes, JsonpDeserializer.stringMapDeserializer(JsonpDeserializer.stringDeserializer()),
				"attributes");
		op.add(Builder::committed, JsonpDeserializer.booleanDeserializer(), "committed");
		op.add(Builder::compound, JsonpDeserializer.booleanDeserializer(), "compound");
		op.add(Builder::deletedDocs, JsonpDeserializer.longDeserializer(), "deleted_docs");
		op.add(Builder::generation, JsonpDeserializer.integerDeserializer(), "generation");
		op.add(Builder::memoryInBytes, JsonpDeserializer.doubleDeserializer(), "memory_in_bytes");
		op.add(Builder::search, JsonpDeserializer.booleanDeserializer(), "search");
		op.add(Builder::sizeInBytes, JsonpDeserializer.doubleDeserializer(), "size_in_bytes");
		op.add(Builder::numDocs, JsonpDeserializer.longDeserializer(), "num_docs");
		op.add(Builder::version, JsonpDeserializer.stringDeserializer(), "version");

	}

}
