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
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.Map;
import java.util.function.Function;

// typedef: _types.aggregations.MatrixStatsFields


@JsonpDeserializable
public class MatrixStatsFields implements JsonpSerializable {
	private final String name;

	private final long count;

	private final double mean;

	private final double variance;

	private final double skewness;

	private final double kurtosis;

	private final Map<String, Double> covariance;

	private final Map<String, Double> correlation;

	// ---------------------------------------------------------------------------------------------

	private MatrixStatsFields(Builder builder) {

		this.name = ApiTypeHelper.requireNonNull(builder.name, this, "name");
		this.count = ApiTypeHelper.requireNonNull(builder.count, this, "count");
		this.mean = ApiTypeHelper.requireNonNull(builder.mean, this, "mean");
		this.variance = ApiTypeHelper.requireNonNull(builder.variance, this, "variance");
		this.skewness = ApiTypeHelper.requireNonNull(builder.skewness, this, "skewness");
		this.kurtosis = ApiTypeHelper.requireNonNull(builder.kurtosis, this, "kurtosis");
		this.covariance = ApiTypeHelper.unmodifiableRequired(builder.covariance, this, "covariance");
		this.correlation = ApiTypeHelper.unmodifiableRequired(builder.correlation, this, "correlation");

	}

	public static MatrixStatsFields of(Function<Builder, ObjectBuilder<MatrixStatsFields>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code name}
	 */
	public final String name() {
		return this.name;
	}

	/**
	 * Required - API name: {@code count}
	 */
	public final long count() {
		return this.count;
	}

	/**
	 * Required - API name: {@code mean}
	 */
	public final double mean() {
		return this.mean;
	}

	/**
	 * Required - API name: {@code variance}
	 */
	public final double variance() {
		return this.variance;
	}

	/**
	 * Required - API name: {@code skewness}
	 */
	public final double skewness() {
		return this.skewness;
	}

	/**
	 * Required - API name: {@code kurtosis}
	 */
	public final double kurtosis() {
		return this.kurtosis;
	}

	/**
	 * Required - API name: {@code covariance}
	 */
	public final Map<String, Double> covariance() {
		return this.covariance;
	}

	/**
	 * Required - API name: {@code correlation}
	 */
	public final Map<String, Double> correlation() {
		return this.correlation;
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

		generator.writeKey("name");
		generator.write(this.name);

		generator.writeKey("count");
		generator.write(this.count);

		generator.writeKey("mean");
		generator.write(this.mean);

		generator.writeKey("variance");
		generator.write(this.variance);

		generator.writeKey("skewness");
		generator.write(this.skewness);

		generator.writeKey("kurtosis");
		generator.write(this.kurtosis);

		if (ApiTypeHelper.isDefined(this.covariance)) {
			generator.writeKey("covariance");
			generator.writeStartObject();
			for (Map.Entry<String, Double> item0 : this.covariance.entrySet()) {
				generator.writeKey(item0.getKey());
				generator.write(item0.getValue());

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.correlation)) {
			generator.writeKey("correlation");
			generator.writeStartObject();
			for (Map.Entry<String, Double> item0 : this.correlation.entrySet()) {
				generator.writeKey(item0.getKey());
				generator.write(item0.getValue());

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link MatrixStatsFields}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<MatrixStatsFields> {
		private String name;

		private Long count;

		private Double mean;

		private Double variance;

		private Double skewness;

		private Double kurtosis;

		private Map<String, Double> covariance;

		private Map<String, Double> correlation;

		/**
		 * Required - API name: {@code name}
		 */
		public final Builder name(String value) {
			this.name = value;
			return this;
		}

		/**
		 * Required - API name: {@code count}
		 */
		public final Builder count(long value) {
			this.count = value;
			return this;
		}

		/**
		 * Required - API name: {@code mean}
		 */
		public final Builder mean(double value) {
			this.mean = value;
			return this;
		}

		/**
		 * Required - API name: {@code variance}
		 */
		public final Builder variance(double value) {
			this.variance = value;
			return this;
		}

		/**
		 * Required - API name: {@code skewness}
		 */
		public final Builder skewness(double value) {
			this.skewness = value;
			return this;
		}

		/**
		 * Required - API name: {@code kurtosis}
		 */
		public final Builder kurtosis(double value) {
			this.kurtosis = value;
			return this;
		}

		/**
		 * Required - API name: {@code covariance}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>covariance</code>.
		 */
		public final Builder covariance(Map<String, Double> map) {
			this.covariance = _mapPutAll(this.covariance, map);
			return this;
		}

		/**
		 * Required - API name: {@code covariance}
		 * <p>
		 * Adds an entry to <code>covariance</code>.
		 */
		public final Builder covariance(String key, Double value) {
			this.covariance = _mapPut(this.covariance, key, value);
			return this;
		}

		/**
		 * Required - API name: {@code correlation}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>correlation</code>.
		 */
		public final Builder correlation(Map<String, Double> map) {
			this.correlation = _mapPutAll(this.correlation, map);
			return this;
		}

		/**
		 * Required - API name: {@code correlation}
		 * <p>
		 * Adds an entry to <code>correlation</code>.
		 */
		public final Builder correlation(String key, Double value) {
			this.correlation = _mapPut(this.correlation, key, value);
			return this;
		}

		/**
		 * Builds a {@link MatrixStatsFields}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public MatrixStatsFields build() {
			_checkSingleUse();

			return new MatrixStatsFields(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link MatrixStatsFields}
	 */
	public static final JsonpDeserializer<MatrixStatsFields> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, MatrixStatsFields::setupMatrixStatsFieldsDeserializer);

	protected static void setupMatrixStatsFieldsDeserializer(ObjectDeserializer<MatrixStatsFields.Builder> op) {

		op.add(Builder::name, JsonpDeserializer.stringDeserializer(), "name");
		op.add(Builder::count, JsonpDeserializer.longDeserializer(), "count");
		op.add(Builder::mean, JsonpDeserializer.doubleDeserializer(), "mean");
		op.add(Builder::variance, JsonpDeserializer.doubleDeserializer(), "variance");
		op.add(Builder::skewness, JsonpDeserializer.doubleDeserializer(), "skewness");
		op.add(Builder::kurtosis, JsonpDeserializer.doubleDeserializer(), "kurtosis");
		op.add(Builder::covariance, JsonpDeserializer.stringMapDeserializer(JsonpDeserializer.doubleDeserializer()),
				"covariance");
		op.add(Builder::correlation, JsonpDeserializer.stringMapDeserializer(JsonpDeserializer.doubleDeserializer()),
				"correlation");

	}

}
