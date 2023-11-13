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

package org.opensearch.client.opensearch.cluster.stats;

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

// typedef: cluster.stats.ClusterJvmVersion


@JsonpDeserializable
public class ClusterJvmVersion implements JsonpSerializable {
	private final boolean bundledJdk;

	private final int count;

	private final boolean usingBundledJdk;

	private final String version;

	private final String vmName;

	private final String vmVendor;

	private final String vmVersion;

	// ---------------------------------------------------------------------------------------------

	private ClusterJvmVersion(Builder builder) {

		this.bundledJdk = ApiTypeHelper.requireNonNull(builder.bundledJdk, this, "bundledJdk");
		this.count = ApiTypeHelper.requireNonNull(builder.count, this, "count");
		this.usingBundledJdk = ApiTypeHelper.requireNonNull(builder.usingBundledJdk, this, "usingBundledJdk");
		this.version = ApiTypeHelper.requireNonNull(builder.version, this, "version");
		this.vmName = ApiTypeHelper.requireNonNull(builder.vmName, this, "vmName");
		this.vmVendor = ApiTypeHelper.requireNonNull(builder.vmVendor, this, "vmVendor");
		this.vmVersion = ApiTypeHelper.requireNonNull(builder.vmVersion, this, "vmVersion");

	}

	public static ClusterJvmVersion of(Function<Builder, ObjectBuilder<ClusterJvmVersion>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code bundled_jdk}
	 */
	public final boolean bundledJdk() {
		return this.bundledJdk;
	}

	/**
	 * Required - API name: {@code count}
	 */
	public final int count() {
		return this.count;
	}

	/**
	 * Required - API name: {@code using_bundled_jdk}
	 */
	public final boolean usingBundledJdk() {
		return this.usingBundledJdk;
	}

	/**
	 * Required - API name: {@code version}
	 */
	public final String version() {
		return this.version;
	}

	/**
	 * Required - API name: {@code vm_name}
	 */
	public final String vmName() {
		return this.vmName;
	}

	/**
	 * Required - API name: {@code vm_vendor}
	 */
	public final String vmVendor() {
		return this.vmVendor;
	}

	/**
	 * Required - API name: {@code vm_version}
	 */
	public final String vmVersion() {
		return this.vmVersion;
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

		generator.writeKey("bundled_jdk");
		generator.write(this.bundledJdk);

		generator.writeKey("count");
		generator.write(this.count);

		generator.writeKey("using_bundled_jdk");
		generator.write(this.usingBundledJdk);

		generator.writeKey("version");
		generator.write(this.version);

		generator.writeKey("vm_name");
		generator.write(this.vmName);

		generator.writeKey("vm_vendor");
		generator.write(this.vmVendor);

		generator.writeKey("vm_version");
		generator.write(this.vmVersion);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ClusterJvmVersion}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<ClusterJvmVersion> {
		private Boolean bundledJdk;

		private Integer count;

		private Boolean usingBundledJdk;

		private String version;

		private String vmName;

		private String vmVendor;

		private String vmVersion;

		/**
		 * Required - API name: {@code bundled_jdk}
		 */
		public final Builder bundledJdk(boolean value) {
			this.bundledJdk = value;
			return this;
		}

		/**
		 * Required - API name: {@code count}
		 */
		public final Builder count(int value) {
			this.count = value;
			return this;
		}

		/**
		 * Required - API name: {@code using_bundled_jdk}
		 */
		public final Builder usingBundledJdk(boolean value) {
			this.usingBundledJdk = value;
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
		 * Required - API name: {@code vm_name}
		 */
		public final Builder vmName(String value) {
			this.vmName = value;
			return this;
		}

		/**
		 * Required - API name: {@code vm_vendor}
		 */
		public final Builder vmVendor(String value) {
			this.vmVendor = value;
			return this;
		}

		/**
		 * Required - API name: {@code vm_version}
		 */
		public final Builder vmVersion(String value) {
			this.vmVersion = value;
			return this;
		}

		/**
		 * Builds a {@link ClusterJvmVersion}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ClusterJvmVersion build() {
			_checkSingleUse();

			return new ClusterJvmVersion(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ClusterJvmVersion}
	 */
	public static final JsonpDeserializer<ClusterJvmVersion> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, ClusterJvmVersion::setupClusterJvmVersionDeserializer);

	protected static void setupClusterJvmVersionDeserializer(ObjectDeserializer<ClusterJvmVersion.Builder> op) {

		op.add(Builder::bundledJdk, JsonpDeserializer.booleanDeserializer(), "bundled_jdk");
		op.add(Builder::count, JsonpDeserializer.integerDeserializer(), "count");
		op.add(Builder::usingBundledJdk, JsonpDeserializer.booleanDeserializer(), "using_bundled_jdk");
		op.add(Builder::version, JsonpDeserializer.stringDeserializer(), "version");
		op.add(Builder::vmName, JsonpDeserializer.stringDeserializer(), "vm_name");
		op.add(Builder::vmVendor, JsonpDeserializer.stringDeserializer(), "vm_vendor");
		op.add(Builder::vmVersion, JsonpDeserializer.stringDeserializer(), "vm_version");

	}

}
