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

package org.opensearch.client.opensearch.ingest;

import org.opensearch.client.opensearch._types.Script;

/**
 * Builders for {@link Processor} variants.
 */
public class ProcessorBuilders {
	private ProcessorBuilders() {
	}

	/**
	 * Creates a builder for the {@link AttachmentProcessor attachment}
	 * {@code Processor} variant.
	 */
	public static AttachmentProcessor.Builder attachment() {
		return new AttachmentProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link AppendProcessor append} {@code Processor}
	 * variant.
	 */
	public static AppendProcessor.Builder append() {
		return new AppendProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link CsvProcessor csv} {@code Processor} variant.
	 */
	public static CsvProcessor.Builder csv() {
		return new CsvProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link ConvertProcessor convert} {@code Processor}
	 * variant.
	 */
	public static ConvertProcessor.Builder convert() {
		return new ConvertProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link DateProcessor date} {@code Processor}
	 * variant.
	 */
	public static DateProcessor.Builder date() {
		return new DateProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link DateIndexNameProcessor date_index_name}
	 * {@code Processor} variant.
	 */
	public static DateIndexNameProcessor.Builder dateIndexName() {
		return new DateIndexNameProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link DotExpanderProcessor dot_expander}
	 * {@code Processor} variant.
	 */
	public static DotExpanderProcessor.Builder dotExpander() {
		return new DotExpanderProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link FailProcessor fail} {@code Processor}
	 * variant.
	 */
	public static FailProcessor.Builder fail() {
		return new FailProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link ForeachProcessor foreach} {@code Processor}
	 * variant.
	 */
	public static ForeachProcessor.Builder foreach() {
		return new ForeachProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link JsonProcessor json} {@code Processor}
	 * variant.
	 */
	public static JsonProcessor.Builder json() {
		return new JsonProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link UserAgentProcessor user_agent}
	 * {@code Processor} variant.
	 */
	public static UserAgentProcessor.Builder userAgent() {
		return new UserAgentProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link KeyValueProcessor kv} {@code Processor}
	 * variant.
	 */
	public static KeyValueProcessor.Builder kv() {
		return new KeyValueProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link GeoIpProcessor geoip} {@code Processor}
	 * variant.
	 */
	public static GeoIpProcessor.Builder geoip() {
		return new GeoIpProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link GrokProcessor grok} {@code Processor}
	 * variant.
	 */
	public static GrokProcessor.Builder grok() {
		return new GrokProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link GsubProcessor gsub} {@code Processor}
	 * variant.
	 */
	public static GsubProcessor.Builder gsub() {
		return new GsubProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link JoinProcessor join} {@code Processor}
	 * variant.
	 */
	public static JoinProcessor.Builder join() {
		return new JoinProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link LowercaseProcessor lowercase}
	 * {@code Processor} variant.
	 */
	public static LowercaseProcessor.Builder lowercase() {
		return new LowercaseProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link RemoveProcessor remove} {@code Processor}
	 * variant.
	 */
	public static RemoveProcessor.Builder remove() {
		return new RemoveProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link RenameProcessor rename} {@code Processor}
	 * variant.
	 */
	public static RenameProcessor.Builder rename() {
		return new RenameProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link Script script} {@code Processor} variant.
	 */
	public static Script.Builder script() {
		return new Script.Builder();
	}

	/**
	 * Creates a builder for the {@link SetProcessor set} {@code Processor} variant.
	 */
	public static SetProcessor.Builder set() {
		return new SetProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link SortProcessor sort} {@code Processor}
	 * variant.
	 */
	public static SortProcessor.Builder sort() {
		return new SortProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link SplitProcessor split} {@code Processor}
	 * variant.
	 */
	public static SplitProcessor.Builder split() {
		return new SplitProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link TrimProcessor trim} {@code Processor}
	 * variant.
	 */
	public static TrimProcessor.Builder trim() {
		return new TrimProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link UppercaseProcessor uppercase}
	 * {@code Processor} variant.
	 */
	public static UppercaseProcessor.Builder uppercase() {
		return new UppercaseProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link UrlDecodeProcessor urldecode}
	 * {@code Processor} variant.
	 */
	public static UrlDecodeProcessor.Builder urldecode() {
		return new UrlDecodeProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link BytesProcessor bytes} {@code Processor}
	 * variant.
	 */
	public static BytesProcessor.Builder bytes() {
		return new BytesProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link DissectProcessor dissect} {@code Processor}
	 * variant.
	 */
	public static DissectProcessor.Builder dissect() {
		return new DissectProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link SetSecurityUserProcessor set_security_user}
	 * {@code Processor} variant.
	 */
	public static SetSecurityUserProcessor.Builder setSecurityUser() {
		return new SetSecurityUserProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link PipelineProcessor pipeline}
	 * {@code Processor} variant.
	 */
	public static PipelineProcessor.Builder pipeline() {
		return new PipelineProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link DropProcessor drop} {@code Processor}
	 * variant.
	 */
	public static DropProcessor.Builder drop() {
		return new DropProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link CircleProcessor circle} {@code Processor}
	 * variant.
	 */
	public static CircleProcessor.Builder circle() {
		return new CircleProcessor.Builder();
	}

	/**
	 * Creates a builder for the {@link InferenceProcessor inference}
	 * {@code Processor} variant.
	 */
	public static InferenceProcessor.Builder inference() {
		return new InferenceProcessor.Builder();
	}

}
