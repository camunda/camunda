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

package org.opensearch.client.opensearch.indices;

import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.ExpandWildcard;
import org.opensearch.client.opensearch._types.RequestBase;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.mapping.DynamicMapping;
import org.opensearch.client.opensearch._types.mapping.DynamicTemplate;
import org.opensearch.client.opensearch._types.mapping.FieldNamesField;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.RoutingField;
import org.opensearch.client.opensearch._types.mapping.RuntimeField;
import org.opensearch.client.opensearch._types.mapping.SourceField;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

// typedef: indices.put_mapping.Request

/**
 * Updates the index mappings.
 * 
 */
@JsonpDeserializable
public class PutMappingRequest extends RequestBase implements JsonpSerializable {
	@Nullable
	private final FieldNamesField fieldNames;

	private final Map<String, JsonData> meta;

	@Nullable
	private final RoutingField routing;

	@Nullable
	private final SourceField source;

	@Nullable
	private final Boolean allowNoIndices;

	@Nullable
	private final Boolean dateDetection;

	@Nullable
	private final DynamicMapping dynamic;

	private final List<String> dynamicDateFormats;

	private final List<Map<String, DynamicTemplate>> dynamicTemplates;

	private final List<ExpandWildcard> expandWildcards;

	@Nullable
	private final Boolean ignoreUnavailable;

	private final List<String> index;

	@Deprecated
	@Nullable
	private final Time masterTimeout;

	@Nullable
	private final Time clusterManagerTimeout;

	@Nullable
	private final Boolean numericDetection;

	private final Map<String, Property> properties;

	private final Map<String, RuntimeField> runtime;

	@Nullable
	private final Time timeout;

	@Nullable
	private final Boolean writeIndexOnly;

	// ---------------------------------------------------------------------------------------------

	private PutMappingRequest(Builder builder) {

		this.fieldNames = builder.fieldNames;
		this.meta = ApiTypeHelper.unmodifiable(builder.meta);
		this.routing = builder.routing;
		this.source = builder.source;
		this.allowNoIndices = builder.allowNoIndices;
		this.dateDetection = builder.dateDetection;
		this.dynamic = builder.dynamic;
		this.dynamicDateFormats = ApiTypeHelper.unmodifiable(builder.dynamicDateFormats);
		this.dynamicTemplates = ApiTypeHelper.unmodifiable(builder.dynamicTemplates);
		this.expandWildcards = ApiTypeHelper.unmodifiable(builder.expandWildcards);
		this.ignoreUnavailable = builder.ignoreUnavailable;
		this.index = ApiTypeHelper.unmodifiableRequired(builder.index, this, "index");
		this.masterTimeout = builder.masterTimeout;
		this.clusterManagerTimeout = builder.clusterManagerTimeout;
		this.numericDetection = builder.numericDetection;
		this.properties = ApiTypeHelper.unmodifiable(builder.properties);
		this.runtime = ApiTypeHelper.unmodifiable(builder.runtime);
		this.timeout = builder.timeout;
		this.writeIndexOnly = builder.writeIndexOnly;

	}

