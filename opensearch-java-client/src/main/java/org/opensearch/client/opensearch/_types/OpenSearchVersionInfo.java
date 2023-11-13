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

package org.opensearch.client.opensearch._types;

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
import java.util.function.Function;

@JsonpDeserializable
public final class OpenSearchVersionInfo implements JsonpSerializable {
	private final String buildDate;

	private final String buildFlavor;

	private final String buildHash;

	private final boolean buildSnapshot;

	private final String buildType;

	private final String distribution;

	private final String luceneVersion;

	private final String minimumIndexCompatibilityVersion;

	private final String minimumWireCompatibilityVersion;

	private final String number;

	// ---------------------------------------------------------------------------------------------

	private OpenSearchVersionInfo(Builder builder) {

		this.buildDate = ApiTypeHelper.requireNonNull(builder.buildDate, this, "buildDate");
		this.buildFlavor = builder.buildFlavor;
		this.buildHash = ApiTypeHelper.requireNonNull(builder.buildHash, this, "buildHash");
		this.buildSnapshot = ApiTypeHelper.requireNonNull(builder.buildSnapshot, this, "buildSnapshot");
		this.buildType = ApiTypeHelper.requireNonNull(builder.buildType, this, "buildType");
		this.distribution = ApiTypeHelper.requireNonNull(builder.distribution, this, "distribution");
		this.luceneVersion = ApiTypeHelper.requireNonNull(builder.luceneVersion, this, "luceneVersion");
		this.minimumIndexCompatibilityVersion = ApiTypeHelper.requireNonNull(builder.minimumIndexCompatibilityVersion,
				this, "minimumIndexCompatibilityVersion");
		this.minimumWireCompatibilityVersion = ApiTypeHelper.requireNonNull(builder.minimumWireCompatibilityVersion,
				this, "minimumWireCompatibilityVersion");
		this.number = ApiTypeHelper.requireNonNull(builder.number, this, "number");

	}

	public static OpenSearchVersionInfo of(Function<Builder, ObjectBuilder<OpenSearchVersionInfo>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code build_date}
	 */
	public String buildDate() {
		return this.buildDate;
	}

	/**
	 * API name: {@code build_flavor}
	 */
	public String buildFlavor() {
		return this.buildFlavor;
	}

	/**
	 * API name: {@code build_hash}
	 */
	public String buildHash() {
		return this.buildHash;
	}

	/**
	 * Required - API name: {@code build_snapshot}
	 */
	public boolean buildSnapshot() {
		return this.buildSnapshot;
	}

	/**
	 * Required - API name: {@code build_type}
	 */
	public String buildType() {
		return this.buildType;
	}

	/**
	 * API name: {@code distribution}
	 */
	public String distribution() {
		return this.distribution;
	}

	/**
	 * API name: {@code lucene_version}
	 */
	public String luceneVersion() {
		return this.luceneVersion;
	}

	/**
	 * Required - API name: {@code minimum_index_compatibility_version}
	 */
	public String minimumIndexCompatibilityVersion() {
		return this.minimumIndexCompatibilityVersion;
	}

	/**
	 * Required - API name: {@code minimum_wire_compatibility_version}
	 */
	public String minimumWireCompatibilityVersion() {
		return this.minimumWireCompatibilityVersion;
	}

