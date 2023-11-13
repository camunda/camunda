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

package org.opensearch.client.opensearch.indices;

import jakarta.json.stream.JsonGenerator;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;


@JsonpDeserializable
public class IndexSettingsMapping implements JsonpSerializable {

	@Nullable
	private final IndexSettingsMappingLimit totalFields;

	@Nullable
	private final IndexSettingsMappingLimit depth;

	@Nullable
	private final IndexSettingsMappingLimit nestedFields;

	@Nullable
	private final IndexSettingsMappingLimit nestedObjects;

	@Nullable
	private final IndexSettingsMappingLimit fieldNameLength;

	private IndexSettingsMapping(Builder builder) {

		this.totalFields = builder.totalFields;
		this.depth = builder.depth;
		this.nestedFields = builder.nestedFields;
		this.nestedObjects = builder.nestedObjects;
		this.fieldNameLength = builder.fieldNameLength;

	}

	public static IndexSettingsMapping of(Function<Builder, ObjectBuilder<IndexSettingsMapping>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code total_fields}
	 */
	@Nullable
	public final IndexSettingsMappingLimit totalFields() {
		return this.totalFields;
	}

	/**
	 * API name: {@code depth}
	 */
	@Nullable
	public final IndexSettingsMappingLimit depth() {
		return this.depth;
	}

	/**
	 * API name: {@code nested_fields}
	 */
	@Nullable
	public final IndexSettingsMappingLimit nestedFields() {
		return this.nestedFields;
	}

	/**
	 * API name: {@code nested_objects}
	 */
	@Nullable
	public final IndexSettingsMappingLimit nestedObjects() {
		return this.nestedObjects;
	}

	/**
	 * API name: {@code field_name_length}
	 */
	@Nullable
	public final IndexSettingsMappingLimit fieldNameLength() {
		return this.fieldNameLength;
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
		if (this.totalFields != null) {
			generator.writeKey("total_fields");
			this.totalFields.serialize(generator, mapper);

		}
		if (this.depth != null) {
			generator.writeKey("depth");
			this.depth.serialize(generator, mapper);

		}
		if (this.nestedFields != null) {
			generator.writeKey("nested_fields");
			this.nestedFields.serialize(generator, mapper);

		}
		if (this.nestedObjects != null) {
			generator.writeKey("nested_objects");
			this.nestedObjects.serialize(generator, mapper);

		}
		if (this.fieldNameLength != null) {
			generator.writeKey("field_name_length");
			this.fieldNameLength.serialize(generator, mapper);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link IndexSettingsMapping}.
	 */
	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<IndexSettingsMapping> {
		@Nullable
		private IndexSettingsMappingLimit totalFields;

		@Nullable
		private IndexSettingsMappingLimit depth;

		@Nullable
		private IndexSettingsMappingLimit nestedFields;

		@Nullable
		private IndexSettingsMappingLimit nestedObjects;

		@Nullable
		private IndexSettingsMappingLimit fieldNameLength;

		/**
		 * API name: {@code total_fields}
		 */
		public final Builder totalFields(@Nullable IndexSettingsMappingLimit value) {
			this.totalFields = value;
			return this;
		}

		/**
		 * API name: {@code total_fields}
		 */
		public final Builder totalFields(Function<IndexSettingsMappingLimit.Builder, ObjectBuilder<IndexSettingsMappingLimit>> fn) {
			return this.totalFields(fn.apply(new IndexSettingsMappingLimit.Builder()).build());
		}

		/**
		 * API name: {@code depth}
		 */
		public final Builder depth(@Nullable IndexSettingsMappingLimit value) {
			this.depth = value;
			return this;
		}

		/**
		 * API name: {@code depth}
		 */
		public final Builder depth(Function<IndexSettingsMappingLimit.Builder, ObjectBuilder<IndexSettingsMappingLimit>> fn) {
			return this.depth(fn.apply(new IndexSettingsMappingLimit.Builder()).build());
		}

		/**
		 * API name: {@code nested_fields}
		 */
		public final Builder nestedFields(@Nullable IndexSettingsMappingLimit value) {
			this.nestedFields = value;
			return this;
		}

		/**
		 * API name: {@code nested_fields}
		 */
		public final Builder nestedFields(Function<IndexSettingsMappingLimit.Builder, ObjectBuilder<IndexSettingsMappingLimit>> fn) {
			return this.nestedFields(fn.apply(new IndexSettingsMappingLimit.Builder()).build());
		}

		/**
		 * API name: {@code nested_objects}
		 */
		public final Builder nestedObjects(@Nullable IndexSettingsMappingLimit value) {
			this.nestedObjects = value;
			return this;
		}

		/**
		 * API name: {@code nested_objects}
		 */
		public final Builder nestedObjects(Function<IndexSettingsMappingLimit.Builder, ObjectBuilder<IndexSettingsMappingLimit>> fn) {
			return this.nestedObjects(fn.apply(new IndexSettingsMappingLimit.Builder()).build());
		}

		/**
		 * API name: {@code field_name_length}
		 */
		public final Builder fieldNameLength(@Nullable IndexSettingsMappingLimit value) {
			this.fieldNameLength = value;
			return this;
		}

		/**
		 * API name: {@code field_name_length}
		 */
		public final Builder fieldNameLength(Function<IndexSettingsMappingLimit.Builder, ObjectBuilder<IndexSettingsMappingLimit>> fn) {
			return this.fieldNameLength(fn.apply(new IndexSettingsMappingLimit.Builder()).build());
		}

		/**
		 * Builds a {@link IndexSettingsMapping}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public IndexSettingsMapping build() {
			_checkSingleUse();

			return new IndexSettingsMapping(this);
		}
	}

	/**
	 * Json deserializer for {@link IndexSettingsMapping}
	 */
	public static final JsonpDeserializer<IndexSettingsMapping> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			IndexSettingsMapping::setupIndexSettingsDeserializer);

	protected static void setupIndexSettingsDeserializer(ObjectDeserializer<IndexSettingsMapping.Builder> op) {

		op.add(Builder::totalFields, IndexSettingsMappingLimit._DESERIALIZER, "total_fields");
		op.add(Builder::depth, IndexSettingsMappingLimit._DESERIALIZER, "depth");
		op.add(Builder::nestedFields, IndexSettingsMappingLimit._DESERIALIZER, "nested_fields");
		op.add(Builder::nestedObjects, IndexSettingsMappingLimit._DESERIALIZER, "nested_objects");
		op.add(Builder::fieldNameLength, IndexSettingsMappingLimit._DESERIALIZER, "field_name_length");

	}

}
