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

package org.opensearch.client.opensearch.ingest.simulate;

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
import javax.annotation.Nullable;
import org.opensearch.client.util.ActionStatusOptions;

// typedef: ingest.simulate.PipelineSimulation

@JsonpDeserializable
public class PipelineSimulation implements JsonpSerializable {
	@Nullable
	private final DocumentSimulation doc;

	private final List<PipelineSimulation> processorResults;

	@Nullable
	private final String tag;

	@Nullable
	private final String processorType;

	@Nullable
	private final ActionStatusOptions status;

	// ---------------------------------------------------------------------------------------------

	private PipelineSimulation(Builder builder) {

		this.doc = builder.doc;
		this.processorResults = ApiTypeHelper.unmodifiable(builder.processorResults);
		this.tag = builder.tag;
		this.processorType = builder.processorType;
		this.status = builder.status;

	}

	public static PipelineSimulation of(Function<Builder, ObjectBuilder<PipelineSimulation>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code doc}
	 */
	@Nullable
	public final DocumentSimulation doc() {
		return this.doc;
	}

	/**
	 * API name: {@code processor_results}
	 */
	public final List<PipelineSimulation> processorResults() {
		return this.processorResults;
	}

	/**
	 * API name: {@code tag}
	 */
	@Nullable
	public final String tag() {
		return this.tag;
	}

	/**
	 * API name: {@code processor_type}
	 */
	@Nullable
	public final String processorType() {
		return this.processorType;
	}

	/**
	 * API name: {@code status}
	 */
	@Nullable
	public final ActionStatusOptions status() {
		return this.status;
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

		if (this.doc != null) {
			generator.writeKey("doc");
			this.doc.serialize(generator, mapper);

		}
		if (ApiTypeHelper.isDefined(this.processorResults)) {
			generator.writeKey("processor_results");
			generator.writeStartArray();
			for (PipelineSimulation item0 : this.processorResults) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (this.tag != null) {
			generator.writeKey("tag");
			generator.write(this.tag);

		}
		if (this.processorType != null) {
			generator.writeKey("processor_type");
			generator.write(this.processorType);

		}
		if (this.status != null) {
			generator.writeKey("status");
			this.status.serialize(generator, mapper);
		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link PipelineSimulation}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<PipelineSimulation> {
		@Nullable
		private DocumentSimulation doc;

		@Nullable
		private List<PipelineSimulation> processorResults;

		@Nullable
		private String tag;

		@Nullable
		private String processorType;

		@Nullable
		private ActionStatusOptions status;

		/**
		 * API name: {@code doc}
		 */
		public final Builder doc(@Nullable DocumentSimulation value) {
			this.doc = value;
			return this;
		}

		/**
		 * API name: {@code doc}
		 */
		public final Builder doc(Function<DocumentSimulation.Builder, ObjectBuilder<DocumentSimulation>> fn) {
			return this.doc(fn.apply(new DocumentSimulation.Builder()).build());
		}

		/**
		 * API name: {@code processor_results}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>processorResults</code>.
		 */
		public final Builder processorResults(List<PipelineSimulation> list) {
			this.processorResults = _listAddAll(this.processorResults, list);
			return this;
		}

		/**
		 * API name: {@code processor_results}
		 * <p>
		 * Adds one or more values to <code>processorResults</code>.
		 */
		public final Builder processorResults(PipelineSimulation value, PipelineSimulation... values) {
			this.processorResults = _listAdd(this.processorResults, value, values);
			return this;
		}

		/**
		 * API name: {@code processor_results}
		 * <p>
		 * Adds a value to <code>processorResults</code> using a builder lambda.
		 */
		public final Builder processorResults(
				Function<PipelineSimulation.Builder, ObjectBuilder<PipelineSimulation>> fn) {
			return processorResults(fn.apply(new PipelineSimulation.Builder()).build());
		}

		/**
		 * API name: {@code tag}
		 */
		public final Builder tag(@Nullable String value) {
			this.tag = value;
			return this;
		}

		/**
		 * API name: {@code processor_type}
		 */
		public final Builder processorType(@Nullable String value) {
			this.processorType = value;
			return this;
		}

		/**
		 * API name: {@code status}
		 */
		public final Builder status(@Nullable ActionStatusOptions value) {
			this.status = value;
			return this;
		}

		/**
		 * Builds a {@link PipelineSimulation}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public PipelineSimulation build() {
			_checkSingleUse();

			return new PipelineSimulation(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link PipelineSimulation}
	 */
	public static final JsonpDeserializer<PipelineSimulation> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, PipelineSimulation::setupPipelineSimulationDeserializer);

	protected static void setupPipelineSimulationDeserializer(ObjectDeserializer<PipelineSimulation.Builder> op) {

		op.add(Builder::doc, DocumentSimulation._DESERIALIZER, "doc");
		op.add(Builder::processorResults, JsonpDeserializer.arrayDeserializer(PipelineSimulation._DESERIALIZER),
				"processor_results");
		op.add(Builder::tag, JsonpDeserializer.stringDeserializer(), "tag");
		op.add(Builder::processorType, JsonpDeserializer.stringDeserializer(), "processor_type");
		op.add(Builder::status, ActionStatusOptions._DESERIALIZER, "status");

	}

}