	public static PutMappingRequest of(Function<Builder, ObjectBuilder<PutMappingRequest>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Control whether field names are enabled for the index.
	 * <p>
	 * API name: {@code _field_names}
	 */
	@Nullable
	public final FieldNamesField fieldNames() {
		return this.fieldNames;
	}

	/**
	 * A mapping type can have custom meta data associated with it. These are not
	 * used at all by Elasticsearch, but can be used to store application-specific
	 * metadata.
	 * <p>
	 * API name: {@code _meta}
	 */
	public final Map<String, JsonData> meta() {
		return this.meta;
	}

	/**
	 * Enable making a routing value required on indexed documents.
	 * <p>
	 * API name: {@code _routing}
	 */
	@Nullable
	public final RoutingField routing() {
		return this.routing;
	}

	/**
	 * Control whether the _source field is enabled on the index.
	 * <p>
	 * API name: {@code _source}
	 */
	@Nullable
	public final SourceField source() {
		return this.source;
	}

	/**
	 * Whether to ignore if a wildcard indices expression resolves into no concrete
	 * indices. (This includes <code>_all</code> string or when no indices have been
	 * specified)
	 * <p>
	 * API name: {@code allow_no_indices}
	 */
	@Nullable
	public final Boolean allowNoIndices() {
		return this.allowNoIndices;
	}

	/**
	 * Controls whether dynamic date detection is enabled.
	 * <p>
	 * API name: {@code date_detection}
	 */
	@Nullable
	public final Boolean dateDetection() {
		return this.dateDetection;
	}

	/**
	 * Controls whether new fields are added dynamically.
	 * <p>
	 * API name: {@code dynamic}
	 */
	@Nullable
	public final DynamicMapping dynamic() {
		return this.dynamic;
	}

	/**
	 * If date detection is enabled then new string fields are checked against
	 * 'dynamic_date_formats' and if the value matches then a new date field is
	 * added instead of string.
	 * <p>
	 * API name: {@code dynamic_date_formats}
	 */
	public final List<String> dynamicDateFormats() {
		return this.dynamicDateFormats;
	}

	/**
	 * Specify dynamic templates for the mapping.
	 * <p>
	 * API name: {@code dynamic_templates}
	 */
	public final List<Map<String, DynamicTemplate>> dynamicTemplates() {
		return this.dynamicTemplates;
	}

	/**
	 * Whether to expand wildcard expression to concrete indices that are open,
	 * closed or both.
	 * <p>
	 * API name: {@code expand_wildcards}
	 */
	public final List<ExpandWildcard> expandWildcards() {
		return this.expandWildcards;
	}

	/**
	 * Whether specified concrete indices should be ignored when unavailable
	 * (missing or closed)
	 * <p>
	 * API name: {@code ignore_unavailable}
	 */
	@Nullable
	public final Boolean ignoreUnavailable() {
		return this.ignoreUnavailable;
	}

	/**
	 * Required - A comma-separated list of index names the mapping should be added
	 * to (supports wildcards); use <code>_all</code> or omit to add the mapping on
	 * all indices.
	 * <p>
	 * API name: {@code index}
	 */
	public final List<String> index() {
		return this.index;
	}

	/**
	 * Specify timeout for connection to master
	 * <p>
	 * API name: {@code master_timeout}
	 */
	@Deprecated
	@Nullable
	public final Time masterTimeout() {
		return this.masterTimeout;
	}

	/**
	 * Specify timeout for connection to cluster-manager
	 * <p>
	 * API name: {@code cluster_manager_timeout}
	 */
	@Nullable
	public final Time clusterManagerTimeout() {
		return this.clusterManagerTimeout;
	}

	/**
	 * Automatically map strings into numeric data types for all fields.
	 * <p>
	 * API name: {@code numeric_detection}
	 */
	@Nullable
	public final Boolean numericDetection() {
		return this.numericDetection;
	}

	/**
	 * Mapping for a field. For new fields, this mapping can include:
	 * <ul>
	 * <li>Field name</li>
	 * <li>Field data type</li>
	 * <li>Mapping parameters</li>
	 * </ul>
	 * <p>
	 * API name: {@code properties}
	 */
	public final Map<String, Property> properties() {
		return this.properties;
	}

	/**
	 * Mapping of runtime fields for the index.
	 * <p>
	 * API name: {@code runtime}
	 */
	public final Map<String, RuntimeField> runtime() {
		return this.runtime;
	}

	/**
	 * Explicit operation timeout
	 * <p>
	 * API name: {@code timeout}
	 */
	@Nullable
	public final Time timeout() {
		return this.timeout;
	}

	/**
	 * When true, applies mappings only to the write index of an alias or data
	 * stream
	 * <p>
	 * API name: {@code write_index_only}
	 */
	@Nullable
	public final Boolean writeIndexOnly() {
		return this.writeIndexOnly;
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

		if (this.fieldNames != null) {
			generator.writeKey("_field_names");
			this.fieldNames.serialize(generator, mapper);

		}
		if (ApiTypeHelper.isDefined(this.meta)) {
			generator.writeKey("_meta");
			generator.writeStartObject();
			for (Map.Entry<String, JsonData> item0 : this.meta.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (this.routing != null) {
			generator.writeKey("_routing");
			this.routing.serialize(generator, mapper);

		}
		if (this.source != null) {
			generator.writeKey("_source");
			this.source.serialize(generator, mapper);

		}
		if (this.dateDetection != null) {
			generator.writeKey("date_detection");
			generator.write(this.dateDetection);

		}
		if (this.dynamic != null) {
			generator.writeKey("dynamic");
			this.dynamic.serialize(generator, mapper);
		}
		if (ApiTypeHelper.isDefined(this.dynamicDateFormats)) {
			generator.writeKey("dynamic_date_formats");
			generator.writeStartArray();
			for (String item0 : this.dynamicDateFormats) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.dynamicTemplates)) {
			generator.writeKey("dynamic_templates");
			generator.writeStartArray();
			for (Map<String, DynamicTemplate> item0 : this.dynamicTemplates) {
				generator.writeStartObject();
				if (item0 != null) {
					for (Map.Entry<String, DynamicTemplate> item1 : item0.entrySet()) {
						generator.writeKey(item1.getKey());
						item1.getValue().serialize(generator, mapper);

					}
				}
				generator.writeEnd();

			}
			generator.writeEnd();

		}
		if (this.numericDetection != null) {
			generator.writeKey("numeric_detection");
			generator.write(this.numericDetection);

		}
		if (ApiTypeHelper.isDefined(this.properties)) {
			generator.writeKey("properties");
			generator.writeStartObject();
			for (Map.Entry<String, Property> item0 : this.properties.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.runtime)) {
			generator.writeKey("runtime");
			generator.writeStartObject();
			for (Map.Entry<String, RuntimeField> item0 : this.runtime.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link PutMappingRequest}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<PutMappingRequest> {
		@Nullable
		private FieldNamesField fieldNames;

		@Nullable
		private Map<String, JsonData> meta;

		@Nullable
		private RoutingField routing;

		@Nullable
		private SourceField source;

		@Nullable
		private Boolean allowNoIndices;

		@Nullable
		private Boolean dateDetection;

		@Nullable
		private DynamicMapping dynamic;

		@Nullable
		private List<String> dynamicDateFormats;

		@Nullable
		private List<Map<String, DynamicTemplate>> dynamicTemplates;

		@Nullable
		private List<ExpandWildcard> expandWildcards;

		@Nullable
		private Boolean ignoreUnavailable;

		private List<String> index;

		@Deprecated
		@Nullable
		private Time masterTimeout;

		@Nullable
		private Time clusterManagerTimeout;

		@Nullable
		private Boolean numericDetection;

		@Nullable
		private Map<String, Property> properties;

		@Nullable
		private Map<String, RuntimeField> runtime;

		@Nullable
		private Time timeout;

		@Nullable
		private Boolean writeIndexOnly;

		/**
		 * Control whether field names are enabled for the index.
		 * <p>
		 * API name: {@code _field_names}
		 */
		public final Builder fieldNames(@Nullable FieldNamesField value) {
			this.fieldNames = value;
			return this;
		}

		/**
		 * Control whether field names are enabled for the index.
		 * <p>
		 * API name: {@code _field_names}
		 */
		public final Builder fieldNames(Function<FieldNamesField.Builder, ObjectBuilder<FieldNamesField>> fn) {
			return this.fieldNames(fn.apply(new FieldNamesField.Builder()).build());
		}

		/**
		 * A mapping type can have custom meta data associated with it. These are not
		 * used at all by Elasticsearch, but can be used to store application-specific
		 * metadata.
		 * <p>
		 * API name: {@code _meta}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>meta</code>.
		 */
		public final Builder meta(Map<String, JsonData> map) {
			this.meta = _mapPutAll(this.meta, map);
			return this;
		}

		/**
		 * A mapping type can have custom meta data associated with it. These are not
		 * used at all by Elasticsearch, but can be used to store application-specific
		 * metadata.
		 * <p>
		 * API name: {@code _meta}
		 * <p>
		 * Adds an entry to <code>meta</code>.
		 */
		public final Builder meta(String key, JsonData value) {
			this.meta = _mapPut(this.meta, key, value);
			return this;
		}

		/**
		 * Enable making a routing value required on indexed documents.
		 * <p>
		 * API name: {@code _routing}
		 */
		public final Builder routing(@Nullable RoutingField value) {
			this.routing = value;
			return this;
		}

		/**
		 * Enable making a routing value required on indexed documents.
		 * <p>
		 * API name: {@code _routing}
		 */
		public final Builder routing(Function<RoutingField.Builder, ObjectBuilder<RoutingField>> fn) {
			return this.routing(fn.apply(new RoutingField.Builder()).build());
		}

		/**
		 * Control whether the _source field is enabled on the index.
		 * <p>
		 * API name: {@code _source}
		 */
		public final Builder source(@Nullable SourceField value) {
			this.source = value;
			return this;
		}

		/**
		 * Control whether the _source field is enabled on the index.
		 * <p>
		 * API name: {@code _source}
		 */
		public final Builder source(Function<SourceField.Builder, ObjectBuilder<SourceField>> fn) {
			return this.source(fn.apply(new SourceField.Builder()).build());
		}

		/**
		 * Whether to ignore if a wildcard indices expression resolves into no concrete
		 * indices. (This includes <code>_all</code> string or when no indices have been
		 * specified)
		 * <p>
		 * API name: {@code allow_no_indices}
		 */
		public final Builder allowNoIndices(@Nullable Boolean value) {
			this.allowNoIndices = value;
			return this;
		}

		/**
		 * Controls whether dynamic date detection is enabled.
		 * <p>
		 * API name: {@code date_detection}
		 */
		public final Builder dateDetection(@Nullable Boolean value) {
			this.dateDetection = value;
			return this;
		}

		/**
		 * Controls whether new fields are added dynamically.
		 * <p>
		 * API name: {@code dynamic}
		 */
		public final Builder dynamic(@Nullable DynamicMapping value) {
			this.dynamic = value;
			return this;
		}

		/**
		 * If date detection is enabled then new string fields are checked against
		 * 'dynamic_date_formats' and if the value matches then a new date field is
		 * added instead of string.
		 * <p>
		 * API name: {@code dynamic_date_formats}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>dynamicDateFormats</code>.
		 */
		public final Builder dynamicDateFormats(List<String> list) {
			this.dynamicDateFormats = _listAddAll(this.dynamicDateFormats, list);
			return this;
		}

		/**
		 * If date detection is enabled then new string fields are checked against
		 * 'dynamic_date_formats' and if the value matches then a new date field is
		 * added instead of string.
		 * <p>
		 * API name: {@code dynamic_date_formats}
		 * <p>
		 * Adds one or more values to <code>dynamicDateFormats</code>.
		 */
		public final Builder dynamicDateFormats(String value, String... values) {
			this.dynamicDateFormats = _listAdd(this.dynamicDateFormats, value, values);
			return this;
		}

		/**
		 * Specify dynamic templates for the mapping.
		 * <p>
		 * API name: {@code dynamic_templates}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>dynamicTemplates</code>.
		 */
		public final Builder dynamicTemplates(List<Map<String, DynamicTemplate>> list) {
			this.dynamicTemplates = _listAddAll(this.dynamicTemplates, list);
			return this;
		}

		/**
		 * Specify dynamic templates for the mapping.
		 * <p>
		 * API name: {@code dynamic_templates}
		 * <p>
		 * Adds one or more values to <code>dynamicTemplates</code>.
		 */
		public final Builder dynamicTemplates(Map<String, DynamicTemplate> value,
				Map<String, DynamicTemplate>... values) {
			this.dynamicTemplates = _listAdd(this.dynamicTemplates, value, values);
			return this;
		}

		/**
		 * Whether to expand wildcard expression to concrete indices that are open,
		 * closed or both.
		 * <p>
		 * API name: {@code expand_wildcards}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>expandWildcards</code>.
		 */
		public final Builder expandWildcards(List<ExpandWildcard> list) {
			this.expandWildcards = _listAddAll(this.expandWildcards, list);
			return this;
		}

		/**
		 * Whether to expand wildcard expression to concrete indices that are open,
		 * closed or both.
		 * <p>
		 * API name: {@code expand_wildcards}
		 * <p>
		 * Adds one or more values to <code>expandWildcards</code>.
		 */
		public final Builder expandWildcards(ExpandWildcard value, ExpandWildcard... values) {
			this.expandWildcards = _listAdd(this.expandWildcards, value, values);
			return this;
		}

		/**
		 * Whether specified concrete indices should be ignored when unavailable
		 * (missing or closed)
		 * <p>
		 * API name: {@code ignore_unavailable}
		 */
		public final Builder ignoreUnavailable(@Nullable Boolean value) {
			this.ignoreUnavailable = value;
			return this;
		}

		/**
		 * Required - A comma-separated list of index names the mapping should be added
		 * to (supports wildcards); use <code>_all</code> or omit to add the mapping on
		 * all indices.
		 * <p>
		 * API name: {@code index}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>index</code>.
		 */
		public final Builder index(List<String> list) {
			this.index = _listAddAll(this.index, list);
			return this;
		}

		/**
		 * Required - A comma-separated list of index names the mapping should be added
		 * to (supports wildcards); use <code>_all</code> or omit to add the mapping on
		 * all indices.
		 * <p>
		 * API name: {@code index}
		 * <p>
		 * Adds one or more values to <code>index</code>.
		 */
		public final Builder index(String value, String... values) {
			this.index = _listAdd(this.index, value, values);
			return this;
		}

		/**
		 * Specify timeout for connection to master
		 * <p>
		 * API name: {@code master_timeout}
		 */
		@Deprecated
		public final Builder masterTimeout(@Nullable Time value) {
			this.masterTimeout = value;
			return this;
		}

		/**
		 * Specify timeout for connection to master
		 * <p>
		 * API name: {@code master_timeout}
		 */
		@Deprecated
		public final Builder masterTimeout(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.masterTimeout(fn.apply(new Time.Builder()).build());
		}

		/**
		 * Specify timeout for connection to cluster-manager
		 * <p>
		 * API name: {@code cluster_manager_timeout}
		 */
		public final Builder clusterManagerTimeout(@Nullable Time value) {
			this.clusterManagerTimeout = value;
			return this;
		}

		/**
		 * Specify timeout for connection to cluster-manager
		 * <p>
		 * API name: {@code cluster_manager_timeout}
		 */
		public final Builder clusterManagerTimeout(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.clusterManagerTimeout(fn.apply(new Time.Builder()).build());
		}

		/**
		 * Automatically map strings into numeric data types for all fields.
		 * <p>
		 * API name: {@code numeric_detection}
		 */
		public final Builder numericDetection(@Nullable Boolean value) {
			this.numericDetection = value;
			return this;
		}

		/**
		 * Mapping for a field. For new fields, this mapping can include:
		 * <ul>
		 * <li>Field name</li>
		 * <li>Field data type</li>
		 * <li>Mapping parameters</li>
		 * </ul>
		 * <p>
		 * API name: {@code properties}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>properties</code>.
		 */
		public final Builder properties(Map<String, Property> map) {
			this.properties = _mapPutAll(this.properties, map);
			return this;
		}

		/**
		 * Mapping for a field. For new fields, this mapping can include:
		 * <ul>
		 * <li>Field name</li>
		 * <li>Field data type</li>
		 * <li>Mapping parameters</li>
		 * </ul>
		 * <p>
		 * API name: {@code properties}
		 * <p>
		 * Adds an entry to <code>properties</code>.
		 */
		public final Builder properties(String key, Property value) {
			this.properties = _mapPut(this.properties, key, value);
			return this;
		}

		/**
		 * Mapping for a field. For new fields, this mapping can include:
		 * <ul>
		 * <li>Field name</li>
		 * <li>Field data type</li>
		 * <li>Mapping parameters</li>
		 * </ul>
		 * <p>
		 * API name: {@code properties}
		 * <p>
		 * Adds an entry to <code>properties</code> using a builder lambda.
		 */
		public final Builder properties(String key, Function<Property.Builder, ObjectBuilder<Property>> fn) {
			return properties(key, fn.apply(new Property.Builder()).build());
		}

		/**
		 * Mapping of runtime fields for the index.
		 * <p>
		 * API name: {@code runtime}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>runtime</code>.
		 */
		public final Builder runtime(Map<String, RuntimeField> map) {
			this.runtime = _mapPutAll(this.runtime, map);
			return this;
		}

		/**
		 * Mapping of runtime fields for the index.
		 * <p>
		 * API name: {@code runtime}
		 * <p>
		 * Adds an entry to <code>runtime</code>.
		 */
		public final Builder runtime(String key, RuntimeField value) {
			this.runtime = _mapPut(this.runtime, key, value);
			return this;
		}

		/**
		 * Mapping of runtime fields for the index.
		 * <p>
		 * API name: {@code runtime}
		 * <p>
		 * Adds an entry to <code>runtime</code> using a builder lambda.
		 */
		public final Builder runtime(String key, Function<RuntimeField.Builder, ObjectBuilder<RuntimeField>> fn) {
			return runtime(key, fn.apply(new RuntimeField.Builder()).build());
		}

		/**
		 * Explicit operation timeout
		 * <p>
		 * API name: {@code timeout}
		 */
		public final Builder timeout(@Nullable Time value) {
			this.timeout = value;
			return this;
		}

		/**
		 * Explicit operation timeout
		 * <p>
		 * API name: {@code timeout}
		 */
		public final Builder timeout(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.timeout(fn.apply(new Time.Builder()).build());
		}

		/**
		 * When true, applies mappings only to the write index of an alias or data
		 * stream
		 * <p>
		 * API name: {@code write_index_only}
		 */
		public final Builder writeIndexOnly(@Nullable Boolean value) {
			this.writeIndexOnly = value;
			return this;
		}

		/**
		 * Builds a {@link PutMappingRequest}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public PutMappingRequest build() {
			_checkSingleUse();

			return new PutMappingRequest(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link PutMappingRequest}
	 */
	public static final JsonpDeserializer<PutMappingRequest> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, PutMappingRequest::setupPutMappingRequestDeserializer);

	protected static void setupPutMappingRequestDeserializer(ObjectDeserializer<PutMappingRequest.Builder> op) {

		op.add(Builder::fieldNames, FieldNamesField._DESERIALIZER, "_field_names");
		op.add(Builder::meta, JsonpDeserializer.stringMapDeserializer(JsonData._DESERIALIZER), "_meta");
		op.add(Builder::routing, RoutingField._DESERIALIZER, "_routing");
		op.add(Builder::source, SourceField._DESERIALIZER, "_source");
		op.add(Builder::dateDetection, JsonpDeserializer.booleanDeserializer(), "date_detection");
		op.add(Builder::dynamic, DynamicMapping._DESERIALIZER, "dynamic");
		op.add(Builder::dynamicDateFormats, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"dynamic_date_formats");
		op.add(Builder::dynamicTemplates, JsonpDeserializer.arrayDeserializer(
				JsonpDeserializer.stringMapDeserializer(DynamicTemplate._DESERIALIZER)), "dynamic_templates");
		op.add(Builder::numericDetection, JsonpDeserializer.booleanDeserializer(), "numeric_detection");
		op.add(Builder::properties, JsonpDeserializer.stringMapDeserializer(Property._DESERIALIZER), "properties");
		op.add(Builder::runtime, JsonpDeserializer.stringMapDeserializer(RuntimeField._DESERIALIZER), "runtime");

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Endpoint "{@code indices.put_mapping}".
	 */
	public static final Endpoint<PutMappingRequest, PutMappingResponse, ErrorResponse> _ENDPOINT = new SimpleEndpoint<>(

			// Request method
			request -> {
				return "PUT";

			},

			// Request path
			request -> {
				final int _index = 1 << 0;

				int propsSet = 0;

				propsSet |= _index;

				if (propsSet == (_index)) {
					StringBuilder buf = new StringBuilder();
					buf.append("/");
					SimpleEndpoint.pathEncode(request.index.stream().map(v -> v).collect(Collectors.joining(",")), buf);
					buf.append("/_mapping");
					return buf.toString();
				}
				if (propsSet == (_index)) {
					StringBuilder buf = new StringBuilder();
					buf.append("/");
					SimpleEndpoint.pathEncode(request.index.stream().map(v -> v).collect(Collectors.joining(",")), buf);
					buf.append("/_mappings");
					return buf.toString();
				}
				throw SimpleEndpoint.noPathTemplateFound("path");

			},

			// Request parameters
			request -> {
				Map<String, String> params = new HashMap<>();
				if (request.masterTimeout != null) {
					params.put("master_timeout", request.masterTimeout._toJsonString());
				}
				if (request.clusterManagerTimeout != null) {
					params.put("cluster_manager_timeout", request.clusterManagerTimeout._toJsonString());
				}
				if (ApiTypeHelper.isDefined(request.expandWildcards)) {
					params.put("expand_wildcards",
							request.expandWildcards.stream()
									.map(v -> v.jsonValue()).collect(Collectors.joining(",")));
				}
				if (request.ignoreUnavailable != null) {
					params.put("ignore_unavailable", String.valueOf(request.ignoreUnavailable));
				}
				if (request.allowNoIndices != null) {
					params.put("allow_no_indices", String.valueOf(request.allowNoIndices));
				}
				if (request.writeIndexOnly != null) {
					params.put("write_index_only", String.valueOf(request.writeIndexOnly));
				}
				if (request.timeout != null) {
					params.put("timeout", request.timeout._toJsonString());
				}
				return params;

			}, SimpleEndpoint.emptyMap(), true, PutMappingResponse._DESERIALIZER);
}
