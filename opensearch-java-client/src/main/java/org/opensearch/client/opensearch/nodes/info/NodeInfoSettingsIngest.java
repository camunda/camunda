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

package org.opensearch.client.opensearch.nodes.info;

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;

import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: nodes.info.NodeInfoSettingsIngest

@JsonpDeserializable
public class NodeInfoSettingsIngest implements JsonpSerializable {
	@Nullable
	private final NodeInfoIngestInfo attachment;

	@Nullable
	private final NodeInfoIngestInfo append;

	@Nullable
	private final NodeInfoIngestInfo csv;

	@Nullable
	private final NodeInfoIngestInfo convert;

	@Nullable
	private final NodeInfoIngestInfo date;

	@Nullable
	private final NodeInfoIngestInfo dateIndexName;

	@Nullable
	private final NodeInfoIngestInfo dotExpander;

	@Nullable
	private final NodeInfoIngestInfo fail;

	@Nullable
	private final NodeInfoIngestInfo foreach;

	@Nullable
	private final NodeInfoIngestInfo json;

	@Nullable
	private final NodeInfoIngestInfo userAgent;

	@Nullable
	private final NodeInfoIngestInfo kv;

	@Nullable
	private final NodeInfoIngestInfo geoip;

	@Nullable
	private final NodeInfoIngestInfo grok;

	@Nullable
	private final NodeInfoIngestInfo gsub;

	@Nullable
	private final NodeInfoIngestInfo join;

	@Nullable
	private final NodeInfoIngestInfo lowercase;

	@Nullable
	private final NodeInfoIngestInfo remove;

	@Nullable
	private final NodeInfoIngestInfo rename;

	@Nullable
	private final NodeInfoIngestInfo script;

	@Nullable
	private final NodeInfoIngestInfo set;

	@Nullable
	private final NodeInfoIngestInfo sort;

	@Nullable
	private final NodeInfoIngestInfo split;

	@Nullable
	private final NodeInfoIngestInfo trim;

	@Nullable
	private final NodeInfoIngestInfo uppercase;

	@Nullable
	private final NodeInfoIngestInfo urldecode;

	@Nullable
	private final NodeInfoIngestInfo bytes;

	@Nullable
	private final NodeInfoIngestInfo dissect;

	@Nullable
	private final NodeInfoIngestInfo setSecurityUser;

	@Nullable
	private final NodeInfoIngestInfo pipeline;

	@Nullable
	private final NodeInfoIngestInfo drop;

	@Nullable
	private final NodeInfoIngestInfo circle;

	@Nullable
	private final NodeInfoIngestInfo inference;

	// ---------------------------------------------------------------------------------------------

	private NodeInfoSettingsIngest(Builder builder) {

		this.attachment = builder.attachment;
		this.append = builder.append;
		this.csv = builder.csv;
		this.convert = builder.convert;
		this.date = builder.date;
		this.dateIndexName = builder.dateIndexName;
		this.dotExpander = builder.dotExpander;
		this.fail = builder.fail;
		this.foreach = builder.foreach;
		this.json = builder.json;
		this.userAgent = builder.userAgent;
		this.kv = builder.kv;
		this.geoip = builder.geoip;
		this.grok = builder.grok;
		this.gsub = builder.gsub;
		this.join = builder.join;
		this.lowercase = builder.lowercase;
		this.remove = builder.remove;
		this.rename = builder.rename;
		this.script = builder.script;
		this.set = builder.set;
		this.sort = builder.sort;
		this.split = builder.split;
		this.trim = builder.trim;
		this.uppercase = builder.uppercase;
		this.urldecode = builder.urldecode;
		this.bytes = builder.bytes;
		this.dissect = builder.dissect;
		this.setSecurityUser = builder.setSecurityUser;
		this.pipeline = builder.pipeline;
		this.drop = builder.drop;
		this.circle = builder.circle;
		this.inference = builder.inference;

	}

