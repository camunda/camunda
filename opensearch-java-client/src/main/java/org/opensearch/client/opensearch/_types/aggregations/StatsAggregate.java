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
import org.opensearch.client.json.JsonpUtils;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.aggregations.StatsAggregate

/**
 * Statistics aggregation result. <code>min</code>, <code>max</code> and
 * <code>avg</code> are missing if there were no values to process
 * (<code>count</code> is zero).
 *
 */
@JsonpDeserializable
public class StatsAggregate extends AggregateBase implements AggregateVariant {
	private final long count;

	private final double min;

	private final double max;

	private final double avg;

	private final double sum;

	@Nullable
	private final String minAsString;

	@Nullable
	private final String maxAsString;

	@Nullable
	private final String avgAsString;

	@Nullable
	private final String sumAsString;

	// ---------------------------------------------------------------------------------------------

	protected StatsAggregate(AbstractBuilder<?> builder) {
		super(builder);

		this.count = ApiTypeHelper.requireNonNull(builder.count, this, "count");
		this.min = ApiTypeHelper.requireNonNull(builder.min, this, "min");
		this.max = ApiTypeHelper.requireNonNull(builder.max, this, "max");
		this.avg = ApiTypeHelper.requireNonNull(builder.avg, this, "avg");
		this.sum = ApiTypeHelper.requireNonNull(builder.sum, this, "sum");
		this.minAsString = builder.minAsString;
		this.maxAsString = builder.maxAsString;
		this.avgAsString = builder.avgAsString;
		this.sumAsString = builder.sumAsString;

	}

