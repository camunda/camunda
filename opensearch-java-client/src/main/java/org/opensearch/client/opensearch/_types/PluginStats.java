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
import java.util.List;
import java.util.function.Function;

// typedef: _types.PluginStats

@JsonpDeserializable
public class PluginStats implements JsonpSerializable {
	private final String classname;

	private final String description;

	private final String opensearchVersion;

	private final List<String> extendedPlugins;

	private final boolean hasNativeController;

	private final String javaVersion;

	private final String name;

	private final String version;

	// ---------------------------------------------------------------------------------------------

	private PluginStats(Builder builder) {

		this.classname = ApiTypeHelper.requireNonNull(builder.classname, this, "classname");
		this.description = ApiTypeHelper.requireNonNull(builder.description, this, "description");
		this.opensearchVersion = ApiTypeHelper.requireNonNull(builder.opensearchVersion, this,
				"opensearchVersion");
		this.extendedPlugins = ApiTypeHelper.unmodifiableRequired(builder.extendedPlugins, this, "extendedPlugins");
		this.hasNativeController = ApiTypeHelper.requireNonNull(builder.hasNativeController, this,
				"hasNativeController");
		this.javaVersion = ApiTypeHelper.requireNonNull(builder.javaVersion, this, "javaVersion");
		this.name = ApiTypeHelper.requireNonNull(builder.name, this, "name");
		this.version = ApiTypeHelper.requireNonNull(builder.version, this, "version");

	}

	public static PluginStats of(Function<Builder, ObjectBuilder<PluginStats>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code classname}
	 */
	public final String classname() {
		return this.classname;
	}

	/**
	 * Required - API name: {@code description}
	 */
	public final String description() {
		return this.description;
	}

	/**
	 * API name: {@code opensearch_version}
	 */
	public String opensearchVersion() {
		return this.opensearchVersion;
	}

	/**
	 * Required - API name: {@code extended_plugins}
	 */
	public final List<String> extendedPlugins() {
		return this.extendedPlugins;
	}

	/**
	 * Required - API name: {@code has_native_controller}
	 */
	public final boolean hasNativeController() {
		return this.hasNativeController;
	}

	/**
	 * Required - API name: {@code java_version}
	 */
	public final String javaVersion() {
		return this.javaVersion;
	}

	/**
	 * Required - API name: {@code name}
	 */
	public final String name() {
		return this.name;
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

		generator.writeKey("classname");
		generator.write(this.classname);

		generator.writeKey("description");
		generator.write(this.description);

		generator.writeKey("opensearch_version");
		generator.write(this.opensearchVersion);

		if (ApiTypeHelper.isDefined(this.extendedPlugins)) {
			generator.writeKey("extended_plugins");
			generator.writeStartArray();
			for (String item0 : this.extendedPlugins) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		generator.writeKey("has_native_controller");
		generator.write(this.hasNativeController);

		generator.writeKey("java_version");
		generator.write(this.javaVersion);

		generator.writeKey("name");
		generator.write(this.name);

		generator.writeKey("version");
		generator.write(this.version);
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link PluginStats}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<PluginStats> {
		private String classname;

		private String description;

		private String opensearchVersion;

		private List<String> extendedPlugins;

		private Boolean hasNativeController;

		private String javaVersion;

		private String name;

		private String version;

		/**
		 * Required - API name: {@code classname}
		 */
		public final Builder classname(String value) {
			this.classname = value;
			return this;
		}

		/**
		 * Required - API name: {@code description}
		 */
		public final Builder description(String value) {
			this.description = value;
			return this;
		}

		/**
		 * Required - API name: {@code opensearch_version}
		 */
		public final Builder opensearchVersion(String value) {
			this.opensearchVersion = value;
			return this;
		}

		/**
		 * Required - API name: {@code extended_plugins}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>extendedPlugins</code>.
		 */
		public final Builder extendedPlugins(List<String> list) {
			this.extendedPlugins = _listAddAll(this.extendedPlugins, list);
			return this;
		}

		/**
		 * Required - API name: {@code extended_plugins}
		 * <p>
		 * Adds one or more values to <code>extendedPlugins</code>.
		 */
		public final Builder extendedPlugins(String value, String... values) {
			this.extendedPlugins = _listAdd(this.extendedPlugins, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code has_native_controller}
		 */
		public final Builder hasNativeController(boolean value) {
			this.hasNativeController = value;
			return this;
		}

		/**
		 * Required - API name: {@code java_version}
		 */
		public final Builder javaVersion(String value) {
			this.javaVersion = value;
			return this;
		}

		/**
		 * Required - API name: {@code name}
		 */
		public final Builder name(String value) {
			this.name = value;
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
		 * Builds a {@link PluginStats}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public PluginStats build() {
			_checkSingleUse();

			return new PluginStats(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link PluginStats}
	 */
	public static final JsonpDeserializer<PluginStats> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			PluginStats::setupPluginStatsDeserializer);

	protected static void setupPluginStatsDeserializer(ObjectDeserializer<PluginStats.Builder> op) {

		op.add(Builder::classname, JsonpDeserializer.stringDeserializer(), "classname");
		op.add(Builder::description, JsonpDeserializer.stringDeserializer(), "description");
		op.add(Builder::opensearchVersion, JsonpDeserializer.stringDeserializer(), "opensearch_version");
		op.add(Builder::extendedPlugins, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"extended_plugins");
		op.add(Builder::hasNativeController, JsonpDeserializer.booleanDeserializer(), "has_native_controller");
		op.add(Builder::javaVersion, JsonpDeserializer.stringDeserializer(), "java_version");
		op.add(Builder::name, JsonpDeserializer.stringDeserializer(), "name");
		op.add(Builder::version, JsonpDeserializer.stringDeserializer(), "version");

	}

}
