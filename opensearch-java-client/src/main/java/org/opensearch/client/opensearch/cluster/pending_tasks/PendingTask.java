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

package org.opensearch.client.opensearch.cluster.pending_tasks;

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

// typedef: cluster.pending_tasks.PendingTask

@JsonpDeserializable
public class PendingTask implements JsonpSerializable {
	private final boolean executing;

	private final int insertOrder;

	private final String priority;

	private final String source;

	private final String timeInQueue;

	private final int timeInQueueMillis;

	// ---------------------------------------------------------------------------------------------

	private PendingTask(Builder builder) {

		this.executing = ApiTypeHelper.requireNonNull(builder.executing, this, "executing");
		this.insertOrder = ApiTypeHelper.requireNonNull(builder.insertOrder, this, "insertOrder");
		this.priority = ApiTypeHelper.requireNonNull(builder.priority, this, "priority");
		this.source = ApiTypeHelper.requireNonNull(builder.source, this, "source");
		this.timeInQueue = ApiTypeHelper.requireNonNull(builder.timeInQueue, this, "timeInQueue");
		this.timeInQueueMillis = ApiTypeHelper.requireNonNull(builder.timeInQueueMillis, this, "timeInQueueMillis");

	}

	public static PendingTask of(Function<Builder, ObjectBuilder<PendingTask>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code executing}
	 */
	public final boolean executing() {
		return this.executing;
	}

	/**
	 * Required - API name: {@code insert_order}
	 */
	public final int insertOrder() {
		return this.insertOrder;
	}

	/**
	 * Required - API name: {@code priority}
	 */
	public final String priority() {
		return this.priority;
	}

	/**
	 * Required - API name: {@code source}
	 */
	public final String source() {
		return this.source;
	}

	/**
	 * Required - API name: {@code time_in_queue}
	 */
	public final String timeInQueue() {
		return this.timeInQueue;
	}

	/**
	 * Required - API name: {@code time_in_queue_millis}
	 */
	public final int timeInQueueMillis() {
		return this.timeInQueueMillis;
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

		generator.writeKey("executing");
		generator.write(this.executing);

		generator.writeKey("insert_order");
		generator.write(this.insertOrder);

		generator.writeKey("priority");
		generator.write(this.priority);

		generator.writeKey("source");
		generator.write(this.source);

		generator.writeKey("time_in_queue");
		generator.write(this.timeInQueue);

		generator.writeKey("time_in_queue_millis");
		generator.write(this.timeInQueueMillis);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link PendingTask}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<PendingTask> {
		private Boolean executing;

		private Integer insertOrder;

		private String priority;

		private String source;

		private String timeInQueue;

		private Integer timeInQueueMillis;

		/**
		 * Required - API name: {@code executing}
		 */
		public final Builder executing(boolean value) {
			this.executing = value;
			return this;
		}

		/**
		 * Required - API name: {@code insert_order}
		 */
		public final Builder insertOrder(int value) {
			this.insertOrder = value;
			return this;
		}

		/**
		 * Required - API name: {@code priority}
		 */
		public final Builder priority(String value) {
			this.priority = value;
			return this;
		}

		/**
		 * Required - API name: {@code source}
		 */
		public final Builder source(String value) {
			this.source = value;
			return this;
		}

		/**
		 * Required - API name: {@code time_in_queue}
		 */
		public final Builder timeInQueue(String value) {
			this.timeInQueue = value;
			return this;
		}

		/**
		 * Required - API name: {@code time_in_queue_millis}
		 */
		public final Builder timeInQueueMillis(int value) {
			this.timeInQueueMillis = value;
			return this;
		}

		/**
		 * Builds a {@link PendingTask}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public PendingTask build() {
			_checkSingleUse();

			return new PendingTask(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link PendingTask}
	 */
	public static final JsonpDeserializer<PendingTask> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			PendingTask::setupPendingTaskDeserializer);

	protected static void setupPendingTaskDeserializer(ObjectDeserializer<PendingTask.Builder> op) {

		op.add(Builder::executing, JsonpDeserializer.booleanDeserializer(), "executing");
		op.add(Builder::insertOrder, JsonpDeserializer.integerDeserializer(), "insert_order");
		op.add(Builder::priority, JsonpDeserializer.stringDeserializer(), "priority");
		op.add(Builder::source, JsonpDeserializer.stringDeserializer(), "source");
		op.add(Builder::timeInQueue, JsonpDeserializer.stringDeserializer(), "time_in_queue");
		op.add(Builder::timeInQueueMillis, JsonpDeserializer.integerDeserializer(), "time_in_queue_millis");

	}

}