	public static NodeInfoSettingsIngest of(Function<Builder, ObjectBuilder<NodeInfoSettingsIngest>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code attachment}
	 */
	@Nullable
	public final NodeInfoIngestInfo attachment() {
		return this.attachment;
	}

	/**
	 * API name: {@code append}
	 */
	@Nullable
	public final NodeInfoIngestInfo append() {
		return this.append;
	}

	/**
	 * API name: {@code csv}
	 */
	@Nullable
	public final NodeInfoIngestInfo csv() {
		return this.csv;
	}

	/**
	 * API name: {@code convert}
	 */
	@Nullable
	public final NodeInfoIngestInfo convert() {
		return this.convert;
	}

	/**
	 * API name: {@code date}
	 */
	@Nullable
	public final NodeInfoIngestInfo date() {
		return this.date;
	}

	/**
	 * API name: {@code date_index_name}
	 */
	@Nullable
	public final NodeInfoIngestInfo dateIndexName() {
		return this.dateIndexName;
	}

	/**
	 * API name: {@code dot_expander}
	 */
	@Nullable
	public final NodeInfoIngestInfo dotExpander() {
		return this.dotExpander;
	}

	/**
	 * API name: {@code fail}
	 */
	@Nullable
	public final NodeInfoIngestInfo fail() {
		return this.fail;
	}

	/**
	 * API name: {@code foreach}
	 */
	@Nullable
	public final NodeInfoIngestInfo foreach() {
		return this.foreach;
	}

	/**
	 * API name: {@code json}
	 */
	@Nullable
	public final NodeInfoIngestInfo json() {
		return this.json;
	}

	/**
	 * API name: {@code user_agent}
	 */
	@Nullable
	public final NodeInfoIngestInfo userAgent() {
		return this.userAgent;
	}

	/**
	 * API name: {@code kv}
	 */
	@Nullable
	public final NodeInfoIngestInfo kv() {
		return this.kv;
	}

	/**
	 * API name: {@code geoip}
	 */
	@Nullable
	public final NodeInfoIngestInfo geoip() {
		return this.geoip;
	}

	/**
	 * API name: {@code grok}
	 */
	@Nullable
	public final NodeInfoIngestInfo grok() {
		return this.grok;
	}

	/**
	 * API name: {@code gsub}
	 */
	@Nullable
	public final NodeInfoIngestInfo gsub() {
		return this.gsub;
	}

	/**
	 * API name: {@code join}
	 */
	@Nullable
	public final NodeInfoIngestInfo join() {
		return this.join;
	}

	/**
	 * API name: {@code lowercase}
	 */
	@Nullable
	public final NodeInfoIngestInfo lowercase() {
		return this.lowercase;
	}

	/**
	 * API name: {@code remove}
	 */
	@Nullable
	public final NodeInfoIngestInfo remove() {
		return this.remove;
	}

	/**
	 * API name: {@code rename}
	 */
	@Nullable
	public final NodeInfoIngestInfo rename() {
		return this.rename;
	}

	/**
	 * API name: {@code script}
	 */
	@Nullable
	public final NodeInfoIngestInfo script() {
		return this.script;
	}

	/**
	 * API name: {@code set}
	 */
	@Nullable
	public final NodeInfoIngestInfo set() {
		return this.set;
	}

	/**
	 * API name: {@code sort}
	 */
	@Nullable
	public final NodeInfoIngestInfo sort() {
		return this.sort;
	}

	/**
	 * API name: {@code split}
	 */
	@Nullable
	public final NodeInfoIngestInfo split() {
		return this.split;
	}

	/**
	 * API name: {@code trim}
	 */
	@Nullable
	public final NodeInfoIngestInfo trim() {
		return this.trim;
	}

	/**
	 * API name: {@code uppercase}
	 */
	@Nullable
	public final NodeInfoIngestInfo uppercase() {
		return this.uppercase;
	}