	public static StatsAggregate statsAggregateOf(Function<Builder, ObjectBuilder<StatsAggregate>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Aggregate variant kind.
	 */
	@Override
	public Aggregate.Kind _aggregateKind() {
		return Aggregate.Kind.Stats;
	}

	/**
	 * Required - API name: {@code count}
	 */
	public final long count() {
		return this.count;
	}

	/**
	 * Required - API name: {@code min}
	 * <p>
	 * Defaults to {@code Double.POSITIVE_INFINITY} if parsed from a JSON
	 * {@code null} value.
	 */
	public final double min() {
		return this.min;
	}

	/**
	 * Required - API name: {@code max}
	 * <p>
	 * Defaults to {@code Double.NEGATIVE_INFINITY} if parsed from a JSON
	 * {@code null} value.
	 */
	public final double max() {
		return this.max;
	}

	/**
	 * Required - API name: {@code avg}
	 * <p>
	 * Defaults to {@code 0} if parsed from a JSON {@code null} value.
	 */
	public final double avg() {
		return this.avg;
	}

	/**
	 * Required - API name: {@code sum}
	 */
	public final double sum() {
		return this.sum;
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
	 * API name: {@code avg_as_string}
	 */
	@Nullable
	public final String avgAsString() {
		return this.avgAsString;
	}

	/**
	 * API name: {@code sum_as_string}
	 */
	@Nullable
	public final String sumAsString() {
		return this.sumAsString;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		generator.writeKey("count");
		generator.write(this.count);

		generator.writeKey("min");
		JsonpUtils.serializeDoubleOrNull(generator, this.min, Double.POSITIVE_INFINITY);
		generator.writeKey("max");
		JsonpUtils.serializeDoubleOrNull(generator, this.max, Double.NEGATIVE_INFINITY);
		generator.writeKey("avg");
		JsonpUtils.serializeDoubleOrNull(generator, this.avg, 0);
		generator.writeKey("sum");
		generator.write(this.sum);

		if (this.minAsString != null) {
			generator.writeKey("min_as_string");
			generator.write(this.minAsString);

		}
		if (this.maxAsString != null) {
			generator.writeKey("max_as_string");
			generator.write(this.maxAsString);

		}
		if (this.avgAsString != null) {
			generator.writeKey("avg_as_string");
			generator.write(this.avgAsString);

		}
		if (this.sumAsString != null) {
			generator.writeKey("sum_as_string");
			generator.write(this.sumAsString);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link StatsAggregate}.
	 */

	public static class Builder extends StatsAggregate.AbstractBuilder<Builder>
			implements
				ObjectBuilder<StatsAggregate> {
		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link StatsAggregate}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public StatsAggregate build() {
			_checkSingleUse();

			return new StatsAggregate(this);
		}
	}

	protected abstract static class AbstractBuilder<BuilderT extends AbstractBuilder<BuilderT>>
			extends
				AggregateBase.AbstractBuilder<BuilderT> {
		private Long count;

		private Double min;

		private Double max;

		private Double avg;

		private Double sum;

		@Nullable
		private String minAsString;

		@Nullable
		private String maxAsString;

		@Nullable
		private String avgAsString;

		@Nullable
		private String sumAsString;

		/**
		 * Required - API name: {@code count}
		 */
		public final BuilderT count(long value) {
			this.count = value;
			return self();
		}

		/**
		 * Required - API name: {@code min}
		 * <p>
		 * Defaults to {@code Double.POSITIVE_INFINITY} if parsed from a JSON
		 * {@code null} value.
		 */
		public final BuilderT min(double value) {
			this.min = value;
			return self();
		}

		/**
		 * Required - API name: {@code max}
		 * <p>
		 * Defaults to {@code Double.NEGATIVE_INFINITY} if parsed from a JSON
		 * {@code null} value.
		 */
		public final BuilderT max(double value) {
			this.max = value;
			return self();
		}

		/**
		 * Required - API name: {@code avg}
		 * <p>
		 * Defaults to {@code 0} if parsed from a JSON {@code null} value.
		 */
		public final BuilderT avg(double value) {
			this.avg = value;
			return self();
		}

		/**
		 * Required - API name: {@code sum}
		 */
		public final BuilderT sum(double value) {
			this.sum = value;
			return self();
		}

		/**
		 * API name: {@code min_as_string}
		 */
		public final BuilderT minAsString(@Nullable String value) {
			this.minAsString = value;
			return self();
		}

		/**
		 * API name: {@code max_as_string}
		 */
		public final BuilderT maxAsString(@Nullable String value) {
			this.maxAsString = value;
			return self();
		}

		/**
		 * API name: {@code avg_as_string}
		 */
		public final BuilderT avgAsString(@Nullable String value) {
			this.avgAsString = value;
			return self();
		}

		/**
		 * API name: {@code sum_as_string}
		 */
		public final BuilderT sumAsString(@Nullable String value) {
			this.sumAsString = value;
			return self();
		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link StatsAggregate}
	 */
	public static final JsonpDeserializer<StatsAggregate> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			StatsAggregate::setupStatsAggregateDeserializer);

	protected static <BuilderT extends AbstractBuilder<BuilderT>> void setupStatsAggregateDeserializer(
			ObjectDeserializer<BuilderT> op) {
		setupAggregateBaseDeserializer(op);
		op.add(AbstractBuilder::count, JsonpDeserializer.longDeserializer(), "count");
		op.add(AbstractBuilder::min, JsonpDeserializer.doubleOrNullDeserializer(Double.POSITIVE_INFINITY), "min");
		op.add(AbstractBuilder::max, JsonpDeserializer.doubleOrNullDeserializer(Double.NEGATIVE_INFINITY), "max");
		op.add(AbstractBuilder::avg, JsonpDeserializer.doubleOrNullDeserializer(0), "avg");
		op.add(AbstractBuilder::sum, JsonpDeserializer.doubleDeserializer(), "sum");
		op.add(AbstractBuilder::minAsString, JsonpDeserializer.stringDeserializer(), "min_as_string");
		op.add(AbstractBuilder::maxAsString, JsonpDeserializer.stringDeserializer(), "max_as_string");
		op.add(AbstractBuilder::avgAsString, JsonpDeserializer.stringDeserializer(), "avg_as_string");
		op.add(AbstractBuilder::sumAsString, JsonpDeserializer.stringDeserializer(), "sum_as_string");

	}

}
