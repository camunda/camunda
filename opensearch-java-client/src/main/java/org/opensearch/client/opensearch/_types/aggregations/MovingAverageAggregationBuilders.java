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

/**
 * Builders for {@link MovingAverageAggregation} variants.
 */
public class MovingAverageAggregationBuilders {
	private MovingAverageAggregationBuilders() {
	}

	/**
	 * Creates a builder for the {@link EwmaMovingAverageAggregation ewma}
	 * {@code MovingAverageAggregation} variant.
	 */
	public static EwmaMovingAverageAggregation.Builder ewma() {
		return new EwmaMovingAverageAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link HoltMovingAverageAggregation holt}
	 * {@code MovingAverageAggregation} variant.
	 */
	public static HoltMovingAverageAggregation.Builder holt() {
		return new HoltMovingAverageAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link HoltWintersMovingAverageAggregation
	 * holt_winters} {@code MovingAverageAggregation} variant.
	 */
	public static HoltWintersMovingAverageAggregation.Builder holtWinters() {
		return new HoltWintersMovingAverageAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link LinearMovingAverageAggregation linear}
	 * {@code MovingAverageAggregation} variant.
	 */
	public static LinearMovingAverageAggregation.Builder linear() {
		return new LinearMovingAverageAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link SimpleMovingAverageAggregation simple}
	 * {@code MovingAverageAggregation} variant.
	 */
	public static SimpleMovingAverageAggregation.Builder simple() {
		return new SimpleMovingAverageAggregation.Builder();
	}

}
