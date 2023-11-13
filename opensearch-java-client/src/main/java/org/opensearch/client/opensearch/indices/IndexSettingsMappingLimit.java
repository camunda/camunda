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
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;

import javax.annotation.Nullable;
import java.util.function.Function;

@JsonpDeserializable
public class IndexSettingsMappingLimit implements JsonpSerializable {

	@Nullable
	private final Long limit;

	private IndexSettingsMappingLimit(Builder builder) {

		this.limit = builder.limit;

	}

	public static IndexSettingsMappingLimit of(Function<Builder, ObjectBuilder<IndexSettingsMappingLimit>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code limit}
	 */
	@Nullable
	public final Long limit() {
		return this.limit;
	}

	/**
	 * Serialize this object to JSON.
	 */
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {
		generator.writeStartObject();
		serializeInternal(generator);
		generator.writeEnd();
	}

	protected void serializeInternal(JsonGenerator generator) {

		if (this.limit != null) {
			generator.writeKey("limit");
			generator.write(this.limit);
		}

	}

	/**
	 * Builder for {@link IndexSettingsMappingLimit}.
	 */
	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<IndexSettingsMappingLimit> {
		@Nullable
		private Long limit;

		/**
		 * API name: {@code limit}
		 */
		public final Builder limit(@Nullable Long value) {
			this.limit = value;
			return this;
		}

		/**
		 * Builds a {@link IndexSettingsMappingLimit}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public IndexSettingsMappingLimit build() {
			_checkSingleUse();

			return new IndexSettingsMappingLimit(this);
		}
	}

	/**
	 * Json deserializer for {@link IndexSettingsMappingLimit}
	 */
	public static final JsonpDeserializer<IndexSettingsMappingLimit> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			IndexSettingsMappingLimit::setupIndexSettingsMappingLimitDeserializer);

	protected static void setupIndexSettingsMappingLimitDeserializer(ObjectDeserializer<IndexSettingsMappingLimit.Builder> op) {

		op.add(Builder::limit, JsonpDeserializer.longDeserializer(), "limit");

	}

}
