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

package org.opensearch.client.opensearch.cluster;

import org.opensearch.client.opensearch.cluster.allocation_explain.AllocationDecision;
import org.opensearch.client.opensearch.cluster.allocation_explain.ClusterInfo;
import org.opensearch.client.opensearch.cluster.allocation_explain.CurrentNode;
import org.opensearch.client.opensearch.cluster.allocation_explain.Decision;
import org.opensearch.client.opensearch.cluster.allocation_explain.NodeAllocationExplanation;
import org.opensearch.client.opensearch.cluster.allocation_explain.UnassignedInformation;
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

import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: cluster.allocation_explain.Response

@JsonpDeserializable
public class AllocationExplainResponse implements JsonpSerializable {
	@Nullable
	private final String allocateExplanation;

	@Nullable
	private final String allocationDelay;

	@Nullable
	private final Long allocationDelayInMillis;

	@Nullable
	private final Decision canAllocate;

	@Nullable
	private final Decision canMoveToOtherNode;

	@Nullable
	private final Decision canRebalanceCluster;

	private final List<AllocationDecision> canRebalanceClusterDecisions;

	@Nullable
	private final Decision canRebalanceToOtherNode;

	private final List<AllocationDecision> canRemainDecisions;

	@Nullable
	private final Decision canRemainOnCurrentNode;

	@Nullable
	private final ClusterInfo clusterInfo;

	@Nullable
	private final String configuredDelay;

	@Nullable
	private final Long configuredDelayInMillis;

	@Nullable
	private final CurrentNode currentNode;

	private final String currentState;

	private final String index;

	@Nullable
	private final String moveExplanation;

	private final List<NodeAllocationExplanation> nodeAllocationDecisions;

	private final boolean primary;

	@Nullable
	private final String rebalanceExplanation;

	@Nullable
	private final String remainingDelay;

	@Nullable
	private final Long remainingDelayInMillis;

	private final int shard;

	@Nullable
	private final UnassignedInformation unassignedInfo;

	@Nullable
	private final String note;

	// ---------------------------------------------------------------------------------------------

	private AllocationExplainResponse(Builder builder) {

		this.allocateExplanation = builder.allocateExplanation;
		this.allocationDelay = builder.allocationDelay;
		this.allocationDelayInMillis = builder.allocationDelayInMillis;
		this.canAllocate = builder.canAllocate;
		this.canMoveToOtherNode = builder.canMoveToOtherNode;
		this.canRebalanceCluster = builder.canRebalanceCluster;
		this.canRebalanceClusterDecisions = ApiTypeHelper.unmodifiable(builder.canRebalanceClusterDecisions);
		this.canRebalanceToOtherNode = builder.canRebalanceToOtherNode;
		this.canRemainDecisions = ApiTypeHelper.unmodifiable(builder.canRemainDecisions);
		this.canRemainOnCurrentNode = builder.canRemainOnCurrentNode;
		this.clusterInfo = builder.clusterInfo;
		this.configuredDelay = builder.configuredDelay;
		this.configuredDelayInMillis = builder.configuredDelayInMillis;
		this.currentNode = builder.currentNode;
		this.currentState = ApiTypeHelper.requireNonNull(builder.currentState, this, "currentState");
		this.index = ApiTypeHelper.requireNonNull(builder.index, this, "index");
		this.moveExplanation = builder.moveExplanation;
		this.nodeAllocationDecisions = ApiTypeHelper.unmodifiable(builder.nodeAllocationDecisions);
		this.primary = ApiTypeHelper.requireNonNull(builder.primary, this, "primary");
		this.rebalanceExplanation = builder.rebalanceExplanation;
		this.remainingDelay = builder.remainingDelay;
		this.remainingDelayInMillis = builder.remainingDelayInMillis;
		this.shard = ApiTypeHelper.requireNonNull(builder.shard, this, "shard");
		this.unassignedInfo = builder.unassignedInfo;
		this.note = builder.note;

	}

	public static AllocationExplainResponse of(Function<Builder, ObjectBuilder<AllocationExplainResponse>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code allocate_explanation}
	 */
	@Nullable
	public final String allocateExplanation() {
		return this.allocateExplanation;
	}

