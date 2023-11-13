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

package org.opensearch.client.opensearch._types.mapping;

/**
 * Builders for {@link Property} variants.
 */
public class PropertyBuilders {
	private PropertyBuilders() {
	}

	/**
	 * Creates a builder for the {@link AggregateMetricDoubleProperty
	 * aggregate_metric_double} {@code Property} variant.
	 */
	public static AggregateMetricDoubleProperty.Builder aggregateMetricDouble() {
		return new AggregateMetricDoubleProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link BinaryProperty binary} {@code Property}
	 * variant.
	 */
	public static BinaryProperty.Builder binary() {
		return new BinaryProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link BooleanProperty boolean} {@code Property}
	 * variant.
	 */
	public static BooleanProperty.Builder boolean_() {
		return new BooleanProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link ByteNumberProperty byte} {@code Property}
	 * variant.
	 */
	public static ByteNumberProperty.Builder byte_() {
		return new ByteNumberProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link CompletionProperty completion}
	 * {@code Property} variant.
	 */
	public static CompletionProperty.Builder completion() {
		return new CompletionProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link ConstantKeywordProperty constant_keyword}
	 * {@code Property} variant.
	 */
	public static ConstantKeywordProperty.Builder constantKeyword() {
		return new ConstantKeywordProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link DateNanosProperty date_nanos}
	 * {@code Property} variant.
	 */
	public static DateNanosProperty.Builder dateNanos() {
		return new DateNanosProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link DateProperty date} {@code Property} variant.
	 */
	public static DateProperty.Builder date() {
		return new DateProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link DateRangeProperty date_range}
	 * {@code Property} variant.
	 */
	public static DateRangeProperty.Builder dateRange() {
		return new DateRangeProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link DenseVectorProperty dense_vector}
	 * {@code Property} variant.
	 */
	public static DenseVectorProperty.Builder denseVector() {
		return new DenseVectorProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link DoubleNumberProperty double}
	 * {@code Property} variant.
	 */
	public static DoubleNumberProperty.Builder double_() {
		return new DoubleNumberProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link DoubleRangeProperty double_range}
	 * {@code Property} variant.
	 */
	public static DoubleRangeProperty.Builder doubleRange() {
		return new DoubleRangeProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link FieldAliasProperty alias} {@code Property}
	 * variant.
	 */
	public static FieldAliasProperty.Builder alias() {
		return new FieldAliasProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link FlattenedProperty flattened}
	 * {@code Property} variant.
	 */
	public static FlattenedProperty.Builder flattened() {
		return new FlattenedProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link FloatNumberProperty float} {@code Property}
	 * variant.
	 */
	public static FloatNumberProperty.Builder float_() {
		return new FloatNumberProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link FloatRangeProperty float_range}
	 * {@code Property} variant.
	 */
	public static FloatRangeProperty.Builder floatRange() {
		return new FloatRangeProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link GeoPointProperty geo_point} {@code Property}
	 * variant.
	 */
	public static GeoPointProperty.Builder geoPoint() {
		return new GeoPointProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link GeoShapeProperty geo_shape} {@code Property}
	 * variant.
	 */
	public static GeoShapeProperty.Builder geoShape() {
		return new GeoShapeProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link HalfFloatNumberProperty half_float}
	 * {@code Property} variant.
	 */
	public static HalfFloatNumberProperty.Builder halfFloat() {
		return new HalfFloatNumberProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link HistogramProperty histogram}
	 * {@code Property} variant.
	 */
	public static HistogramProperty.Builder histogram() {
		return new HistogramProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link IntegerNumberProperty integer}
	 * {@code Property} variant.
	 */
	public static IntegerNumberProperty.Builder integer() {
		return new IntegerNumberProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link IntegerRangeProperty integer_range}
	 * {@code Property} variant.
	 */
	public static IntegerRangeProperty.Builder integerRange() {
		return new IntegerRangeProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link IpProperty ip} {@code Property} variant.
	 */
	public static IpProperty.Builder ip() {
		return new IpProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link IpRangeProperty ip_range} {@code Property}
	 * variant.
	 */
	public static IpRangeProperty.Builder ipRange() {
		return new IpRangeProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link JoinProperty join} {@code Property} variant.
	 */
	public static JoinProperty.Builder join() {
		return new JoinProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link KeywordProperty keyword} {@code Property}
	 * variant.
	 */
	public static KeywordProperty.Builder keyword() {
		return new KeywordProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link LongNumberProperty long} {@code Property}
	 * variant.
	 */
	public static LongNumberProperty.Builder long_() {
		return new LongNumberProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link LongRangeProperty long_range}
	 * {@code Property} variant.
	 */
	public static LongRangeProperty.Builder longRange() {
		return new LongRangeProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link Murmur3HashProperty murmur3}
	 * {@code Property} variant.
	 */
	public static Murmur3HashProperty.Builder murmur3() {
		return new Murmur3HashProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link NestedProperty nested} {@code Property}
	 * variant.
	 */
	public static NestedProperty.Builder nested() {
		return new NestedProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link ObjectProperty object} {@code Property}
	 * variant.
	 */
	public static ObjectProperty.Builder object() {
		return new ObjectProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link PercolatorProperty percolator}
	 * {@code Property} variant.
	 */
	public static PercolatorProperty.Builder percolator() {
		return new PercolatorProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link PointProperty point} {@code Property}
	 * variant.
	 */
	public static PointProperty.Builder point() {
		return new PointProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link RankFeatureProperty rank_feature}
	 * {@code Property} variant.
	 */
	public static RankFeatureProperty.Builder rankFeature() {
		return new RankFeatureProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link RankFeaturesProperty rank_features}
	 * {@code Property} variant.
	 */
	public static RankFeaturesProperty.Builder rankFeatures() {
		return new RankFeaturesProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link ScaledFloatNumberProperty scaled_float}
	 * {@code Property} variant.
	 */
	public static ScaledFloatNumberProperty.Builder scaledFloat() {
		return new ScaledFloatNumberProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link SearchAsYouTypeProperty search_as_you_type}
	 * {@code Property} variant.
	 */
	public static SearchAsYouTypeProperty.Builder searchAsYouType() {
		return new SearchAsYouTypeProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link ShapeProperty shape} {@code Property}
	 * variant.
	 */
	public static ShapeProperty.Builder shape() {
		return new ShapeProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link ShortNumberProperty short} {@code Property}
	 * variant.
	 */
	public static ShortNumberProperty.Builder short_() {
		return new ShortNumberProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link TextProperty text} {@code Property} variant.
	 */
	public static TextProperty.Builder text() {
		return new TextProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link TokenCountProperty token_count}
	 * {@code Property} variant.
	 */
	public static TokenCountProperty.Builder tokenCount() {
		return new TokenCountProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link UnsignedLongNumberProperty unsigned_long}
	 * {@code Property} variant.
	 */
	public static UnsignedLongNumberProperty.Builder unsignedLong() {
		return new UnsignedLongNumberProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link VersionProperty version} {@code Property}
	 * variant.
	 */
	public static VersionProperty.Builder version() {
		return new VersionProperty.Builder();
	}

	/**
	 * Creates a builder for the {@link WildcardProperty wildcard} {@code Property}
	 * variant.
	 */
	public static WildcardProperty.Builder wildcard() {
		return new WildcardProperty.Builder();
	}

}