	/**
	 * Required - API name: {@code number}
	 */
	public String number() {
		return this.number;
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

		generator.writeKey("build_date");
		generator.write(this.buildDate);

		generator.writeKey("build_flavor");
		generator.write(this.buildFlavor);

		generator.writeKey("build_hash");
		generator.write(this.buildHash);

		generator.writeKey("build_snapshot");
		generator.write(this.buildSnapshot);

		generator.writeKey("build_type");
		generator.write(this.buildType);

		generator.writeKey("distribution");
		generator.write(this.distribution);

		generator.writeKey("lucene_version");
		generator.write(this.luceneVersion);

		generator.writeKey("minimum_index_compatibility_version");
		generator.write(this.minimumIndexCompatibilityVersion);

		generator.writeKey("minimum_wire_compatibility_version");
		generator.write(this.minimumWireCompatibilityVersion);

		generator.writeKey("number");
		generator.write(this.number);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link OpenSearchVersionInfo}.
	 */
	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<OpenSearchVersionInfo> {
		private String buildDate;

		private String buildFlavor;

		private String buildHash;

		private Boolean buildSnapshot;

		private String buildType;

		private String distribution;

		private String luceneVersion;

		private String minimumIndexCompatibilityVersion;

		private String minimumWireCompatibilityVersion;

		private String number;

		/**
		 * Required - API name: {@code build_date}
		 */
		public final Builder buildDate(String value) {
			this.buildDate = value;
			return this;
		}

		/**
		 * API name: {@code build_flavor}
		 */
		public Builder buildFlavor(String value) {
			this.buildFlavor = value;
			return this;
		}

		/**
		 * API name: {@code build_hash}
		 */
		public final Builder buildHash(String value) {
			this.buildHash = value;
			return this;
		}

		/**
		 * Required - API name: {@code build_snapshot}
		 */
		public final Builder buildSnapshot(boolean value) {
			this.buildSnapshot = value;
			return this;
		}

		/**
		 * Required - API name: {@code build_type}
		 */
		public final Builder buildType(String value) {
			this.buildType = value;
			return this;
		}

		/**
		 * API name: {@code distribution}
		 */
		public Builder distribution(String value) {
			this.distribution = value;
			return this;
		}

		/**
		 * API name: {@code lucene_version}
		 */
		public final Builder luceneVersion(String value) {
			this.luceneVersion = value;
			return this;
		}

		/**
		 * Required - API name: {@code minimum_index_compatibility_version}
		 */
		public final Builder minimumIndexCompatibilityVersion(String value) {
			this.minimumIndexCompatibilityVersion = value;
			return this;
		}

		/**
		 * Required - API name: {@code minimum_wire_compatibility_version}
		 */
		public final Builder minimumWireCompatibilityVersion(String value) {
			this.minimumWireCompatibilityVersion = value;
			return this;
		}

		/**
		 * Required - API name: {@code number}
		 */
		public final Builder number(String value) {
			this.number = value;
			return this;
		}

		/**
		 * Builds a {@link OpenSearchVersionInfo}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */

		public OpenSearchVersionInfo build() {
			_checkSingleUse();
			return new OpenSearchVersionInfo(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for OpenSearchVersionInfo
	 */
	public static final JsonpDeserializer<OpenSearchVersionInfo> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, OpenSearchVersionInfo::setupOpenSearchVersionInfoDeserializer);

	protected static void setupOpenSearchVersionInfoDeserializer(
			ObjectDeserializer<OpenSearchVersionInfo.Builder> op) {

		op.add(Builder::buildDate, JsonpDeserializer.stringDeserializer(), "build_date");
		op.add(Builder::buildFlavor, JsonpDeserializer.stringDeserializer(), "build_flavor");
		op.add(Builder::buildHash, JsonpDeserializer.stringDeserializer(), "build_hash");
		op.add(Builder::buildSnapshot, JsonpDeserializer.booleanDeserializer(), "build_snapshot");
		op.add(Builder::buildType, JsonpDeserializer.stringDeserializer(), "build_type");
		op.add(Builder::distribution, JsonpDeserializer.stringDeserializer(), "distribution");
		op.add(Builder::luceneVersion, JsonpDeserializer.stringDeserializer(), "lucene_version");
		op.add(Builder::minimumIndexCompatibilityVersion, JsonpDeserializer.stringDeserializer(),
				"minimum_index_compatibility_version");
		op.add(Builder::minimumWireCompatibilityVersion, JsonpDeserializer.stringDeserializer(),
				"minimum_wire_compatibility_version");
		op.add(Builder::number, JsonpDeserializer.stringDeserializer(), "number");

	}

}