	/**
	 * API name: {@code allocation_delay}
	 */
	@Nullable
	public final String allocationDelay() {
		return this.allocationDelay;
	}

	/**
	 * API name: {@code allocation_delay_in_millis}
	 */
	@Nullable
	public final Long allocationDelayInMillis() {
		return this.allocationDelayInMillis;
	}

	/**
	 * API name: {@code can_allocate}
	 */
	@Nullable
	public final Decision canAllocate() {
		return this.canAllocate;
	}

	/**
	 * API name: {@code can_move_to_other_node}
	 */
	@Nullable
	public final Decision canMoveToOtherNode() {
		return this.canMoveToOtherNode;
	}

	/**
	 * API name: {@code can_rebalance_cluster}
	 */
	@Nullable
	public final Decision canRebalanceCluster() {
		return this.canRebalanceCluster;
	}

	/**
	 * API name: {@code can_rebalance_cluster_decisions}
	 */
	public final List<AllocationDecision> canRebalanceClusterDecisions() {
		return this.canRebalanceClusterDecisions;
	}

	/**
	 * API name: {@code can_rebalance_to_other_node}
	 */
	@Nullable
	public final Decision canRebalanceToOtherNode() {
		return this.canRebalanceToOtherNode;
	}

	/**
	 * API name: {@code can_remain_decisions}
	 */
	public final List<AllocationDecision> canRemainDecisions() {
		return this.canRemainDecisions;
	}

	/**
	 * API name: {@code can_remain_on_current_node}
	 */
	@Nullable
	public final Decision canRemainOnCurrentNode() {
		return this.canRemainOnCurrentNode;
	}

	/**
	 * API name: {@code cluster_info}
	 */
	@Nullable
	public final ClusterInfo clusterInfo() {
		return this.clusterInfo;
	}

	/**
	 * API name: {@code configured_delay}
	 */
	@Nullable
	public final String configuredDelay() {
		return this.configuredDelay;
	}

	/**
	 * API name: {@code configured_delay_in_millis}
	 */
	@Nullable
	public final Long configuredDelayInMillis() {
		return this.configuredDelayInMillis;
	}

	/**
	 * API name: {@code current_node}
	 */
	@Nullable
	public final CurrentNode currentNode() {
		return this.currentNode;
	}

	/**
	 * Required - API name: {@code current_state}
	 */
	public final String currentState() {
		return this.currentState;
	}

	/**
	 * Required - API name: {@code index}
	 */
	public final String index() {
		return this.index;
	}

	/**
	 * API name: {@code move_explanation}
	 */
	@Nullable
	public final String moveExplanation() {
		return this.moveExplanation;
	}

	/**
	 * API name: {@code node_allocation_decisions}
	 */
	public final List<NodeAllocationExplanation> nodeAllocationDecisions() {
		return this.nodeAllocationDecisions;
	}

	/**
	 * Required - API name: {@code primary}
	 */
	public final boolean primary() {
		return this.primary;
	}

	/**
	 * API name: {@code rebalance_explanation}
	 */
	@Nullable
	public final String rebalanceExplanation() {
		return this.rebalanceExplanation;
	}

	/**
	 * API name: {@code remaining_delay}
	 */
	@Nullable
	public final String remainingDelay() {
		return this.remainingDelay;
	}

	/**
	 * API name: {@code remaining_delay_in_millis}
	 */
	@Nullable
	public final Long remainingDelayInMillis() {
		return this.remainingDelayInMillis;
	}

	/**
	 * Required - API name: {@code shard}
	 */
	public final int shard() {
		return this.shard;
	}

	/**
	 * API name: {@code unassigned_info}
	 */
	@Nullable
	public final UnassignedInformation unassignedInfo() {
		return this.unassignedInfo;
	}

