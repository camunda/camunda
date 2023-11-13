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

package org.opensearch.client.opensearch._types.query_dsl;

/**
 * Builders for {@link IntervalsQuery} variants.
 */
public class IntervalsQueryBuilders {
	private IntervalsQueryBuilders() {
	}

	/**
	 * Creates a builder for the {@link IntervalsAllOf all_of}
	 * {@code IntervalsQuery} variant.
	 */
	public static IntervalsAllOf.Builder allOf() {
		return new IntervalsAllOf.Builder();
	}

	/**
	 * Creates a builder for the {@link IntervalsAnyOf any_of}
	 * {@code IntervalsQuery} variant.
	 */
	public static IntervalsAnyOf.Builder anyOf() {
		return new IntervalsAnyOf.Builder();
	}

	/**
	 * Creates a builder for the {@link IntervalsFuzzy fuzzy} {@code IntervalsQuery}
	 * variant.
	 */
	public static IntervalsFuzzy.Builder fuzzy() {
		return new IntervalsFuzzy.Builder();
	}

	/**
	 * Creates a builder for the {@link IntervalsMatch match} {@code IntervalsQuery}
	 * variant.
	 */
	public static IntervalsMatch.Builder match() {
		return new IntervalsMatch.Builder();
	}

	/**
	 * Creates a builder for the {@link IntervalsPrefix prefix}
	 * {@code IntervalsQuery} variant.
	 */
	public static IntervalsPrefix.Builder prefix() {
		return new IntervalsPrefix.Builder();
	}

	/**
	 * Creates a builder for the {@link IntervalsWildcard wildcard}
	 * {@code IntervalsQuery} variant.
	 */
	public static IntervalsWildcard.Builder wildcard() {
		return new IntervalsWildcard.Builder();
	}

}