	/**
	 * API name: {@code urldecode}
	 */
	@Nullable
	public final NodeInfoIngestInfo urldecode() {
		return this.urldecode;
	}

	/**
	 * API name: {@code bytes}
	 */
	@Nullable
	public final NodeInfoIngestInfo bytes() {
		return this.bytes;
	}

	/**
	 * API name: {@code dissect}
	 */
	@Nullable
	public final NodeInfoIngestInfo dissect() {
		return this.dissect;
	}

	/**
	 * API name: {@code set_security_user}
	 */
	@Nullable
	public final NodeInfoIngestInfo setSecurityUser() {
		return this.setSecurityUser;
	}

	/**
	 * API name: {@code pipeline}
	 */
	@Nullable
	public final NodeInfoIngestInfo pipeline() {
		return this.pipeline;
	}

	/**
	 * API name: {@code drop}
	 */
	@Nullable
	public final NodeInfoIngestInfo drop() {
		return this.drop;
	}

	/**
	 * API name: {@code circle}
	 */
	@Nullable
	public final NodeInfoIngestInfo circle() {
		return this.circle;
	}

	/**
	 * API name: {@code inference}
	 */
	@Nullable
	public final NodeInfoIngestInfo inference() {
		return this.inference;
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

		if (this.attachment != null) {
			generator.writeKey("attachment");
			this.attachment.serialize(generator, mapper);

		}
		if (this.append != null) {
			generator.writeKey("append");
			this.append.serialize(generator, mapper);

		}
		if (this.csv != null) {
			generator.writeKey("csv");
			this.csv.serialize(generator, mapper);

		}
		if (this.convert != null) {
			generator.writeKey("convert");
			this.convert.serialize(generator, mapper);

		}
		if (this.date != null) {
			generator.writeKey("date");
			this.date.serialize(generator, mapper);

		}
		if (this.dateIndexName != null) {
			generator.writeKey("date_index_name");
			this.dateIndexName.serialize(generator, mapper);

		}
		if (this.dotExpander != null) {
			generator.writeKey("dot_expander");
			this.dotExpander.serialize(generator, mapper);

		}
		if (this.fail != null) {
			generator.writeKey("fail");
			this.fail.serialize(generator, mapper);

		}
		if (this.foreach != null) {
			generator.writeKey("foreach");
			this.foreach.serialize(generator, mapper);

		}
		if (this.json != null) {
			generator.writeKey("json");
			this.json.serialize(generator, mapper);

		}
		if (this.userAgent != null) {
			generator.writeKey("user_agent");
			this.userAgent.serialize(generator, mapper);

		}
		if (this.kv != null) {
			generator.writeKey("kv");
			this.kv.serialize(generator, mapper);

		}
		if (this.geoip != null) {
			generator.writeKey("geoip");
			this.geoip.serialize(generator, mapper);

		}
		if (this.grok != null) {
			generator.writeKey("grok");
			this.grok.serialize(generator, mapper);

		}
		if (this.gsub != null) {
			generator.writeKey("gsub");
			this.gsub.serialize(generator, mapper);

		}
		if (this.join != null) {
			generator.writeKey("join");
			this.join.serialize(generator, mapper);

		}
		if (this.lowercase != null) {
			generator.writeKey("lowercase");
			this.lowercase.serialize(generator, mapper);

		}
		if (this.remove != null) {
			generator.writeKey("remove");
			this.remove.serialize(generator, mapper);

		}
		if (this.rename != null) {
			generator.writeKey("rename");
			this.rename.serialize(generator, mapper);

		}
		if (this.script != null) {
			generator.writeKey("script");
			this.script.serialize(generator, mapper);

		}
		if (this.set != null) {
			generator.writeKey("set");
			this.set.serialize(generator, mapper);

		}
		if (this.sort != null) {
			generator.writeKey("sort");
			this.sort.serialize(generator, mapper);

		}
		if (this.split != null) {
			generator.writeKey("split");
			this.split.serialize(generator, mapper);

		}
		if (this.trim != null) {
			generator.writeKey("trim");
			this.trim.serialize(generator, mapper);

		}
		if (this.uppercase != null) {
			generator.writeKey("uppercase");
			this.uppercase.serialize(generator, mapper);

		}
		if (this.urldecode != null) {
			generator.writeKey("urldecode");
			this.urldecode.serialize(generator, mapper);

		}
		if (this.bytes != null) {
			generator.writeKey("bytes");
			this.bytes.serialize(generator, mapper);

		}
		if (this.dissect != null) {
			generator.writeKey("dissect");
			this.dissect.serialize(generator, mapper);

		}
		if (this.setSecurityUser != null) {
			generator.writeKey("set_security_user");
			this.setSecurityUser.serialize(generator, mapper);

		}
		if (this.pipeline != null) {
			generator.writeKey("pipeline");
			this.pipeline.serialize(generator, mapper);

		}
		if (this.drop != null) {
			generator.writeKey("drop");
			this.drop.serialize(generator, mapper);

		}
		if (this.circle != null) {
			generator.writeKey("circle");
			this.circle.serialize(generator, mapper);

		}
		if (this.inference != null) {
			generator.writeKey("inference");
			this.inference.serialize(generator, mapper);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link NodeInfoSettingsIngest}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<NodeInfoSettingsIngest> {
		@Nullable
		private NodeInfoIngestInfo attachment;

		@Nullable
		private NodeInfoIngestInfo append;

		@Nullable
		private NodeInfoIngestInfo csv;

		@Nullable
		private NodeInfoIngestInfo convert;

		@Nullable
		private NodeInfoIngestInfo date;

		@Nullable
		private NodeInfoIngestInfo dateIndexName;

		@Nullable
		private NodeInfoIngestInfo dotExpander;

		@Nullable
		private NodeInfoIngestInfo fail;

		@Nullable
		private NodeInfoIngestInfo foreach;

		@Nullable
		private NodeInfoIngestInfo json;

		@Nullable
		private NodeInfoIngestInfo userAgent;

		@Nullable
		private NodeInfoIngestInfo kv;

		@Nullable
		private NodeInfoIngestInfo geoip;

		@Nullable
		private NodeInfoIngestInfo grok;

		@Nullable
		private NodeInfoIngestInfo gsub;

		@Nullable
		private NodeInfoIngestInfo join;

		@Nullable
		private NodeInfoIngestInfo lowercase;

		@Nullable
		private NodeInfoIngestInfo remove;

		@Nullable
		private NodeInfoIngestInfo rename;

		@Nullable
		private NodeInfoIngestInfo script;

		@Nullable
		private NodeInfoIngestInfo set;

		@Nullable
		private NodeInfoIngestInfo sort;

		@Nullable
		private NodeInfoIngestInfo split;

		@Nullable
		private NodeInfoIngestInfo trim;

		@Nullable
		private NodeInfoIngestInfo uppercase;

		@Nullable
		private NodeInfoIngestInfo urldecode;

		@Nullable
		private NodeInfoIngestInfo bytes;

		@Nullable
		private NodeInfoIngestInfo dissect;

		@Nullable
		private NodeInfoIngestInfo setSecurityUser;

		@Nullable
		private NodeInfoIngestInfo pipeline;

		@Nullable
		private NodeInfoIngestInfo drop;

		@Nullable
		private NodeInfoIngestInfo circle;

		@Nullable
		private NodeInfoIngestInfo inference;

		/**
		 * API name: {@code attachment}
		 */
		public final Builder attachment(@Nullable NodeInfoIngestInfo value) {
			this.attachment = value;
			return this;
		}

		/**
		 * API name: {@code attachment}
		 */
		public final Builder attachment(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.attachment(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code append}
		 */
		public final Builder append(@Nullable NodeInfoIngestInfo value) {
			this.append = value;
			return this;
		}

		/**
		 * API name: {@code append}
		 */
		public final Builder append(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.append(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code csv}
		 */
		public final Builder csv(@Nullable NodeInfoIngestInfo value) {
			this.csv = value;
			return this;
		}

		/**
		 * API name: {@code csv}
		 */
		public final Builder csv(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.csv(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code convert}
		 */
		public final Builder convert(@Nullable NodeInfoIngestInfo value) {
			this.convert = value;
			return this;
		}

		/**
		 * API name: {@code convert}
		 */
		public final Builder convert(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.convert(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code date}
		 */
		public final Builder date(@Nullable NodeInfoIngestInfo value) {
			this.date = value;
			return this;
		}

		/**
		 * API name: {@code date}
		 */
		public final Builder date(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.date(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code date_index_name}
		 */
		public final Builder dateIndexName(@Nullable NodeInfoIngestInfo value) {
			this.dateIndexName = value;
			return this;
		}

		/**
		 * API name: {@code date_index_name}
		 */
		public final Builder dateIndexName(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.dateIndexName(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code dot_expander}
		 */
		public final Builder dotExpander(@Nullable NodeInfoIngestInfo value) {
			this.dotExpander = value;
			return this;
		}

		/**
		 * API name: {@code dot_expander}
		 */
		public final Builder dotExpander(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.dotExpander(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code fail}
		 */
		public final Builder fail(@Nullable NodeInfoIngestInfo value) {
			this.fail = value;
			return this;
		}

		/**
		 * API name: {@code fail}
		 */
		public final Builder fail(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.fail(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code foreach}
		 */
		public final Builder foreach(@Nullable NodeInfoIngestInfo value) {
			this.foreach = value;
			return this;
		}

		/**
		 * API name: {@code foreach}
		 */
		public final Builder foreach(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.foreach(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code json}
		 */
		public final Builder json(@Nullable NodeInfoIngestInfo value) {
			this.json = value;
			return this;
		}

		/**
		 * API name: {@code json}
		 */
		public final Builder json(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.json(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code user_agent}
		 */
		public final Builder userAgent(@Nullable NodeInfoIngestInfo value) {
			this.userAgent = value;
			return this;
		}

		/**
		 * API name: {@code user_agent}
		 */
		public final Builder userAgent(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.userAgent(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code kv}
		 */
		public final Builder kv(@Nullable NodeInfoIngestInfo value) {
			this.kv = value;
			return this;
		}

		/**
		 * API name: {@code kv}
		 */
		public final Builder kv(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.kv(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code geoip}
		 */
		public final Builder geoip(@Nullable NodeInfoIngestInfo value) {
			this.geoip = value;
			return this;
		}

		/**
		 * API name: {@code geoip}
		 */
		public final Builder geoip(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.geoip(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code grok}
		 */
		public final Builder grok(@Nullable NodeInfoIngestInfo value) {
			this.grok = value;
			return this;
		}

		/**
		 * API name: {@code grok}
		 */
		public final Builder grok(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.grok(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code gsub}
		 */
		public final Builder gsub(@Nullable NodeInfoIngestInfo value) {
			this.gsub = value;
			return this;
		}

		/**
		 * API name: {@code gsub}
		 */
		public final Builder gsub(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.gsub(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code join}
		 */
		public final Builder join(@Nullable NodeInfoIngestInfo value) {
			this.join = value;
			return this;
		}

		/**
		 * API name: {@code join}
		 */
		public final Builder join(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.join(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code lowercase}
		 */
		public final Builder lowercase(@Nullable NodeInfoIngestInfo value) {
			this.lowercase = value;
			return this;
		}

		/**
		 * API name: {@code lowercase}
		 */
		public final Builder lowercase(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.lowercase(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code remove}
		 */
		public final Builder remove(@Nullable NodeInfoIngestInfo value) {
			this.remove = value;
			return this;
		}

		/**
		 * API name: {@code remove}
		 */
		public final Builder remove(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.remove(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code rename}
		 */
		public final Builder rename(@Nullable NodeInfoIngestInfo value) {
			this.rename = value;
			return this;
		}

		/**
		 * API name: {@code rename}
		 */
		public final Builder rename(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.rename(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code script}
		 */
		public final Builder script(@Nullable NodeInfoIngestInfo value) {
			this.script = value;
			return this;
		}

		/**
		 * API name: {@code script}
		 */
		public final Builder script(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.script(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code set}
		 */
		public final Builder set(@Nullable NodeInfoIngestInfo value) {
			this.set = value;
			return this;
		}

		/**
		 * API name: {@code set}
		 */
		public final Builder set(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.set(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code sort}
		 */
		public final Builder sort(@Nullable NodeInfoIngestInfo value) {
			this.sort = value;
			return this;
		}

		/**
		 * API name: {@code sort}
		 */
		public final Builder sort(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.sort(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code split}
		 */
		public final Builder split(@Nullable NodeInfoIngestInfo value) {
			this.split = value;
			return this;
		}

		/**
		 * API name: {@code split}
		 */
		public final Builder split(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.split(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code trim}
		 */
		public final Builder trim(@Nullable NodeInfoIngestInfo value) {
			this.trim = value;
			return this;
		}

		/**
		 * API name: {@code trim}
		 */
		public final Builder trim(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.trim(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code uppercase}
		 */
		public final Builder uppercase(@Nullable NodeInfoIngestInfo value) {
			this.uppercase = value;
			return this;
		}

		/**
		 * API name: {@code uppercase}
		 */
		public final Builder uppercase(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.uppercase(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code urldecode}
		 */
		public final Builder urldecode(@Nullable NodeInfoIngestInfo value) {
			this.urldecode = value;
			return this;
		}

		/**
		 * API name: {@code urldecode}
		 */
		public final Builder urldecode(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.urldecode(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code bytes}
		 */
		public final Builder bytes(@Nullable NodeInfoIngestInfo value) {
			this.bytes = value;
			return this;
		}

		/**
		 * API name: {@code bytes}
		 */
		public final Builder bytes(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.bytes(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code dissect}
		 */
		public final Builder dissect(@Nullable NodeInfoIngestInfo value) {
			this.dissect = value;
			return this;
		}

		/**
		 * API name: {@code dissect}
		 */
		public final Builder dissect(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.dissect(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code set_security_user}
		 */
		public final Builder setSecurityUser(@Nullable NodeInfoIngestInfo value) {
			this.setSecurityUser = value;
			return this;
		}

		/**
		 * API name: {@code set_security_user}
		 */
		public final Builder setSecurityUser(
				Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.setSecurityUser(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code pipeline}
		 */
		public final Builder pipeline(@Nullable NodeInfoIngestInfo value) {
			this.pipeline = value;
			return this;
		}

		/**
		 * API name: {@code pipeline}
		 */
		public final Builder pipeline(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.pipeline(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code drop}
		 */
		public final Builder drop(@Nullable NodeInfoIngestInfo value) {
			this.drop = value;
			return this;
		}

		/**
		 * API name: {@code drop}
		 */
		public final Builder drop(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.drop(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code circle}
		 */
		public final Builder circle(@Nullable NodeInfoIngestInfo value) {
			this.circle = value;
			return this;
		}

		/**
		 * API name: {@code circle}
		 */
		public final Builder circle(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.circle(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * API name: {@code inference}
		 */
		public final Builder inference(@Nullable NodeInfoIngestInfo value) {
			this.inference = value;
			return this;
		}

		/**
		 * API name: {@code inference}
		 */
		public final Builder inference(Function<NodeInfoIngestInfo.Builder, ObjectBuilder<NodeInfoIngestInfo>> fn) {
			return this.inference(fn.apply(new NodeInfoIngestInfo.Builder()).build());
		}

		/**
		 * Builds a {@link NodeInfoSettingsIngest}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public NodeInfoSettingsIngest build() {
			_checkSingleUse();

			return new NodeInfoSettingsIngest(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link NodeInfoSettingsIngest}
	 */
	public static final JsonpDeserializer<NodeInfoSettingsIngest> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, NodeInfoSettingsIngest::setupNodeInfoSettingsIngestDeserializer);

	protected static void setupNodeInfoSettingsIngestDeserializer(
			ObjectDeserializer<NodeInfoSettingsIngest.Builder> op) {

		op.add(Builder::attachment, NodeInfoIngestInfo._DESERIALIZER, "attachment");
		op.add(Builder::append, NodeInfoIngestInfo._DESERIALIZER, "append");
		op.add(Builder::csv, NodeInfoIngestInfo._DESERIALIZER, "csv");
		op.add(Builder::convert, NodeInfoIngestInfo._DESERIALIZER, "convert");
		op.add(Builder::date, NodeInfoIngestInfo._DESERIALIZER, "date");
		op.add(Builder::dateIndexName, NodeInfoIngestInfo._DESERIALIZER, "date_index_name");
		op.add(Builder::dotExpander, NodeInfoIngestInfo._DESERIALIZER, "dot_expander");
		op.add(Builder::fail, NodeInfoIngestInfo._DESERIALIZER, "fail");
		op.add(Builder::foreach, NodeInfoIngestInfo._DESERIALIZER, "foreach");
		op.add(Builder::json, NodeInfoIngestInfo._DESERIALIZER, "json");
		op.add(Builder::userAgent, NodeInfoIngestInfo._DESERIALIZER, "user_agent");
		op.add(Builder::kv, NodeInfoIngestInfo._DESERIALIZER, "kv");
		op.add(Builder::geoip, NodeInfoIngestInfo._DESERIALIZER, "geoip");
		op.add(Builder::grok, NodeInfoIngestInfo._DESERIALIZER, "grok");
		op.add(Builder::gsub, NodeInfoIngestInfo._DESERIALIZER, "gsub");
		op.add(Builder::join, NodeInfoIngestInfo._DESERIALIZER, "join");
		op.add(Builder::lowercase, NodeInfoIngestInfo._DESERIALIZER, "lowercase");
		op.add(Builder::remove, NodeInfoIngestInfo._DESERIALIZER, "remove");
		op.add(Builder::rename, NodeInfoIngestInfo._DESERIALIZER, "rename");
		op.add(Builder::script, NodeInfoIngestInfo._DESERIALIZER, "script");
		op.add(Builder::set, NodeInfoIngestInfo._DESERIALIZER, "set");
		op.add(Builder::sort, NodeInfoIngestInfo._DESERIALIZER, "sort");
		op.add(Builder::split, NodeInfoIngestInfo._DESERIALIZER, "split");
		op.add(Builder::trim, NodeInfoIngestInfo._DESERIALIZER, "trim");
		op.add(Builder::uppercase, NodeInfoIngestInfo._DESERIALIZER, "uppercase");
		op.add(Builder::urldecode, NodeInfoIngestInfo._DESERIALIZER, "urldecode");
		op.add(Builder::bytes, NodeInfoIngestInfo._DESERIALIZER, "bytes");
		op.add(Builder::dissect, NodeInfoIngestInfo._DESERIALIZER, "dissect");
		op.add(Builder::setSecurityUser, NodeInfoIngestInfo._DESERIALIZER, "set_security_user");
		op.add(Builder::pipeline, NodeInfoIngestInfo._DESERIALIZER, "pipeline");
		op.add(Builder::drop, NodeInfoIngestInfo._DESERIALIZER, "drop");
		op.add(Builder::circle, NodeInfoIngestInfo._DESERIALIZER, "circle");
		op.add(Builder::inference, NodeInfoIngestInfo._DESERIALIZER, "inference");

	}

}
