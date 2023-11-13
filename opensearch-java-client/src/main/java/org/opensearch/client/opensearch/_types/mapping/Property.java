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

import org.opensearch.client.json.JsonEnum;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import org.opensearch.client.util.TaggedUnion;
import org.opensearch.client.util.TaggedUnionUtils;
import jakarta.json.stream.JsonGenerator;

import java.util.function.Function;

// typedef: _types.mapping.Property


@JsonpDeserializable
public class Property implements TaggedUnion<Property.Kind, PropertyVariant>, JsonpSerializable {

	/**
	 * {@link Property} variant kinds.
	 */
	/**
	 * {@link Property} variant kinds.
	 */

	public enum Kind implements JsonEnum {
		AggregateMetricDouble("aggregate_metric_double"),

		Binary("binary"),

		Boolean("boolean"),

		Byte("byte"),

		Completion("completion"),

		ConstantKeyword("constant_keyword"),

		DateNanos("date_nanos"),

		Date("date"),

		DateRange("date_range"),

		DenseVector("dense_vector"),

		Double("double"),

		DoubleRange("double_range"),

		Alias("alias"),

		Flattened("flattened"),

		Float("float"),

		FloatRange("float_range"),

		GeoPoint("geo_point"),

		GeoShape("geo_shape"),

		HalfFloat("half_float"),

		Histogram("histogram"),

		Integer("integer"),

		IntegerRange("integer_range"),

		Ip("ip"),

		IpRange("ip_range"),

		Join("join"),

		Keyword("keyword"),

		Long("long"),

		LongRange("long_range"),

		Murmur3("murmur3"),

		Nested("nested"),

		Object("object"),

		Percolator("percolator"),

		Point("point"),

		RankFeature("rank_feature"),

		RankFeatures("rank_features"),

		ScaledFloat("scaled_float"),

		SearchAsYouType("search_as_you_type"),

		Shape("shape"),

		Short("short"),

		Text("text"),

		TokenCount("token_count"),

		UnsignedLong("unsigned_long"),

		Version("version"),

		Wildcard("wildcard"),

		;

		private final String jsonValue;

		Kind(String jsonValue) {
			this.jsonValue = jsonValue;
		}

		public String jsonValue() {
			return this.jsonValue;
		}

	}

	private final Kind _kind;
	private final PropertyVariant _value;

	@Override
	public final Kind _kind() {
		return _kind;
	}

	@Override
	public final PropertyVariant _get() {
		return _value;
	}

