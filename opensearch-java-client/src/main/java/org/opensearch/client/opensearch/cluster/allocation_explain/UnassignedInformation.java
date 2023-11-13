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

package org.opensearch.client.opensearch.cluster.allocation_explain;

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
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: cluster.allocation_explain.UnassignedInformation

@JsonpDeserializable
public class UnassignedInformation implements JsonpSerializable {
	private final String at;

	@Nullable
	private final String lastAllocationStatus;

	private final UnassignedInformationReason reason;

	@Nullable
	private final String details;

	@Nullable
	private final Integer failedAllocationAttempts;

	@Nullable
	private final Boolean delayed;

	@Nullable
	private final String allocationStatus;

	// ---------------------------------------------------------------------------------------------

	private UnassignedInformation(Builder builder) {

		this.at = ApiTypeHelper.requireNonNull(builder.at, this, "at");
		this.lastAllocationStatus = builder.lastAllocationStatus;
		this.reason = ApiTypeHelper.requireNonNull(builder.reason, this, "reason");
		this.details = builder.details;
		this.failedAllocationAttempts = builder.failedAllocationAttempts;
		this.delayed = builder.delayed;
		this.allocationStatus = builder.allocationStatus;

	}

	public static UnassignedInformation of(Function<Builder, ObjectBuilder<UnassignedInformation>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code at}
	 */
	public final String at() {
		return this.at;
	}

	/**
	 * API name: {@code last_allocation_status}
	 */
	@Nullable
	public final String lastAllocationStatus() {
		return this.lastAllocationStatus;
	}

	/**
	 * Required - API name: {@code reason}
	 */
	public final UnassignedInformationReason reason() {
		return this.reason;
	}

	/**
	 * API name: {@code details}
	 */
	@Nullable
	public final String details() {
		return this.details;
	}

	/**
	 * API name: {@code failed_allocation_attempts}
	 */
	@Nullable
	public final Integer failedAllocationAttempts() {
		return this.failedAllocationAttempts;
	}

	/**
	 * API name: {@code delayed}
	 */
	@Nullable
	public final Boolean delayed() {
		return this.delayed;
	}

	/**
	 * API name: {@code allocation_status}
	 */
	@Nullable
	public final String allocationStatus() {
		return this.allocationStatus;
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

		generator.writeKey("at");
		generator.write(this.at);

		if (this.lastAllocationStatus != null) {
			generator.writeKey("last_allocation_status");
			generator.write(this.lastAllocationStatus);

		}
		generator.writeKey("reason");
		this.reason.serialize(generator, mapper);
		if (this.details != null) {
			generator.writeKey("details");
			generator.write(this.details);

		}
		if (this.failedAllocationAttempts != null) {
			generator.writeKey("failed_allocation_attempts");
			generator.write(this.failedAllocationAttempts);

		}
		if (this.delayed != null) {
			generator.writeKey("delayed");
			generator.write(this.delayed);

		}
		if (this.allocationStatus != null) {
			generator.writeKey("allocation_status");
			generator.write(this.allocationStatus);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link UnassignedInformation}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<UnassignedInformation> {
		private String at;

		@Nullable
		private String lastAllocationStatus;

		private UnassignedInformationReason reason;

		@Nullable
		private String details;

		@Nullable
		private Integer failedAllocationAttempts;

		@Nullable
		private Boolean delayed;

		@Nullable
		private String allocationStatus;

		/**
		 * Required - API name: {@code at}
		 */
		public final Builder at(String value) {
			this.at = value;
			return this;
		}

		/**
		 * API name: {@code last_allocation_status}
		 */
		public final Builder lastAllocationStatus(@Nullable String value) {
			this.lastAllocationStatus = value;
			return this;
		}

		/**
		 * Required - API name: {@code reason}
		 */
		public final Builder reason(UnassignedInformationReason value) {
			this.reason = value;
			return this;
		}

		/**
		 * API name: {@code details}
		 */
		public final Builder details(@Nullable String value) {
			this.details = value;
			return this;
		}

		/**
		 * API name: {@code failed_allocation_attempts}
		 */
		public final Builder failedAllocationAttempts(@Nullable Integer value) {
			this.failedAllocationAttempts = value;
			return this;
		}

		/**
		 * API name: {@code delayed}
		 */
		public final Builder delayed(@Nullable Boolean value) {
			this.delayed = value;
			return this;
		}

		/**
		 * API name: {@code allocation_status}
		 */
		public final Builder allocationStatus(@Nullable String value) {
			this.allocationStatus = value;
			return this;
		}

		/**
		 * Builds a {@link UnassignedInformation}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public UnassignedInformation build() {
			_checkSingleUse();

			return new UnassignedInformation(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link UnassignedInformation}
	 */
	public static final JsonpDeserializer<UnassignedInformation> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, UnassignedInformation::setupUnassignedInformationDeserializer);

	protected static void setupUnassignedInformationDeserializer(ObjectDeserializer<UnassignedInformation.Builder> op) {

		op.add(Builder::at, JsonpDeserializer.stringDeserializer(), "at");
		op.add(Builder::lastAllocationStatus, JsonpDeserializer.stringDeserializer(), "last_allocation_status");
		op.add(Builder::reason, UnassignedInformationReason._DESERIALIZER, "reason");
		op.add(Builder::details, JsonpDeserializer.stringDeserializer(), "details");
		op.add(Builder::failedAllocationAttempts, JsonpDeserializer.integerDeserializer(),
				"failed_allocation_attempts");
		op.add(Builder::delayed, JsonpDeserializer.booleanDeserializer(), "delayed");
		op.add(Builder::allocationStatus, JsonpDeserializer.stringDeserializer(), "allocation_status");

	}

}