	/**
	 * API name: {@code note}
	 */
	@Nullable
	public final String note() {
		return this.note;
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

		if (this.allocateExplanation != null) {
			generator.writeKey("allocate_explanation");
			generator.write(this.allocateExplanation);

		}
		if (this.allocationDelay != null) {
			generator.writeKey("allocation_delay");
			generator.write(this.allocationDelay);

		}
		if (this.allocationDelayInMillis != null) {
			generator.writeKey("allocation_delay_in_millis");
			generator.write(this.allocationDelayInMillis);

		}
		if (this.canAllocate != null) {
			generator.writeKey("can_allocate");
			this.canAllocate.serialize(generator, mapper);
		}
		if (this.canMoveToOtherNode != null) {
			generator.writeKey("can_move_to_other_node");
			this.canMoveToOtherNode.serialize(generator, mapper);
		}
		if (this.canRebalanceCluster != null) {
			generator.writeKey("can_rebalance_cluster");
			this.canRebalanceCluster.serialize(generator, mapper);
		}
		if (ApiTypeHelper.isDefined(this.canRebalanceClusterDecisions)) {
			generator.writeKey("can_rebalance_cluster_decisions");
			generator.writeStartArray();
			for (AllocationDecision item0 : this.canRebalanceClusterDecisions) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (this.canRebalanceToOtherNode != null) {
			generator.writeKey("can_rebalance_to_other_node");
			this.canRebalanceToOtherNode.serialize(generator, mapper);
		}
		if (ApiTypeHelper.isDefined(this.canRemainDecisions)) {
			generator.writeKey("can_remain_decisions");
			generator.writeStartArray();
			for (AllocationDecision item0 : this.canRemainDecisions) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (this.canRemainOnCurrentNode != null) {
			generator.writeKey("can_remain_on_current_node");
			this.canRemainOnCurrentNode.serialize(generator, mapper);
		}
		if (this.clusterInfo != null) {
			generator.writeKey("cluster_info");
			this.clusterInfo.serialize(generator, mapper);

		}
		if (this.configuredDelay != null) {
			generator.writeKey("configured_delay");
			generator.write(this.configuredDelay);

		}
		if (this.configuredDelayInMillis != null) {
			generator.writeKey("configured_delay_in_millis");
			generator.write(this.configuredDelayInMillis);

		}
		if (this.currentNode != null) {
			generator.writeKey("current_node");
			this.currentNode.serialize(generator, mapper);

		}
		generator.writeKey("current_state");
		generator.write(this.currentState);

		generator.writeKey("index");
		generator.write(this.index);

		if (this.moveExplanation != null) {
			generator.writeKey("move_explanation");
			generator.write(this.moveExplanation);

		}
		if (ApiTypeHelper.isDefined(this.nodeAllocationDecisions)) {
			generator.writeKey("node_allocation_decisions");
			generator.writeStartArray();
			for (NodeAllocationExplanation item0 : this.nodeAllocationDecisions) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		generator.writeKey("primary");
		generator.write(this.primary);

		if (this.rebalanceExplanation != null) {
			generator.writeKey("rebalance_explanation");
			generator.write(this.rebalanceExplanation);

		}
		if (this.remainingDelay != null) {
			generator.writeKey("remaining_delay");
			generator.write(this.remainingDelay);

		}
		if (this.remainingDelayInMillis != null) {
			generator.writeKey("remaining_delay_in_millis");
			generator.write(this.remainingDelayInMillis);

		}
		generator.writeKey("shard");
		generator.write(this.shard);

		if (this.unassignedInfo != null) {
			generator.writeKey("unassigned_info");
			this.unassignedInfo.serialize(generator, mapper);

		}
		if (this.note != null) {
			generator.writeKey("note");
			generator.write(this.note);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link AllocationExplainResponse}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<AllocationExplainResponse> {
		@Nullable
		private String allocateExplanation;

		@Nullable
		private String allocationDelay;

		@Nullable
		private Long allocationDelayInMillis;

		@Nullable
		private Decision canAllocate;

		@Nullable
		private Decision canMoveToOtherNode;

		@Nullable
		private Decision canRebalanceCluster;

		@Nullable
		private List<AllocationDecision> canRebalanceClusterDecisions;

		@Nullable
		private Decision canRebalanceToOtherNode;

		@Nullable
		private List<AllocationDecision> canRemainDecisions;

		@Nullable
		private Decision canRemainOnCurrentNode;

		@Nullable
		private ClusterInfo clusterInfo;

		@Nullable
		private String configuredDelay;

		@Nullable
		private Long configuredDelayInMillis;

		@Nullable
		private CurrentNode currentNode;

		private String currentState;

		private String index;

		@Nullable
		private String moveExplanation;

		@Nullable
		private List<NodeAllocationExplanation> nodeAllocationDecisions;

		private Boolean primary;

		@Nullable
		private String rebalanceExplanation;

		@Nullable
		private String remainingDelay;

		@Nullable
		private Long remainingDelayInMillis;

		private Integer shard;

		@Nullable
		private UnassignedInformation unassignedInfo;

		@Nullable
		private String note;

		/**
		 * API name: {@code allocate_explanation}
		 */
		public final Builder allocateExplanation(@Nullable String value) {
			this.allocateExplanation = value;
			return this;
		}

		/**
		 * API name: {@code allocation_delay}
		 */
		public final Builder allocationDelay(@Nullable String value) {
			this.allocationDelay = value;
			return this;
		}

		/**
		 * API name: {@code allocation_delay_in_millis}
		 */
		public final Builder allocationDelayInMillis(@Nullable Long value) {
			this.allocationDelayInMillis = value;
			return this;
		}

		/**
		 * API name: {@code can_allocate}
		 */
		public final Builder canAllocate(@Nullable Decision value) {
			this.canAllocate = value;
			return this;
		}

		/**
		 * API name: {@code can_move_to_other_node}
		 */
		public final Builder canMoveToOtherNode(@Nullable Decision value) {
			this.canMoveToOtherNode = value;
			return this;
		}

		/**
		 * API name: {@code can_rebalance_cluster}
		 */
		public final Builder canRebalanceCluster(@Nullable Decision value) {
			this.canRebalanceCluster = value;
			return this;
		}

		/**
		 * API name: {@code can_rebalance_cluster_decisions}
		 * <p>
		 * Adds all elements of <code>list</code> to
		 * <code>canRebalanceClusterDecisions</code>.
		 */
		public final Builder canRebalanceClusterDecisions(List<AllocationDecision> list) {
			this.canRebalanceClusterDecisions = _listAddAll(this.canRebalanceClusterDecisions, list);
			return this;
		}

		/**
		 * API name: {@code can_rebalance_cluster_decisions}
		 * <p>
		 * Adds one or more values to <code>canRebalanceClusterDecisions</code>.
		 */
		public final Builder canRebalanceClusterDecisions(AllocationDecision value, AllocationDecision... values) {
			this.canRebalanceClusterDecisions = _listAdd(this.canRebalanceClusterDecisions, value, values);
			return this;
		}

		/**
		 * API name: {@code can_rebalance_cluster_decisions}
		 * <p>
		 * Adds a value to <code>canRebalanceClusterDecisions</code> using a builder
		 * lambda.
		 */
		public final Builder canRebalanceClusterDecisions(
				Function<AllocationDecision.Builder, ObjectBuilder<AllocationDecision>> fn) {
			return canRebalanceClusterDecisions(fn.apply(new AllocationDecision.Builder()).build());
		}

		/**
		 * API name: {@code can_rebalance_to_other_node}
		 */
		public final Builder canRebalanceToOtherNode(@Nullable Decision value) {
			this.canRebalanceToOtherNode = value;
			return this;
		}

		/**
		 * API name: {@code can_remain_decisions}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>canRemainDecisions</code>.
		 */
		public final Builder canRemainDecisions(List<AllocationDecision> list) {
			this.canRemainDecisions = _listAddAll(this.canRemainDecisions, list);
			return this;
		}

		/**
		 * API name: {@code can_remain_decisions}
		 * <p>
		 * Adds one or more values to <code>canRemainDecisions</code>.
		 */
		public final Builder canRemainDecisions(AllocationDecision value, AllocationDecision... values) {
			this.canRemainDecisions = _listAdd(this.canRemainDecisions, value, values);
			return this;
		}

		/**
		 * API name: {@code can_remain_decisions}
		 * <p>
		 * Adds a value to <code>canRemainDecisions</code> using a builder lambda.
		 */
		public final Builder canRemainDecisions(
				Function<AllocationDecision.Builder, ObjectBuilder<AllocationDecision>> fn) {
			return canRemainDecisions(fn.apply(new AllocationDecision.Builder()).build());
		}

		/**
		 * API name: {@code can_remain_on_current_node}
		 */
		public final Builder canRemainOnCurrentNode(@Nullable Decision value) {
			this.canRemainOnCurrentNode = value;
			return this;
		}

		/**
		 * API name: {@code cluster_info}
		 */
		public final Builder clusterInfo(@Nullable ClusterInfo value) {
			this.clusterInfo = value;
			return this;
		}

		/**
		 * API name: {@code cluster_info}
		 */
		public final Builder clusterInfo(Function<ClusterInfo.Builder, ObjectBuilder<ClusterInfo>> fn) {
			return this.clusterInfo(fn.apply(new ClusterInfo.Builder()).build());
		}

		/**
		 * API name: {@code configured_delay}
		 */
		public final Builder configuredDelay(@Nullable String value) {
			this.configuredDelay = value;
			return this;
		}

		/**
		 * API name: {@code configured_delay_in_millis}
		 */
		public final Builder configuredDelayInMillis(@Nullable Long value) {
			this.configuredDelayInMillis = value;
			return this;
		}

		/**
		 * API name: {@code current_node}
		 */
		public final Builder currentNode(@Nullable CurrentNode value) {
			this.currentNode = value;
			return this;
		}

		/**
		 * API name: {@code current_node}
		 */
		public final Builder currentNode(Function<CurrentNode.Builder, ObjectBuilder<CurrentNode>> fn) {
			return this.currentNode(fn.apply(new CurrentNode.Builder()).build());
		}

		/**
		 * Required - API name: {@code current_state}
		 */
		public final Builder currentState(String value) {
			this.currentState = value;
			return this;
		}

		/**
		 * Required - API name: {@code index}
		 */
		public final Builder index(String value) {
			this.index = value;
			return this;
		}

		/**
		 * API name: {@code move_explanation}
		 */
		public final Builder moveExplanation(@Nullable String value) {
			this.moveExplanation = value;
			return this;
		}

		/**
		 * API name: {@code node_allocation_decisions}
		 * <p>
		 * Adds all elements of <code>list</code> to
		 * <code>nodeAllocationDecisions</code>.
		 */
		public final Builder nodeAllocationDecisions(List<NodeAllocationExplanation> list) {
			this.nodeAllocationDecisions = _listAddAll(this.nodeAllocationDecisions, list);
			return this;
		}

		/**
		 * API name: {@code node_allocation_decisions}
		 * <p>
		 * Adds one or more values to <code>nodeAllocationDecisions</code>.
		 */
		public final Builder nodeAllocationDecisions(NodeAllocationExplanation value,
				NodeAllocationExplanation... values) {
			this.nodeAllocationDecisions = _listAdd(this.nodeAllocationDecisions, value, values);
			return this;
		}

		/**
		 * API name: {@code node_allocation_decisions}
		 * <p>
		 * Adds a value to <code>nodeAllocationDecisions</code> using a builder lambda.
		 */
		public final Builder nodeAllocationDecisions(
				Function<NodeAllocationExplanation.Builder, ObjectBuilder<NodeAllocationExplanation>> fn) {
			return nodeAllocationDecisions(fn.apply(new NodeAllocationExplanation.Builder()).build());
		}

		/**
		 * Required - API name: {@code primary}
		 */
		public final Builder primary(boolean value) {
			this.primary = value;
			return this;
		}

		/**
		 * API name: {@code rebalance_explanation}
		 */
		public final Builder rebalanceExplanation(@Nullable String value) {
			this.rebalanceExplanation = value;
			return this;
		}

		/**
		 * API name: {@code remaining_delay}
		 */
		public final Builder remainingDelay(@Nullable String value) {
			this.remainingDelay = value;
			return this;
		}

		/**
		 * API name: {@code remaining_delay_in_millis}
		 */
		public final Builder remainingDelayInMillis(@Nullable Long value) {
			this.remainingDelayInMillis = value;
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
		 * API name: {@code unassigned_info}
		 */
		public final Builder unassignedInfo(@Nullable UnassignedInformation value) {
			this.unassignedInfo = value;
			return this;
		}

		/**
		 * API name: {@code unassigned_info}
		 */
		public final Builder unassignedInfo(
				Function<UnassignedInformation.Builder, ObjectBuilder<UnassignedInformation>> fn) {
			return this.unassignedInfo(fn.apply(new UnassignedInformation.Builder()).build());
		}

		/**
		 * API name: {@code note}
		 */
		public final Builder note(@Nullable String value) {
			this.note = value;
			return this;
		}

		/**
		 * Builds a {@link AllocationExplainResponse}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public AllocationExplainResponse build() {
			_checkSingleUse();

			return new AllocationExplainResponse(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link AllocationExplainResponse}
	 */
	public static final JsonpDeserializer<AllocationExplainResponse> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, AllocationExplainResponse::setupAllocationExplainResponseDeserializer);

	protected static void setupAllocationExplainResponseDeserializer(
			ObjectDeserializer<AllocationExplainResponse.Builder> op) {

		op.add(Builder::allocateExplanation, JsonpDeserializer.stringDeserializer(), "allocate_explanation");
		op.add(Builder::allocationDelay, JsonpDeserializer.stringDeserializer(), "allocation_delay");
		op.add(Builder::allocationDelayInMillis, JsonpDeserializer.longDeserializer(), "allocation_delay_in_millis");
		op.add(Builder::canAllocate, Decision._DESERIALIZER, "can_allocate");
		op.add(Builder::canMoveToOtherNode, Decision._DESERIALIZER, "can_move_to_other_node");
		op.add(Builder::canRebalanceCluster, Decision._DESERIALIZER, "can_rebalance_cluster");
		op.add(Builder::canRebalanceClusterDecisions,
				JsonpDeserializer.arrayDeserializer(AllocationDecision._DESERIALIZER),
				"can_rebalance_cluster_decisions");
		op.add(Builder::canRebalanceToOtherNode, Decision._DESERIALIZER, "can_rebalance_to_other_node");
		op.add(Builder::canRemainDecisions, JsonpDeserializer.arrayDeserializer(AllocationDecision._DESERIALIZER),
				"can_remain_decisions");
		op.add(Builder::canRemainOnCurrentNode, Decision._DESERIALIZER, "can_remain_on_current_node");
		op.add(Builder::clusterInfo, ClusterInfo._DESERIALIZER, "cluster_info");
		op.add(Builder::configuredDelay, JsonpDeserializer.stringDeserializer(), "configured_delay");
		op.add(Builder::configuredDelayInMillis, JsonpDeserializer.longDeserializer(), "configured_delay_in_millis");
		op.add(Builder::currentNode, CurrentNode._DESERIALIZER, "current_node");
		op.add(Builder::currentState, JsonpDeserializer.stringDeserializer(), "current_state");
		op.add(Builder::index, JsonpDeserializer.stringDeserializer(), "index");
		op.add(Builder::moveExplanation, JsonpDeserializer.stringDeserializer(), "move_explanation");
		op.add(Builder::nodeAllocationDecisions,
				JsonpDeserializer.arrayDeserializer(NodeAllocationExplanation._DESERIALIZER),
				"node_allocation_decisions");
		op.add(Builder::primary, JsonpDeserializer.booleanDeserializer(), "primary");
		op.add(Builder::rebalanceExplanation, JsonpDeserializer.stringDeserializer(), "rebalance_explanation");
		op.add(Builder::remainingDelay, JsonpDeserializer.stringDeserializer(), "remaining_delay");
		op.add(Builder::remainingDelayInMillis, JsonpDeserializer.longDeserializer(), "remaining_delay_in_millis");
		op.add(Builder::shard, JsonpDeserializer.integerDeserializer(), "shard");
		op.add(Builder::unassignedInfo, UnassignedInformation._DESERIALIZER, "unassigned_info");
		op.add(Builder::note, JsonpDeserializer.stringDeserializer(), "note");

	}

}
