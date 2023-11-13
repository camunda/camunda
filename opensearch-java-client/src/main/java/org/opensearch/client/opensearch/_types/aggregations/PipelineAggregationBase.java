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

package org.opensearch.client.opensearch._types.aggregations;

import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.aggregations.PipelineAggregationBase


public abstract class PipelineAggregationBase extends AggregationBase {
	@Nullable
	private final BucketsPath bucketsPath;

	@Nullable
	private final String format;

	@Nullable
	private final GapPolicy gapPolicy;

	// ---------------------------------------------------------------------------------------------

	protected PipelineAggregationBase(AbstractBuilder<?> builder) {
		super(builder);

		this.bucketsPath = builder.bucketsPath;
		this.format = builder.format;
		this.gapPolicy = builder.gapPolicy;

	}

	/**
	 * API name: {@code buckets_path}
	 */
	@Nullable
	public final BucketsPath bucketsPath() {
		return this.bucketsPath;
	}

	/**
	 * API name: {@code format}
	 */
	@Nullable
	public final String format() {
		return this.format;
	}

	/**
	 * API name: {@code gap_policy}
	 */
	@Nullable
	public final GapPolicy gapPolicy() {
		return this.gapPolicy;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		if (this.bucketsPath != null) {
			generator.writeKey("buckets_path");
			this.bucketsPath.serialize(generator, mapper);

		}
		if (this.format != null) {
			generator.writeKey("format");
			generator.write(this.format);

		}
		if (this.gapPolicy != null) {
			generator.writeKey("gap_policy");
			this.gapPolicy.serialize(generator, mapper);
		}

	}

	protected abstract static class AbstractBuilder<BuilderT extends AbstractBuilder<BuilderT>>
			extends
				AggregationBase.AbstractBuilder<BuilderT> {
		@Nullable
		private BucketsPath bucketsPath;

		@Nullable
		private String format;

		@Nullable
		private GapPolicy gapPolicy;

		/**
		 * API name: {@code buckets_path}
		 */
		public final BuilderT bucketsPath(@Nullable BucketsPath value) {
			this.bucketsPath = value;
			return self();
		}

		/**
		 * API name: {@code buckets_path}
		 */
		public final BuilderT bucketsPath(Function<BucketsPath.Builder, ObjectBuilder<BucketsPath>> fn) {
			return this.bucketsPath(fn.apply(new BucketsPath.Builder()).build());
		}

		/**
		 * API name: {@code format}
		 */
		public final BuilderT format(@Nullable String value) {
			this.format = value;
			return self();
		}

		/**
		 * API name: {@code gap_policy}
		 */
		public final BuilderT gapPolicy(@Nullable GapPolicy value) {
			this.gapPolicy = value;
			return self();
		}

	}

	// ---------------------------------------------------------------------------------------------
	protected static <BuilderT extends AbstractBuilder<BuilderT>> void setupPipelineAggregationBaseDeserializer(
			ObjectDeserializer<BuilderT> op) {
		setupAggregationBaseDeserializer(op);
		op.add(AbstractBuilder::bucketsPath, BucketsPath._DESERIALIZER, "buckets_path");
		op.add(AbstractBuilder::format, JsonpDeserializer.stringDeserializer(), "format");
		op.add(AbstractBuilder::gapPolicy, GapPolicy._DESERIALIZER, "gap_policy");

	}

}
