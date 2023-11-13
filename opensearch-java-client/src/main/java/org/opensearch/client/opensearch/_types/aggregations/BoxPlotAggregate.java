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

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.aggregations.BoxPlotAggregate

@JsonpDeserializable
public class BoxPlotAggregate extends AggregateBase implements AggregateVariant {
	private final double min;

	private final double max;

	private final double q1;

	private final double q2;

	private final double q3;

	private final double lower;

	private final double upper;

	@Nullable
	private final String minAsString;

	@Nullable
	private final String maxAsString;

	@Nullable
	private final String q1AsString;

	@Nullable
	private final String q2AsString;

	@Nullable
	private final String q3AsString;

	@Nullable
	private final String lowerAsString;

	@Nullable
	private final String upperAsString;

	// ---------------------------------------------------------------------------------------------

	private BoxPlotAggregate(Builder builder) {
		super(builder);

		this.min = ApiTypeHelper.requireNonNull(builder.min, this, "min");
		this.max = ApiTypeHelper.requireNonNull(builder.max, this, "max");
		this.q1 = ApiTypeHelper.requireNonNull(builder.q1, this, "q1");
		this.q2 = ApiTypeHelper.requireNonNull(builder.q2, this, "q2");
		this.q3 = ApiTypeHelper.requireNonNull(builder.q3, this, "q3");
		this.lower = ApiTypeHelper.requireNonNull(builder.lower, this, "lower");
		this.upper = ApiTypeHelper.requireNonNull(builder.upper, this, "upper");
		this.minAsString = builder.minAsString;
		this.maxAsString = builder.maxAsString;
		this.q1AsString = builder.q1AsString;
		this.q2AsString = builder.q2AsString;
		this.q3AsString = builder.q3AsString;
		this.lowerAsString = builder.lowerAsString;
		this.upperAsString = builder.upperAsString;

	}

