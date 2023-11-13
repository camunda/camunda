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

package org.opensearch.client.opensearch._types.analysis;

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

// typedef: _types.analysis.IcuAnalyzer


@JsonpDeserializable
public class IcuAnalyzer implements AnalyzerVariant, JsonpSerializable {
	private final IcuNormalizationType method;

	private final IcuNormalizationMode mode;

	// ---------------------------------------------------------------------------------------------

	private IcuAnalyzer(Builder builder) {

		this.method = ApiTypeHelper.requireNonNull(builder.method, this, "method");
		this.mode = ApiTypeHelper.requireNonNull(builder.mode, this, "mode");

	}

	public static IcuAnalyzer of(Function<Builder, ObjectBuilder<IcuAnalyzer>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Analyzer variant kind.
	 */
	@Override
	public Analyzer.Kind _analyzerKind() {
		return Analyzer.Kind.IcuAnalyzer;
	}

	/**
	 * Required - API name: {@code method}
	 */
	public final IcuNormalizationType method() {
		return this.method;
	}

	/**
	 * Required - API name: {@code mode}
	 */
	public final IcuNormalizationMode mode() {
		return this.mode;
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

		generator.write("type", "icu_analyzer");

		generator.writeKey("method");
		this.method.serialize(generator, mapper);
		generator.writeKey("mode");
		this.mode.serialize(generator, mapper);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link IcuAnalyzer}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<IcuAnalyzer> {
		private IcuNormalizationType method;

		private IcuNormalizationMode mode;

		/**
		 * Required - API name: {@code method}
		 */
		public final Builder method(IcuNormalizationType value) {
			this.method = value;
			return this;
		}

		/**
		 * Required - API name: {@code mode}
		 */
		public final Builder mode(IcuNormalizationMode value) {
			this.mode = value;
			return this;
		}

		/**
		 * Builds a {@link IcuAnalyzer}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public IcuAnalyzer build() {
			_checkSingleUse();

			return new IcuAnalyzer(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link IcuAnalyzer}
	 */
	public static final JsonpDeserializer<IcuAnalyzer> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			IcuAnalyzer::setupIcuAnalyzerDeserializer);

	protected static void setupIcuAnalyzerDeserializer(ObjectDeserializer<IcuAnalyzer.Builder> op) {

		op.add(Builder::method, IcuNormalizationType._DESERIALIZER, "method");
		op.add(Builder::mode, IcuNormalizationMode._DESERIALIZER, "mode");

		op.ignore("type");
	}

}