	public Property(PropertyVariant value) {

		this._kind = ApiTypeHelper.requireNonNull(value._propertyKind(), this, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(value, this, "<variant value>");

	}

	private Property(Builder builder) {

		this._kind = ApiTypeHelper.requireNonNull(builder._kind, builder, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(builder._value, builder, "<variant value>");

	}

	public static Property of(Function<Builder, ObjectBuilder<Property>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Is this variant instance of kind {@code aggregate_metric_double}?
	 */
	public boolean isAggregateMetricDouble() {
		return _kind == Kind.AggregateMetricDouble;
	}

	/**
	 * Get the {@code aggregate_metric_double} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the
	 *             {@code aggregate_metric_double} kind.
	 */
	public AggregateMetricDoubleProperty aggregateMetricDouble() {
		return TaggedUnionUtils.get(this, Kind.AggregateMetricDouble);
	}

	/**
	 * Is this variant instance of kind {@code binary}?
	 */
	public boolean isBinary() {
		return _kind == Kind.Binary;
	}

	/**
	 * Get the {@code binary} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code binary} kind.
	 */
	public BinaryProperty binary() {
		return TaggedUnionUtils.get(this, Kind.Binary);
	}

	/**
	 * Is this variant instance of kind {@code boolean}?
	 */
	public boolean isBoolean() {
		return _kind == Kind.Boolean;
	}

	/**
	 * Get the {@code boolean} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code boolean} kind.
	 */
	public BooleanProperty boolean_() {
		return TaggedUnionUtils.get(this, Kind.Boolean);
	}

	/**
	 * Is this variant instance of kind {@code byte}?
	 */
	public boolean isByte() {
		return _kind == Kind.Byte;
	}

	/**
	 * Get the {@code byte} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code byte} kind.
	 */
	public ByteNumberProperty byte_() {
		return TaggedUnionUtils.get(this, Kind.Byte);
	}

	/**
	 * Is this variant instance of kind {@code completion}?
	 */
	public boolean isCompletion() {
		return _kind == Kind.Completion;
	}

	/**
	 * Get the {@code completion} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code completion} kind.
	 */
	public CompletionProperty completion() {
		return TaggedUnionUtils.get(this, Kind.Completion);
	}

	/**
	 * Is this variant instance of kind {@code constant_keyword}?
	 */
	public boolean isConstantKeyword() {
		return _kind == Kind.ConstantKeyword;
	}

	/**
	 * Get the {@code constant_keyword} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code constant_keyword}
	 *             kind.
	 */
	public ConstantKeywordProperty constantKeyword() {
		return TaggedUnionUtils.get(this, Kind.ConstantKeyword);
	}

	/**
	 * Is this variant instance of kind {@code date_nanos}?
	 */
	public boolean isDateNanos() {
		return _kind == Kind.DateNanos;
	}

	/**
	 * Get the {@code date_nanos} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code date_nanos} kind.
	 */
	public DateNanosProperty dateNanos() {
		return TaggedUnionUtils.get(this, Kind.DateNanos);
	}

	/**
	 * Is this variant instance of kind {@code date}?
	 */
	public boolean isDate() {
		return _kind == Kind.Date;
	}

	/**
	 * Get the {@code date} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code date} kind.
	 */
	public DateProperty date() {
		return TaggedUnionUtils.get(this, Kind.Date);
	}

	/**
	 * Is this variant instance of kind {@code date_range}?
	 */
	public boolean isDateRange() {
		return _kind == Kind.DateRange;
	}

	/**
	 * Get the {@code date_range} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code date_range} kind.
	 */
	public DateRangeProperty dateRange() {
		return TaggedUnionUtils.get(this, Kind.DateRange);
	}

	/**
	 * Is this variant instance of kind {@code dense_vector}?
	 */
	public boolean isDenseVector() {
		return _kind == Kind.DenseVector;
	}

	/**
	 * Get the {@code dense_vector} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code dense_vector} kind.
	 */
	public DenseVectorProperty denseVector() {
		return TaggedUnionUtils.get(this, Kind.DenseVector);
	}

	/**
	 * Is this variant instance of kind {@code double}?
	 */
	public boolean isDouble() {
		return _kind == Kind.Double;
	}

	/**
	 * Get the {@code double} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code double} kind.
	 */
	public DoubleNumberProperty double_() {
		return TaggedUnionUtils.get(this, Kind.Double);
	}

	/**
	 * Is this variant instance of kind {@code double_range}?
	 */
	public boolean isDoubleRange() {
		return _kind == Kind.DoubleRange;
	}

	/**
	 * Get the {@code double_range} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code double_range} kind.
	 */
	public DoubleRangeProperty doubleRange() {
		return TaggedUnionUtils.get(this, Kind.DoubleRange);
	}

	/**
	 * Is this variant instance of kind {@code alias}?
	 */
	public boolean isAlias() {
		return _kind == Kind.Alias;
	}

	/**
	 * Get the {@code alias} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code alias} kind.
	 */
	public FieldAliasProperty alias() {
		return TaggedUnionUtils.get(this, Kind.Alias);
	}

	/**
	 * Is this variant instance of kind {@code flattened}?
	 */
	public boolean isFlattened() {
		return _kind == Kind.Flattened;
	}

	/**
	 * Get the {@code flattened} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code flattened} kind.
	 */
	public FlattenedProperty flattened() {
		return TaggedUnionUtils.get(this, Kind.Flattened);
	}

	/**
	 * Is this variant instance of kind {@code float}?
	 */
	public boolean isFloat() {
		return _kind == Kind.Float;
	}

	/**
	 * Get the {@code float} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code float} kind.
	 */
	public FloatNumberProperty float_() {
		return TaggedUnionUtils.get(this, Kind.Float);
	}

	/**
	 * Is this variant instance of kind {@code float_range}?
	 */
	public boolean isFloatRange() {
		return _kind == Kind.FloatRange;
	}

	/**
	 * Get the {@code float_range} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code float_range} kind.
	 */
	public FloatRangeProperty floatRange() {
		return TaggedUnionUtils.get(this, Kind.FloatRange);
	}

	/**
	 * Is this variant instance of kind {@code geo_point}?
	 */
	public boolean isGeoPoint() {
		return _kind == Kind.GeoPoint;
	}

	/**
	 * Get the {@code geo_point} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code geo_point} kind.
	 */
	public GeoPointProperty geoPoint() {
		return TaggedUnionUtils.get(this, Kind.GeoPoint);
	}

	/**
	 * Is this variant instance of kind {@code geo_shape}?
	 */
	public boolean isGeoShape() {
		return _kind == Kind.GeoShape;
	}

	/**
	 * Get the {@code geo_shape} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code geo_shape} kind.
	 */
	public GeoShapeProperty geoShape() {
		return TaggedUnionUtils.get(this, Kind.GeoShape);
	}

	/**
	 * Is this variant instance of kind {@code half_float}?
	 */
	public boolean isHalfFloat() {
		return _kind == Kind.HalfFloat;
	}

	/**
	 * Get the {@code half_float} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code half_float} kind.
	 */
	public HalfFloatNumberProperty halfFloat() {
		return TaggedUnionUtils.get(this, Kind.HalfFloat);
	}

	/**
	 * Is this variant instance of kind {@code histogram}?
	 */
	public boolean isHistogram() {
		return _kind == Kind.Histogram;
	}

	/**
	 * Get the {@code histogram} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code histogram} kind.
	 */
	public HistogramProperty histogram() {
		return TaggedUnionUtils.get(this, Kind.Histogram);
	}

	/**
	 * Is this variant instance of kind {@code integer}?
	 */
	public boolean isInteger() {
		return _kind == Kind.Integer;
	}

	/**
	 * Get the {@code integer} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code integer} kind.
	 */
	public IntegerNumberProperty integer() {
		return TaggedUnionUtils.get(this, Kind.Integer);
	}

	/**
	 * Is this variant instance of kind {@code integer_range}?
	 */
	public boolean isIntegerRange() {
		return _kind == Kind.IntegerRange;
	}

	/**
	 * Get the {@code integer_range} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code integer_range} kind.
	 */
	public IntegerRangeProperty integerRange() {
		return TaggedUnionUtils.get(this, Kind.IntegerRange);
	}

	/**
	 * Is this variant instance of kind {@code ip}?
	 */
	public boolean isIp() {
		return _kind == Kind.Ip;
	}

	/**
	 * Get the {@code ip} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code ip} kind.
	 */
	public IpProperty ip() {
		return TaggedUnionUtils.get(this, Kind.Ip);
	}

	/**
	 * Is this variant instance of kind {@code ip_range}?
	 */
	public boolean isIpRange() {
		return _kind == Kind.IpRange;
	}

	/**
	 * Get the {@code ip_range} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code ip_range} kind.
	 */
	public IpRangeProperty ipRange() {
		return TaggedUnionUtils.get(this, Kind.IpRange);
	}

	/**
	 * Is this variant instance of kind {@code join}?
	 */
	public boolean isJoin() {
		return _kind == Kind.Join;
	}

	/**
	 * Get the {@code join} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code join} kind.
	 */
	public JoinProperty join() {
		return TaggedUnionUtils.get(this, Kind.Join);
	}

	/**
	 * Is this variant instance of kind {@code keyword}?
	 */
	public boolean isKeyword() {
		return _kind == Kind.Keyword;
	}

	/**
	 * Get the {@code keyword} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code keyword} kind.
	 */
	public KeywordProperty keyword() {
		return TaggedUnionUtils.get(this, Kind.Keyword);
	}

	/**
	 * Is this variant instance of kind {@code long}?
	 */
	public boolean isLong() {
		return _kind == Kind.Long;
	}

	/**
	 * Get the {@code long} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code long} kind.
	 */
	public LongNumberProperty long_() {
		return TaggedUnionUtils.get(this, Kind.Long);
	}

	/**
	 * Is this variant instance of kind {@code long_range}?
	 */
	public boolean isLongRange() {
		return _kind == Kind.LongRange;
	}

	/**
	 * Get the {@code long_range} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code long_range} kind.
	 */
	public LongRangeProperty longRange() {
		return TaggedUnionUtils.get(this, Kind.LongRange);
	}

	/**
	 * Is this variant instance of kind {@code murmur3}?
	 */
	public boolean isMurmur3() {
		return _kind == Kind.Murmur3;
	}

	/**
	 * Get the {@code murmur3} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code murmur3} kind.
	 */
	public Murmur3HashProperty murmur3() {
		return TaggedUnionUtils.get(this, Kind.Murmur3);
	}

	/**
	 * Is this variant instance of kind {@code nested}?
	 */
	public boolean isNested() {
		return _kind == Kind.Nested;
	}

	/**
	 * Get the {@code nested} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code nested} kind.
	 */
	public NestedProperty nested() {
		return TaggedUnionUtils.get(this, Kind.Nested);
	}

	/**
	 * Is this variant instance of kind {@code object}?
	 */
	public boolean isObject() {
		return _kind == Kind.Object;
	}

	/**
	 * Get the {@code object} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code object} kind.
	 */
	public ObjectProperty object() {
		return TaggedUnionUtils.get(this, Kind.Object);
	}

	/**
	 * Is this variant instance of kind {@code percolator}?
	 */
	public boolean isPercolator() {
		return _kind == Kind.Percolator;
	}

	/**
	 * Get the {@code percolator} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code percolator} kind.
	 */
	public PercolatorProperty percolator() {
		return TaggedUnionUtils.get(this, Kind.Percolator);
	}

	/**
	 * Is this variant instance of kind {@code point}?
	 */
	public boolean isPoint() {
		return _kind == Kind.Point;
	}

	/**
	 * Get the {@code point} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code point} kind.
	 */
	public PointProperty point() {
		return TaggedUnionUtils.get(this, Kind.Point);
	}

	/**
	 * Is this variant instance of kind {@code rank_feature}?
	 */
	public boolean isRankFeature() {
		return _kind == Kind.RankFeature;
	}

	/**
	 * Get the {@code rank_feature} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code rank_feature} kind.
	 */
	public RankFeatureProperty rankFeature() {
		return TaggedUnionUtils.get(this, Kind.RankFeature);
	}

	/**
	 * Is this variant instance of kind {@code rank_features}?
	 */
	public boolean isRankFeatures() {
		return _kind == Kind.RankFeatures;
	}

	/**
	 * Get the {@code rank_features} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code rank_features} kind.
	 */
	public RankFeaturesProperty rankFeatures() {
		return TaggedUnionUtils.get(this, Kind.RankFeatures);
	}

	/**
	 * Is this variant instance of kind {@code scaled_float}?
	 */
	public boolean isScaledFloat() {
		return _kind == Kind.ScaledFloat;
	}

	/**
	 * Get the {@code scaled_float} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code scaled_float} kind.
	 */
	public ScaledFloatNumberProperty scaledFloat() {
		return TaggedUnionUtils.get(this, Kind.ScaledFloat);
	}

	/**
	 * Is this variant instance of kind {@code search_as_you_type}?
	 */
	public boolean isSearchAsYouType() {
		return _kind == Kind.SearchAsYouType;
	}

	/**
	 * Get the {@code search_as_you_type} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code search_as_you_type}
	 *             kind.
	 */
	public SearchAsYouTypeProperty searchAsYouType() {
		return TaggedUnionUtils.get(this, Kind.SearchAsYouType);
	}

	/**
	 * Is this variant instance of kind {@code shape}?
	 */
	public boolean isShape() {
		return _kind == Kind.Shape;
	}

	/**
	 * Get the {@code shape} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code shape} kind.
	 */
	public ShapeProperty shape() {
		return TaggedUnionUtils.get(this, Kind.Shape);
	}

	/**
	 * Is this variant instance of kind {@code short}?
	 */
	public boolean isShort() {
		return _kind == Kind.Short;
	}

	/**
	 * Get the {@code short} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code short} kind.
	 */
	public ShortNumberProperty short_() {
		return TaggedUnionUtils.get(this, Kind.Short);
	}

	/**
	 * Is this variant instance of kind {@code text}?
	 */
	public boolean isText() {
		return _kind == Kind.Text;
	}

	/**
	 * Get the {@code text} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code text} kind.
	 */
	public TextProperty text() {
		return TaggedUnionUtils.get(this, Kind.Text);
	}

	/**
	 * Is this variant instance of kind {@code token_count}?
	 */
	public boolean isTokenCount() {
		return _kind == Kind.TokenCount;
	}

	/**
	 * Get the {@code token_count} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code token_count} kind.
	 */
	public TokenCountProperty tokenCount() {
		return TaggedUnionUtils.get(this, Kind.TokenCount);
	}

	/**
	 * Is this variant instance of kind {@code unsigned_long}?
	 */
	public boolean isUnsignedLong() {
		return _kind == Kind.UnsignedLong;
	}

	/**
	 * Get the {@code unsigned_long} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code unsigned_long} kind.
	 */
	public UnsignedLongNumberProperty unsignedLong() {
		return TaggedUnionUtils.get(this, Kind.UnsignedLong);
	}

	/**
	 * Is this variant instance of kind {@code version}?
	 */
	public boolean isVersion() {
		return _kind == Kind.Version;
	}

	/**
	 * Get the {@code version} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code version} kind.
	 */
	public VersionProperty version() {
		return TaggedUnionUtils.get(this, Kind.Version);
	}

	/**
	 * Is this variant instance of kind {@code wildcard}?
	 */
	public boolean isWildcard() {
		return _kind == Kind.Wildcard;
	}

	/**
	 * Get the {@code wildcard} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code wildcard} kind.
	 */
	public WildcardProperty wildcard() {
		return TaggedUnionUtils.get(this, Kind.Wildcard);
	}

	@Override
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {

		mapper.serialize(_value, generator);

	}

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Property> {
		private Kind _kind;
		private PropertyVariant _value;

		public ObjectBuilder<Property> aggregateMetricDouble(AggregateMetricDoubleProperty v) {
			this._kind = Kind.AggregateMetricDouble;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> aggregateMetricDouble(
				Function<AggregateMetricDoubleProperty.Builder, ObjectBuilder<AggregateMetricDoubleProperty>> fn) {
			return this.aggregateMetricDouble(fn.apply(new AggregateMetricDoubleProperty.Builder()).build());
		}

		public ObjectBuilder<Property> binary(BinaryProperty v) {
			this._kind = Kind.Binary;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> binary(Function<BinaryProperty.Builder, ObjectBuilder<BinaryProperty>> fn) {
			return this.binary(fn.apply(new BinaryProperty.Builder()).build());
		}

		public ObjectBuilder<Property> boolean_(BooleanProperty v) {
			this._kind = Kind.Boolean;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> boolean_(Function<BooleanProperty.Builder, ObjectBuilder<BooleanProperty>> fn) {
			return this.boolean_(fn.apply(new BooleanProperty.Builder()).build());
		}

		public ObjectBuilder<Property> byte_(ByteNumberProperty v) {
			this._kind = Kind.Byte;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> byte_(
				Function<ByteNumberProperty.Builder, ObjectBuilder<ByteNumberProperty>> fn) {
			return this.byte_(fn.apply(new ByteNumberProperty.Builder()).build());
		}

		public ObjectBuilder<Property> completion(CompletionProperty v) {
			this._kind = Kind.Completion;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> completion(
				Function<CompletionProperty.Builder, ObjectBuilder<CompletionProperty>> fn) {
			return this.completion(fn.apply(new CompletionProperty.Builder()).build());
		}

		public ObjectBuilder<Property> constantKeyword(ConstantKeywordProperty v) {
			this._kind = Kind.ConstantKeyword;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> constantKeyword(
				Function<ConstantKeywordProperty.Builder, ObjectBuilder<ConstantKeywordProperty>> fn) {
			return this.constantKeyword(fn.apply(new ConstantKeywordProperty.Builder()).build());
		}

		public ObjectBuilder<Property> dateNanos(DateNanosProperty v) {
			this._kind = Kind.DateNanos;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> dateNanos(
				Function<DateNanosProperty.Builder, ObjectBuilder<DateNanosProperty>> fn) {
			return this.dateNanos(fn.apply(new DateNanosProperty.Builder()).build());
		}

		public ObjectBuilder<Property> date(DateProperty v) {
			this._kind = Kind.Date;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> date(Function<DateProperty.Builder, ObjectBuilder<DateProperty>> fn) {
			return this.date(fn.apply(new DateProperty.Builder()).build());
		}

		public ObjectBuilder<Property> dateRange(DateRangeProperty v) {
			this._kind = Kind.DateRange;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> dateRange(
				Function<DateRangeProperty.Builder, ObjectBuilder<DateRangeProperty>> fn) {
			return this.dateRange(fn.apply(new DateRangeProperty.Builder()).build());
		}

		public ObjectBuilder<Property> denseVector(DenseVectorProperty v) {
			this._kind = Kind.DenseVector;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> denseVector(
				Function<DenseVectorProperty.Builder, ObjectBuilder<DenseVectorProperty>> fn) {
			return this.denseVector(fn.apply(new DenseVectorProperty.Builder()).build());
		}

		public ObjectBuilder<Property> double_(DoubleNumberProperty v) {
			this._kind = Kind.Double;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> double_(
				Function<DoubleNumberProperty.Builder, ObjectBuilder<DoubleNumberProperty>> fn) {
			return this.double_(fn.apply(new DoubleNumberProperty.Builder()).build());
		}

		public ObjectBuilder<Property> doubleRange(DoubleRangeProperty v) {
			this._kind = Kind.DoubleRange;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> doubleRange(
				Function<DoubleRangeProperty.Builder, ObjectBuilder<DoubleRangeProperty>> fn) {
			return this.doubleRange(fn.apply(new DoubleRangeProperty.Builder()).build());
		}

		public ObjectBuilder<Property> alias(FieldAliasProperty v) {
			this._kind = Kind.Alias;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> alias(
				Function<FieldAliasProperty.Builder, ObjectBuilder<FieldAliasProperty>> fn) {
			return this.alias(fn.apply(new FieldAliasProperty.Builder()).build());
		}

		public ObjectBuilder<Property> flattened(FlattenedProperty v) {
			this._kind = Kind.Flattened;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> flattened(
				Function<FlattenedProperty.Builder, ObjectBuilder<FlattenedProperty>> fn) {
			return this.flattened(fn.apply(new FlattenedProperty.Builder()).build());
		}

		public ObjectBuilder<Property> float_(FloatNumberProperty v) {
			this._kind = Kind.Float;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> float_(
				Function<FloatNumberProperty.Builder, ObjectBuilder<FloatNumberProperty>> fn) {
			return this.float_(fn.apply(new FloatNumberProperty.Builder()).build());
		}

		public ObjectBuilder<Property> floatRange(FloatRangeProperty v) {
			this._kind = Kind.FloatRange;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> floatRange(
				Function<FloatRangeProperty.Builder, ObjectBuilder<FloatRangeProperty>> fn) {
			return this.floatRange(fn.apply(new FloatRangeProperty.Builder()).build());
		}

		public ObjectBuilder<Property> geoPoint(GeoPointProperty v) {
			this._kind = Kind.GeoPoint;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> geoPoint(
				Function<GeoPointProperty.Builder, ObjectBuilder<GeoPointProperty>> fn) {
			return this.geoPoint(fn.apply(new GeoPointProperty.Builder()).build());
		}

		public ObjectBuilder<Property> geoShape(GeoShapeProperty v) {
			this._kind = Kind.GeoShape;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> geoShape(
				Function<GeoShapeProperty.Builder, ObjectBuilder<GeoShapeProperty>> fn) {
			return this.geoShape(fn.apply(new GeoShapeProperty.Builder()).build());
		}

		public ObjectBuilder<Property> halfFloat(HalfFloatNumberProperty v) {
			this._kind = Kind.HalfFloat;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> halfFloat(
				Function<HalfFloatNumberProperty.Builder, ObjectBuilder<HalfFloatNumberProperty>> fn) {
			return this.halfFloat(fn.apply(new HalfFloatNumberProperty.Builder()).build());
		}

		public ObjectBuilder<Property> histogram(HistogramProperty v) {
			this._kind = Kind.Histogram;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> histogram(
				Function<HistogramProperty.Builder, ObjectBuilder<HistogramProperty>> fn) {
			return this.histogram(fn.apply(new HistogramProperty.Builder()).build());
		}

		public ObjectBuilder<Property> integer(IntegerNumberProperty v) {
			this._kind = Kind.Integer;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> integer(
				Function<IntegerNumberProperty.Builder, ObjectBuilder<IntegerNumberProperty>> fn) {
			return this.integer(fn.apply(new IntegerNumberProperty.Builder()).build());
		}

		public ObjectBuilder<Property> integerRange(IntegerRangeProperty v) {
			this._kind = Kind.IntegerRange;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> integerRange(
				Function<IntegerRangeProperty.Builder, ObjectBuilder<IntegerRangeProperty>> fn) {
			return this.integerRange(fn.apply(new IntegerRangeProperty.Builder()).build());
		}

		public ObjectBuilder<Property> ip(IpProperty v) {
			this._kind = Kind.Ip;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> ip(Function<IpProperty.Builder, ObjectBuilder<IpProperty>> fn) {
			return this.ip(fn.apply(new IpProperty.Builder()).build());
		}

		public ObjectBuilder<Property> ipRange(IpRangeProperty v) {
			this._kind = Kind.IpRange;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> ipRange(Function<IpRangeProperty.Builder, ObjectBuilder<IpRangeProperty>> fn) {
			return this.ipRange(fn.apply(new IpRangeProperty.Builder()).build());
		}

		public ObjectBuilder<Property> join(JoinProperty v) {
			this._kind = Kind.Join;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> join(Function<JoinProperty.Builder, ObjectBuilder<JoinProperty>> fn) {
			return this.join(fn.apply(new JoinProperty.Builder()).build());
		}

		public ObjectBuilder<Property> keyword(KeywordProperty v) {
			this._kind = Kind.Keyword;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> keyword(Function<KeywordProperty.Builder, ObjectBuilder<KeywordProperty>> fn) {
			return this.keyword(fn.apply(new KeywordProperty.Builder()).build());
		}

		public ObjectBuilder<Property> long_(LongNumberProperty v) {
			this._kind = Kind.Long;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> long_(
				Function<LongNumberProperty.Builder, ObjectBuilder<LongNumberProperty>> fn) {
			return this.long_(fn.apply(new LongNumberProperty.Builder()).build());
		}

		public ObjectBuilder<Property> longRange(LongRangeProperty v) {
			this._kind = Kind.LongRange;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> longRange(
				Function<LongRangeProperty.Builder, ObjectBuilder<LongRangeProperty>> fn) {
			return this.longRange(fn.apply(new LongRangeProperty.Builder()).build());
		}

		public ObjectBuilder<Property> murmur3(Murmur3HashProperty v) {
			this._kind = Kind.Murmur3;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> murmur3(
				Function<Murmur3HashProperty.Builder, ObjectBuilder<Murmur3HashProperty>> fn) {
			return this.murmur3(fn.apply(new Murmur3HashProperty.Builder()).build());
		}

		public ObjectBuilder<Property> nested(NestedProperty v) {
			this._kind = Kind.Nested;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> nested(Function<NestedProperty.Builder, ObjectBuilder<NestedProperty>> fn) {
			return this.nested(fn.apply(new NestedProperty.Builder()).build());
		}

		public ObjectBuilder<Property> object(ObjectProperty v) {
			this._kind = Kind.Object;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> object(Function<ObjectProperty.Builder, ObjectBuilder<ObjectProperty>> fn) {
			return this.object(fn.apply(new ObjectProperty.Builder()).build());
		}

		public ObjectBuilder<Property> percolator(PercolatorProperty v) {
			this._kind = Kind.Percolator;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> percolator(
				Function<PercolatorProperty.Builder, ObjectBuilder<PercolatorProperty>> fn) {
			return this.percolator(fn.apply(new PercolatorProperty.Builder()).build());
		}

		public ObjectBuilder<Property> point(PointProperty v) {
			this._kind = Kind.Point;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> point(Function<PointProperty.Builder, ObjectBuilder<PointProperty>> fn) {
			return this.point(fn.apply(new PointProperty.Builder()).build());
		}

		public ObjectBuilder<Property> rankFeature(RankFeatureProperty v) {
			this._kind = Kind.RankFeature;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> rankFeature(
				Function<RankFeatureProperty.Builder, ObjectBuilder<RankFeatureProperty>> fn) {
			return this.rankFeature(fn.apply(new RankFeatureProperty.Builder()).build());
		}

		public ObjectBuilder<Property> rankFeatures(RankFeaturesProperty v) {
			this._kind = Kind.RankFeatures;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> rankFeatures(
				Function<RankFeaturesProperty.Builder, ObjectBuilder<RankFeaturesProperty>> fn) {
			return this.rankFeatures(fn.apply(new RankFeaturesProperty.Builder()).build());
		}

		public ObjectBuilder<Property> scaledFloat(ScaledFloatNumberProperty v) {
			this._kind = Kind.ScaledFloat;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> scaledFloat(
				Function<ScaledFloatNumberProperty.Builder, ObjectBuilder<ScaledFloatNumberProperty>> fn) {
			return this.scaledFloat(fn.apply(new ScaledFloatNumberProperty.Builder()).build());
		}

		public ObjectBuilder<Property> searchAsYouType(SearchAsYouTypeProperty v) {
			this._kind = Kind.SearchAsYouType;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> searchAsYouType(
				Function<SearchAsYouTypeProperty.Builder, ObjectBuilder<SearchAsYouTypeProperty>> fn) {
			return this.searchAsYouType(fn.apply(new SearchAsYouTypeProperty.Builder()).build());
		}

		public ObjectBuilder<Property> shape(ShapeProperty v) {
			this._kind = Kind.Shape;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> shape(Function<ShapeProperty.Builder, ObjectBuilder<ShapeProperty>> fn) {
			return this.shape(fn.apply(new ShapeProperty.Builder()).build());
		}

		public ObjectBuilder<Property> short_(ShortNumberProperty v) {
			this._kind = Kind.Short;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> short_(
				Function<ShortNumberProperty.Builder, ObjectBuilder<ShortNumberProperty>> fn) {
			return this.short_(fn.apply(new ShortNumberProperty.Builder()).build());
		}

		public ObjectBuilder<Property> text(TextProperty v) {
			this._kind = Kind.Text;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> text(Function<TextProperty.Builder, ObjectBuilder<TextProperty>> fn) {
			return this.text(fn.apply(new TextProperty.Builder()).build());
		}

		public ObjectBuilder<Property> tokenCount(TokenCountProperty v) {
			this._kind = Kind.TokenCount;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> tokenCount(
				Function<TokenCountProperty.Builder, ObjectBuilder<TokenCountProperty>> fn) {
			return this.tokenCount(fn.apply(new TokenCountProperty.Builder()).build());
		}

		public ObjectBuilder<Property> unsignedLong(UnsignedLongNumberProperty v) {
			this._kind = Kind.UnsignedLong;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> unsignedLong(
				Function<UnsignedLongNumberProperty.Builder, ObjectBuilder<UnsignedLongNumberProperty>> fn) {
			return this.unsignedLong(fn.apply(new UnsignedLongNumberProperty.Builder()).build());
		}

		public ObjectBuilder<Property> version(VersionProperty v) {
			this._kind = Kind.Version;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> version(Function<VersionProperty.Builder, ObjectBuilder<VersionProperty>> fn) {
			return this.version(fn.apply(new VersionProperty.Builder()).build());
		}

		public ObjectBuilder<Property> wildcard(WildcardProperty v) {
			this._kind = Kind.Wildcard;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Property> wildcard(
				Function<WildcardProperty.Builder, ObjectBuilder<WildcardProperty>> fn) {
			return this.wildcard(fn.apply(new WildcardProperty.Builder()).build());
		}

		public Property build() {
			_checkSingleUse();
			return new Property(this);
		}

	}

	protected static void setupPropertyDeserializer(ObjectDeserializer<Builder> op) {

		op.add(Builder::aggregateMetricDouble, AggregateMetricDoubleProperty._DESERIALIZER, "aggregate_metric_double");
		op.add(Builder::binary, BinaryProperty._DESERIALIZER, "binary");
		op.add(Builder::boolean_, BooleanProperty._DESERIALIZER, "boolean");
		op.add(Builder::byte_, ByteNumberProperty._DESERIALIZER, "byte");
		op.add(Builder::completion, CompletionProperty._DESERIALIZER, "completion");
		op.add(Builder::constantKeyword, ConstantKeywordProperty._DESERIALIZER, "constant_keyword");
		op.add(Builder::dateNanos, DateNanosProperty._DESERIALIZER, "date_nanos");
		op.add(Builder::date, DateProperty._DESERIALIZER, "date");
		op.add(Builder::dateRange, DateRangeProperty._DESERIALIZER, "date_range");
		op.add(Builder::denseVector, DenseVectorProperty._DESERIALIZER, "dense_vector");
		op.add(Builder::double_, DoubleNumberProperty._DESERIALIZER, "double");
		op.add(Builder::doubleRange, DoubleRangeProperty._DESERIALIZER, "double_range");
		op.add(Builder::alias, FieldAliasProperty._DESERIALIZER, "alias");
		op.add(Builder::flattened, FlattenedProperty._DESERIALIZER, "flattened");
		op.add(Builder::float_, FloatNumberProperty._DESERIALIZER, "float");
		op.add(Builder::floatRange, FloatRangeProperty._DESERIALIZER, "float_range");
		op.add(Builder::geoPoint, GeoPointProperty._DESERIALIZER, "geo_point");
		op.add(Builder::geoShape, GeoShapeProperty._DESERIALIZER, "geo_shape");
		op.add(Builder::halfFloat, HalfFloatNumberProperty._DESERIALIZER, "half_float");
		op.add(Builder::histogram, HistogramProperty._DESERIALIZER, "histogram");
		op.add(Builder::integer, IntegerNumberProperty._DESERIALIZER, "integer");
		op.add(Builder::integerRange, IntegerRangeProperty._DESERIALIZER, "integer_range");
		op.add(Builder::ip, IpProperty._DESERIALIZER, "ip");
		op.add(Builder::ipRange, IpRangeProperty._DESERIALIZER, "ip_range");
		op.add(Builder::join, JoinProperty._DESERIALIZER, "join");
		op.add(Builder::keyword, KeywordProperty._DESERIALIZER, "keyword");
		op.add(Builder::long_, LongNumberProperty._DESERIALIZER, "long");
		op.add(Builder::longRange, LongRangeProperty._DESERIALIZER, "long_range");
		op.add(Builder::murmur3, Murmur3HashProperty._DESERIALIZER, "murmur3");
		op.add(Builder::nested, NestedProperty._DESERIALIZER, "nested");
		op.add(Builder::object, ObjectProperty._DESERIALIZER, "object");
		op.add(Builder::percolator, PercolatorProperty._DESERIALIZER, "percolator");
		op.add(Builder::point, PointProperty._DESERIALIZER, "point");
		op.add(Builder::rankFeature, RankFeatureProperty._DESERIALIZER, "rank_feature");
		op.add(Builder::rankFeatures, RankFeaturesProperty._DESERIALIZER, "rank_features");
		op.add(Builder::scaledFloat, ScaledFloatNumberProperty._DESERIALIZER, "scaled_float");
		op.add(Builder::searchAsYouType, SearchAsYouTypeProperty._DESERIALIZER, "search_as_you_type");
		op.add(Builder::shape, ShapeProperty._DESERIALIZER, "shape");
		op.add(Builder::short_, ShortNumberProperty._DESERIALIZER, "short");
		op.add(Builder::text, TextProperty._DESERIALIZER, "text");
		op.add(Builder::tokenCount, TokenCountProperty._DESERIALIZER, "token_count");
		op.add(Builder::unsignedLong, UnsignedLongNumberProperty._DESERIALIZER, "unsigned_long");
		op.add(Builder::version, VersionProperty._DESERIALIZER, "version");
		op.add(Builder::wildcard, WildcardProperty._DESERIALIZER, "wildcard");

		op.setTypeProperty("type", "object");

	}

	public static final JsonpDeserializer<Property> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			Property::setupPropertyDeserializer, Builder::build);
}