	public static BoxPlotAggregate of(Function<Builder, ObjectBuilder<BoxPlotAggregate>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Aggregate variant kind.
	 */
	@Override
	public Aggregate.Kind _aggregateKind() {
		return Aggregate.Kind.BoxPlot;
	}

	/**
	 * Required - API name: {@code min}
	 */
	public final double min() {
		return this.min;
	}

	/**
	 * Required - API name: {@code max}
	 */
	public final double max() {
		return this.max;
	}

	/**
	 * Required - API name: {@code q1}
	 */
	public final double q1() {
		return this.q1;
	}

	/**
	 * Required - API name: {@code q2}
	 */
	public final double q2() {
		return this.q2;
	}

	/**
	 * Required - API name: {@code q3}
	 */
	public final double q3() {
		return this.q3;
	}

	/**
	 * Required - API name: {@code lower}
	 */
	public final double lower() {
		return this.lower;
	}

	/**
	 * Required - API name: {@code upper}
	 */
	public final double upper() {
		return this.upper;
	}

	/**
	 * API name: {@code min_as_string}
	 */
	@Nullable
	public final String minAsString() {
		return this.minAsString;
	}

	/**
	 * API name: {@code max_as_string}
	 */
	@Nullable
	public final String maxAsString() {
		return this.maxAsString;
	}

	/**
	 * API name: {@code q1_as_string}
	 */
	@Nullable
	public final String q1AsString() {
		return this.q1AsString;
	}

	/**
	 * API name: {@code q2_as_string}
	 */
	@Nullable
	public final String q2AsString() {
		return this.q2AsString;
	}

	/**
	 * API name: {@code q3_as_string}
	 */
	@Nullable
	public final String q3AsString() {
		return this.q3AsString;
	}

	/**
	 * API name: {@code lower_as_string}
	 */
	@Nullable
	public final String lowerAsString() {
		return this.lowerAsString;
	}

	/**
	 * API name: {@code upper_as_string}
	 */
	@Nullable
	public final String upperAsString() {
		return this.upperAsString;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		generator.writeKey("min");
		generator.write(this.min);

		generator.writeKey("max");
		generator.write(this.max);

		generator.writeKey("q1");
		generator.write(this.q1);

		generator.writeKey("q2");
		generator.write(this.q2);

		generator.writeKey("q3");
		generator.write(this.q3);

		generator.writeKey("lower");
		generator.write(this.lower);

		generator.writeKey("upper");
		generator.write(this.upper);

		if (this.minAsString != null) {
			generator.writeKey("min_as_string");
			generator.write(this.minAsString);

		}
		if (this.maxAsString != null) {
			generator.writeKey("max_as_string");
			generator.write(this.maxAsString);

		}
		if (this.q1AsString != null) {
			generator.writeKey("q1_as_string");
			generator.write(this.q1AsString);

		}
		if (this.q2AsString != null) {
			generator.writeKey("q2_as_string");
			generator.write(this.q2AsString);

		}
		if (this.q3AsString != null) {
			generator.writeKey("q3_as_string");
			generator.write(this.q3AsString);

		}
		if (this.lowerAsString != null) {
			generator.writeKey("lower_as_string");
			generator.write(this.lowerAsString);

		}
		if (this.upperAsString != null) {
			generator.writeKey("upper_as_string");
			generator.write(this.upperAsString);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link BoxPlotAggregate}.
	 */

	public static class Builder extends AggregateBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<BoxPlotAggregate> {
		private Double min;

		private Double max;

		private Double q1;

		private Double q2;

		private Double q3;

		private Double lower;

		private Double upper;

		@Nullable
		private String minAsString;

		@Nullable
		private String maxAsString;

		@Nullable
		private String q1AsString;

		@Nullable
		private String q2AsString;

		@Nullable
		private String q3AsString;

		@Nullable
		private String lowerAsString;

		@Nullable
		private String upperAsString;

		/**
		 * Required - API name: {@code min}
		 */
		public final Builder min(double value) {
			this.min = value;
			return this;
		}

		/**
		 * Required - API name: {@code max}
		 */
		public final Builder max(double value) {
			this.max = value;
			return this;
		}

		/**
		 * Required - API name: {@code q1}
		 */
		public final Builder q1(double value) {
			this.q1 = value;
			return this;
		}

		/**
		 * Required - API name: {@code q2}
		 */
		public final Builder q2(double value) {
			this.q2 = value;
			return this;
		}

		/**
		 * Required - API name: {@code q3}
		 */
		public final Builder q3(double value) {
			this.q3 = value;
			return this;
		}

		/**
		 * Required - API name: {@code lower}
		 */
		public final Builder lower(double value) {
			this.lower = value;
			return this;
		}

		/**
		 * Required - API name: {@code upper}
		 */
		public final Builder upper(double value) {
			this.upper = value;
			return this;
		}

		/**
		 * API name: {@code min_as_string}
		 */
		public final Builder minAsString(@Nullable String value) {
			this.minAsString = value;
			return this;
		}

		/**
		 * API name: {@code max_as_string}
		 */
		public final Builder maxAsString(@Nullable String value) {
			this.maxAsString = value;
			return this;
		}

		/**
		 * API name: {@code q1_as_string}
		 */
		public final Builder q1AsString(@Nullable String value) {
			this.q1AsString = value;
			return this;
		}

		/**
		 * API name: {@code q2_as_string}
		 */
		public final Builder q2AsString(@Nullable String value) {
			this.q2AsString = value;
			return this;
		}

		/**
		 * API name: {@code q3_as_string}
		 */
		public final Builder q3AsString(@Nullable String value) {
			this.q3AsString = value;
			return this;
		}

		/**
		 * API name: {@code lower_as_string}
		 */
		public final Builder lowerAsString(@Nullable String value) {
			this.lowerAsString = value;
			return this;
		}

		/**
		 * API name: {@code upper_as_string}
		 */
		public final Builder upperAsString(@Nullable String value) {
			this.upperAsString = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link BoxPlotAggregate}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public BoxPlotAggregate build() {
			_checkSingleUse();

			return new BoxPlotAggregate(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link BoxPlotAggregate}
	 */
	public static final JsonpDeserializer<BoxPlotAggregate> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			BoxPlotAggregate::setupBoxPlotAggregateDeserializer);

	protected static void setupBoxPlotAggregateDeserializer(ObjectDeserializer<BoxPlotAggregate.Builder> op) {
		AggregateBase.setupAggregateBaseDeserializer(op);
		op.add(Builder::min, JsonpDeserializer.doubleDeserializer(), "min");
		op.add(Builder::max, JsonpDeserializer.doubleDeserializer(), "max");
		op.add(Builder::q1, JsonpDeserializer.doubleDeserializer(), "q1");
		op.add(Builder::q2, JsonpDeserializer.doubleDeserializer(), "q2");
		op.add(Builder::q3, JsonpDeserializer.doubleDeserializer(), "q3");
		op.add(Builder::lower, JsonpDeserializer.doubleDeserializer(), "lower");
		op.add(Builder::upper, JsonpDeserializer.doubleDeserializer(), "upper");
		op.add(Builder::minAsString, JsonpDeserializer.stringDeserializer(), "min_as_string");
		op.add(Builder::maxAsString, JsonpDeserializer.stringDeserializer(), "max_as_string");
		op.add(Builder::q1AsString, JsonpDeserializer.stringDeserializer(), "q1_as_string");
		op.add(Builder::q2AsString, JsonpDeserializer.stringDeserializer(), "q2_as_string");
		op.add(Builder::q3AsString, JsonpDeserializer.stringDeserializer(), "q3_as_string");
		op.add(Builder::lowerAsString, JsonpDeserializer.stringDeserializer(), "lower_as_string");
		op.add(Builder::upperAsString, JsonpDeserializer.stringDeserializer(), "upper_as_string");

	}

}
