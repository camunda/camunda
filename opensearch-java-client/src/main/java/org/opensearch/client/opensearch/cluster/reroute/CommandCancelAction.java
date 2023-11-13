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

package org.opensearch.client.opensearch.cluster.reroute;

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

// typedef: cluster.reroute.CommandCancelAction


@JsonpDeserializable
public class CommandCancelAction implements JsonpSerializable {
	private final String index;

	private final int shard;

	private final String node;

	@Nullable
	private final Boolean allowPrimary;

	// ---------------------------------------------------------------------------------------------

	private CommandCancelAction(Builder builder) {

		this.index = ApiTypeHelper.requireNonNull(builder.index, this, "index");
		this.shard = ApiTypeHelper.requireNonNull(builder.shard, this, "shard");
		this.node = ApiTypeHelper.requireNonNull(builder.node, this, "node");
		this.allowPrimary = builder.allowPrimary;

	}

	public static CommandCancelAction of(Function<Builder, ObjectBuilder<CommandCancelAction>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code index}
	 */
	public final String index() {
		return this.index;
	}

	/**
	 * Required - API name: {@code shard}
	 */
	public final int shard() {
		return this.shard;
	}

	/**
	 * Required - API name: {@code node}
	 */
	public final String node() {
		return this.node;
	}

	/**
	 * API name: {@code allow_primary}
	 */
	@Nullable
	public final Boolean allowPrimary() {
		return this.allowPrimary;
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

		generator.writeKey("index");
		generator.write(this.index);

		generator.writeKey("shard");
		generator.write(this.shard);

		generator.writeKey("node");
		generator.write(this.node);

		if (this.allowPrimary != null) {
			generator.writeKey("allow_primary");
			generator.write(this.allowPrimary);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link CommandCancelAction}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<CommandCancelAction> {
		private String index;

		private Integer shard;

		private String node;

		@Nullable
		private Boolean allowPrimary;

		/**
		 * Required - API name: {@code index}
		 */
		public final Builder index(String value) {
			this.index = value;
			return this;
		}

		/**
		 * Required - API name: {@code shard}
		 */
		public final Builder shard(int value) {
			this.shard = value;
			return this;
		}

		/**
		 * Required - API name: {@code node}
		 */
		public final Builder node(String value) {
			this.node = value;
			return this;
		}

		/**
		 * API name: {@code allow_primary}
		 */
		public final Builder allowPrimary(@Nullable Boolean value) {
			this.allowPrimary = value;
			return this;
		}

		/**
		 * Builds a {@link CommandCancelAction}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public CommandCancelAction build() {
			_checkSingleUse();

			return new CommandCancelAction(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link CommandCancelAction}
	 */
	public static final JsonpDeserializer<CommandCancelAction> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, CommandCancelAction::setupCommandCancelActionDeserializer);

	protected static void setupCommandCancelActionDeserializer(ObjectDeserializer<CommandCancelAction.Builder> op) {

		op.add(Builder::index, JsonpDeserializer.stringDeserializer(), "index");
		op.add(Builder::shard, JsonpDeserializer.integerDeserializer(), "shard");
		op.add(Builder::node, JsonpDeserializer.stringDeserializer(), "node");
		op.add(Builder::allowPrimary, JsonpDeserializer.booleanDeserializer(), "allow_primary");

	}

}
