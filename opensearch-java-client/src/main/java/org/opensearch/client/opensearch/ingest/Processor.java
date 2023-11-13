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

// typedef: ingest._types.ProcessorContainer

@JsonpDeserializable
public class Processor implements TaggedUnion<Processor.Kind, Object>, JsonpSerializable {

	/**
	 * {@link Processor} variant kinds.
	 */
	/**
	 * {@link Processor} variant kinds.
	 */

	public enum Kind implements JsonEnum {
		Attachment("attachment"),

		Append("append"),

		Csv("csv"),

		Convert("convert"),

		Date("date"),

		DateIndexName("date_index_name"),

		DotExpander("dot_expander"),

		Fail("fail"),

		Foreach("foreach"),

		Json("json"),

		UserAgent("user_agent"),

		Kv("kv"),

		Geoip("geoip"),

		Grok("grok"),

		Gsub("gsub"),

		Join("join"),

		Lowercase("lowercase"),

		Remove("remove"),

		Rename("rename"),

		Script("script"),

		Set("set"),

		Sort("sort"),

		Split("split"),

		Trim("trim"),

		Uppercase("uppercase"),

		Urldecode("urldecode"),

		Bytes("bytes"),

		Dissect("dissect"),

		SetSecurityUser("set_security_user"),

		Pipeline("pipeline"),

		Drop("drop"),

		Circle("circle"),

		Inference("inference"),

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
	private final Object _value;

	@Override
	public final Kind _kind() {
		return _kind;
	}

	@Override
	public final Object _get() {
		return _value;
	}

	public Processor(ProcessorVariant value) {

		this._kind = ApiTypeHelper.requireNonNull(value._processorKind(), this, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(value, this, "<variant value>");

	}

	private Processor(Builder builder) {

		this._kind = ApiTypeHelper.requireNonNull(builder._kind, builder, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(builder._value, builder, "<variant value>");

	}

	public static Processor of(Function<Builder, ObjectBuilder<Processor>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Is this variant instance of kind {@code attachment}?
	 */
	public boolean isAttachment() {
		return _kind == Kind.Attachment;
	}

	/**
	 * Get the {@code attachment} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code attachment} kind.
	 */
	public AttachmentProcessor attachment() {
		return TaggedUnionUtils.get(this, Kind.Attachment);
	}

	/**
	 * Is this variant instance of kind {@code append}?
	 */
	public boolean isAppend() {
		return _kind == Kind.Append;
	}

	/**
	 * Get the {@code append} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code append} kind.
	 */
	public AppendProcessor append() {
		return TaggedUnionUtils.get(this, Kind.Append);
	}

	/**
	 * Is this variant instance of kind {@code csv}?
	 */
	public boolean isCsv() {
		return _kind == Kind.Csv;
	}

	/**
	 * Get the {@code csv} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code csv} kind.
	 */
	public CsvProcessor csv() {
		return TaggedUnionUtils.get(this, Kind.Csv);
	}

	/**
	 * Is this variant instance of kind {@code convert}?
	 */
	public boolean isConvert() {
		return _kind == Kind.Convert;
	}

	/**
	 * Get the {@code convert} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code convert} kind.
	 */
	public ConvertProcessor convert() {
		return TaggedUnionUtils.get(this, Kind.Convert);
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
	public DateProcessor date() {
		return TaggedUnionUtils.get(this, Kind.Date);
	}

	/**
	 * Is this variant instance of kind {@code date_index_name}?
	 */
	public boolean isDateIndexName() {
		return _kind == Kind.DateIndexName;
	}

	/**
	 * Get the {@code date_index_name} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code date_index_name}
	 *             kind.
	 */
	public DateIndexNameProcessor dateIndexName() {
		return TaggedUnionUtils.get(this, Kind.DateIndexName);
	}

	/**
	 * Is this variant instance of kind {@code dot_expander}?
	 */
	public boolean isDotExpander() {
		return _kind == Kind.DotExpander;
	}

	/**
	 * Get the {@code dot_expander} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code dot_expander} kind.
	 */
	public DotExpanderProcessor dotExpander() {
		return TaggedUnionUtils.get(this, Kind.DotExpander);
	}

	/**
	 * Is this variant instance of kind {@code fail}?
	 */
	public boolean isFail() {
		return _kind == Kind.Fail;
	}

	/**
	 * Get the {@code fail} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code fail} kind.
	 */
	public FailProcessor fail() {
		return TaggedUnionUtils.get(this, Kind.Fail);
	}

	/**
	 * Is this variant instance of kind {@code foreach}?
	 */
	public boolean isForeach() {
		return _kind == Kind.Foreach;
	}

	/**
	 * Get the {@code foreach} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code foreach} kind.
	 */
	public ForeachProcessor foreach() {
		return TaggedUnionUtils.get(this, Kind.Foreach);
	}

	/**
	 * Is this variant instance of kind {@code json}?
	 */
	public boolean isJson() {
		return _kind == Kind.Json;
	}

	/**
	 * Get the {@code json} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code json} kind.
	 */
	public JsonProcessor json() {
		return TaggedUnionUtils.get(this, Kind.Json);
	}

	/**
	 * Is this variant instance of kind {@code user_agent}?
	 */
	public boolean isUserAgent() {
		return _kind == Kind.UserAgent;
	}

	/**
	 * Get the {@code user_agent} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code user_agent} kind.
	 */
	public UserAgentProcessor userAgent() {
		return TaggedUnionUtils.get(this, Kind.UserAgent);
	}

	/**
	 * Is this variant instance of kind {@code kv}?
	 */
	public boolean isKv() {
		return _kind == Kind.Kv;
	}

	/**
	 * Get the {@code kv} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code kv} kind.
	 */
	public KeyValueProcessor kv() {
		return TaggedUnionUtils.get(this, Kind.Kv);
	}

	/**
	 * Is this variant instance of kind {@code geoip}?
	 */
	public boolean isGeoip() {
		return _kind == Kind.Geoip;
	}

	/**
	 * Get the {@code geoip} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code geoip} kind.
	 */
	public GeoIpProcessor geoip() {
		return TaggedUnionUtils.get(this, Kind.Geoip);
	}

	/**
	 * Is this variant instance of kind {@code grok}?
	 */
	public boolean isGrok() {
		return _kind == Kind.Grok;
	}

	/**
	 * Get the {@code grok} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code grok} kind.
	 */
	public GrokProcessor grok() {
		return TaggedUnionUtils.get(this, Kind.Grok);
	}

	/**
	 * Is this variant instance of kind {@code gsub}?
	 */
	public boolean isGsub() {
		return _kind == Kind.Gsub;
	}

	/**
	 * Get the {@code gsub} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code gsub} kind.
	 */
	public GsubProcessor gsub() {
		return TaggedUnionUtils.get(this, Kind.Gsub);
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
	public JoinProcessor join() {
		return TaggedUnionUtils.get(this, Kind.Join);
	}

	/**
	 * Is this variant instance of kind {@code lowercase}?
	 */
	public boolean isLowercase() {
		return _kind == Kind.Lowercase;
	}

	/**
	 * Get the {@code lowercase} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code lowercase} kind.
	 */
	public LowercaseProcessor lowercase() {
		return TaggedUnionUtils.get(this, Kind.Lowercase);
	}

	/**
	 * Is this variant instance of kind {@code remove}?
	 */
	public boolean isRemove() {
		return _kind == Kind.Remove;
	}

	/**
	 * Get the {@code remove} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code remove} kind.
	 */
	public RemoveProcessor remove() {
		return TaggedUnionUtils.get(this, Kind.Remove);
	}

	/**
	 * Is this variant instance of kind {@code rename}?
	 */
	public boolean isRename() {
		return _kind == Kind.Rename;
	}

	/**
	 * Get the {@code rename} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code rename} kind.
	 */
	public RenameProcessor rename() {
		return TaggedUnionUtils.get(this, Kind.Rename);
	}

	/**
	 * Is this variant instance of kind {@code script}?
	 */
	public boolean isScript() {
		return _kind == Kind.Script;
	}

	/**
	 * Get the {@code script} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code script} kind.
	 */
	public Script script() {
		return TaggedUnionUtils.get(this, Kind.Script);
	}

	/**
	 * Is this variant instance of kind {@code set}?
	 */
	public boolean isSet() {
		return _kind == Kind.Set;
	}

	/**
	 * Get the {@code set} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code set} kind.
	 */
	public SetProcessor set() {
		return TaggedUnionUtils.get(this, Kind.Set);
	}

	/**
	 * Is this variant instance of kind {@code sort}?
	 */
	public boolean isSort() {
		return _kind == Kind.Sort;
	}

	/**
	 * Get the {@code sort} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code sort} kind.
	 */
	public SortProcessor sort() {
		return TaggedUnionUtils.get(this, Kind.Sort);
	}

	/**
	 * Is this variant instance of kind {@code split}?
	 */
	public boolean isSplit() {
		return _kind == Kind.Split;
	}

	/**
	 * Get the {@code split} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code split} kind.
	 */
	public SplitProcessor split() {
		return TaggedUnionUtils.get(this, Kind.Split);
	}

	/**
	 * Is this variant instance of kind {@code trim}?
	 */
	public boolean isTrim() {
		return _kind == Kind.Trim;
	}

	/**
	 * Get the {@code trim} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code trim} kind.
	 */
	public TrimProcessor trim() {
		return TaggedUnionUtils.get(this, Kind.Trim);
	}

	/**
	 * Is this variant instance of kind {@code uppercase}?
	 */
	public boolean isUppercase() {
		return _kind == Kind.Uppercase;
	}

	/**
	 * Get the {@code uppercase} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code uppercase} kind.
	 */
	public UppercaseProcessor uppercase() {
		return TaggedUnionUtils.get(this, Kind.Uppercase);
	}

	/**
	 * Is this variant instance of kind {@code urldecode}?
	 */
	public boolean isUrldecode() {
		return _kind == Kind.Urldecode;
	}

	/**
	 * Get the {@code urldecode} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code urldecode} kind.
	 */
	public UrlDecodeProcessor urldecode() {
		return TaggedUnionUtils.get(this, Kind.Urldecode);
	}

	/**
	 * Is this variant instance of kind {@code bytes}?
	 */
	public boolean isBytes() {
		return _kind == Kind.Bytes;
	}

	/**
	 * Get the {@code bytes} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code bytes} kind.
	 */
	public BytesProcessor bytes() {
		return TaggedUnionUtils.get(this, Kind.Bytes);
	}

	/**
	 * Is this variant instance of kind {@code dissect}?
	 */
	public boolean isDissect() {
		return _kind == Kind.Dissect;
	}

	/**
	 * Get the {@code dissect} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code dissect} kind.
	 */
	public DissectProcessor dissect() {
		return TaggedUnionUtils.get(this, Kind.Dissect);
	}

	/**
	 * Is this variant instance of kind {@code set_security_user}?
	 */
	public boolean isSetSecurityUser() {
		return _kind == Kind.SetSecurityUser;
	}

	/**
	 * Get the {@code set_security_user} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code set_security_user}
	 *             kind.
	 */
	public SetSecurityUserProcessor setSecurityUser() {
		return TaggedUnionUtils.get(this, Kind.SetSecurityUser);
	}

	/**
	 * Is this variant instance of kind {@code pipeline}?
	 */
	public boolean isPipeline() {
		return _kind == Kind.Pipeline;
	}

	/**
	 * Get the {@code pipeline} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code pipeline} kind.
	 */
	public PipelineProcessor pipeline() {
		return TaggedUnionUtils.get(this, Kind.Pipeline);
	}

	/**
	 * Is this variant instance of kind {@code drop}?
	 */
	public boolean isDrop() {
		return _kind == Kind.Drop;
	}

	/**
	 * Get the {@code drop} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code drop} kind.
	 */
	public DropProcessor drop() {
		return TaggedUnionUtils.get(this, Kind.Drop);
	}

	/**
	 * Is this variant instance of kind {@code circle}?
	 */
	public boolean isCircle() {
		return _kind == Kind.Circle;
	}

	/**
	 * Get the {@code circle} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code circle} kind.
	 */
	public CircleProcessor circle() {
		return TaggedUnionUtils.get(this, Kind.Circle);
	}

	/**
	 * Is this variant instance of kind {@code inference}?
	 */
	public boolean isInference() {
		return _kind == Kind.Inference;
	}

	/**
	 * Get the {@code inference} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code inference} kind.
	 */
	public InferenceProcessor inference() {
		return TaggedUnionUtils.get(this, Kind.Inference);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {

		generator.writeStartObject();

		generator.writeKey(_kind.jsonValue());
		if (_value instanceof JsonpSerializable) {
			((JsonpSerializable) _value).serialize(generator, mapper);
		}

		generator.writeEnd();

	}

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Processor> {
		private Kind _kind;
		private Object _value;

		public ObjectBuilder<Processor> attachment(AttachmentProcessor v) {
			this._kind = Kind.Attachment;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> attachment(
				Function<AttachmentProcessor.Builder, ObjectBuilder<AttachmentProcessor>> fn) {
			return this.attachment(fn.apply(new AttachmentProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> append(AppendProcessor v) {
			this._kind = Kind.Append;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> append(Function<AppendProcessor.Builder, ObjectBuilder<AppendProcessor>> fn) {
			return this.append(fn.apply(new AppendProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> csv(CsvProcessor v) {
			this._kind = Kind.Csv;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> csv(Function<CsvProcessor.Builder, ObjectBuilder<CsvProcessor>> fn) {
			return this.csv(fn.apply(new CsvProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> convert(ConvertProcessor v) {
			this._kind = Kind.Convert;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> convert(
				Function<ConvertProcessor.Builder, ObjectBuilder<ConvertProcessor>> fn) {
			return this.convert(fn.apply(new ConvertProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> date(DateProcessor v) {
			this._kind = Kind.Date;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> date(Function<DateProcessor.Builder, ObjectBuilder<DateProcessor>> fn) {
			return this.date(fn.apply(new DateProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> dateIndexName(DateIndexNameProcessor v) {
			this._kind = Kind.DateIndexName;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> dateIndexName(
				Function<DateIndexNameProcessor.Builder, ObjectBuilder<DateIndexNameProcessor>> fn) {
			return this.dateIndexName(fn.apply(new DateIndexNameProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> dotExpander(DotExpanderProcessor v) {
			this._kind = Kind.DotExpander;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> dotExpander(
				Function<DotExpanderProcessor.Builder, ObjectBuilder<DotExpanderProcessor>> fn) {
			return this.dotExpander(fn.apply(new DotExpanderProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> fail(FailProcessor v) {
			this._kind = Kind.Fail;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> fail(Function<FailProcessor.Builder, ObjectBuilder<FailProcessor>> fn) {
			return this.fail(fn.apply(new FailProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> foreach(ForeachProcessor v) {
			this._kind = Kind.Foreach;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> foreach(
				Function<ForeachProcessor.Builder, ObjectBuilder<ForeachProcessor>> fn) {
			return this.foreach(fn.apply(new ForeachProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> json(JsonProcessor v) {
			this._kind = Kind.Json;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> json(Function<JsonProcessor.Builder, ObjectBuilder<JsonProcessor>> fn) {
			return this.json(fn.apply(new JsonProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> userAgent(UserAgentProcessor v) {
			this._kind = Kind.UserAgent;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> userAgent(
				Function<UserAgentProcessor.Builder, ObjectBuilder<UserAgentProcessor>> fn) {
			return this.userAgent(fn.apply(new UserAgentProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> kv(KeyValueProcessor v) {
			this._kind = Kind.Kv;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> kv(Function<KeyValueProcessor.Builder, ObjectBuilder<KeyValueProcessor>> fn) {
			return this.kv(fn.apply(new KeyValueProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> geoip(GeoIpProcessor v) {
			this._kind = Kind.Geoip;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> geoip(Function<GeoIpProcessor.Builder, ObjectBuilder<GeoIpProcessor>> fn) {
			return this.geoip(fn.apply(new GeoIpProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> grok(GrokProcessor v) {
			this._kind = Kind.Grok;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> grok(Function<GrokProcessor.Builder, ObjectBuilder<GrokProcessor>> fn) {
			return this.grok(fn.apply(new GrokProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> gsub(GsubProcessor v) {
			this._kind = Kind.Gsub;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> gsub(Function<GsubProcessor.Builder, ObjectBuilder<GsubProcessor>> fn) {
			return this.gsub(fn.apply(new GsubProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> join(JoinProcessor v) {
			this._kind = Kind.Join;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> join(Function<JoinProcessor.Builder, ObjectBuilder<JoinProcessor>> fn) {
			return this.join(fn.apply(new JoinProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> lowercase(LowercaseProcessor v) {
			this._kind = Kind.Lowercase;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> lowercase(
				Function<LowercaseProcessor.Builder, ObjectBuilder<LowercaseProcessor>> fn) {
			return this.lowercase(fn.apply(new LowercaseProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> remove(RemoveProcessor v) {
			this._kind = Kind.Remove;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> remove(Function<RemoveProcessor.Builder, ObjectBuilder<RemoveProcessor>> fn) {
			return this.remove(fn.apply(new RemoveProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> rename(RenameProcessor v) {
			this._kind = Kind.Rename;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> rename(Function<RenameProcessor.Builder, ObjectBuilder<RenameProcessor>> fn) {
			return this.rename(fn.apply(new RenameProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> script(Script v) {
			this._kind = Kind.Script;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> script(Function<Script.Builder, ObjectBuilder<Script>> fn) {
			return this.script(fn.apply(new Script.Builder()).build());
		}

		public ObjectBuilder<Processor> set(SetProcessor v) {
			this._kind = Kind.Set;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> set(Function<SetProcessor.Builder, ObjectBuilder<SetProcessor>> fn) {
			return this.set(fn.apply(new SetProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> sort(SortProcessor v) {
			this._kind = Kind.Sort;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> sort(Function<SortProcessor.Builder, ObjectBuilder<SortProcessor>> fn) {
			return this.sort(fn.apply(new SortProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> split(SplitProcessor v) {
			this._kind = Kind.Split;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> split(Function<SplitProcessor.Builder, ObjectBuilder<SplitProcessor>> fn) {
			return this.split(fn.apply(new SplitProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> trim(TrimProcessor v) {
			this._kind = Kind.Trim;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> trim(Function<TrimProcessor.Builder, ObjectBuilder<TrimProcessor>> fn) {
			return this.trim(fn.apply(new TrimProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> uppercase(UppercaseProcessor v) {
			this._kind = Kind.Uppercase;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> uppercase(
				Function<UppercaseProcessor.Builder, ObjectBuilder<UppercaseProcessor>> fn) {
			return this.uppercase(fn.apply(new UppercaseProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> urldecode(UrlDecodeProcessor v) {
			this._kind = Kind.Urldecode;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> urldecode(
				Function<UrlDecodeProcessor.Builder, ObjectBuilder<UrlDecodeProcessor>> fn) {
			return this.urldecode(fn.apply(new UrlDecodeProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> bytes(BytesProcessor v) {
			this._kind = Kind.Bytes;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> bytes(Function<BytesProcessor.Builder, ObjectBuilder<BytesProcessor>> fn) {
			return this.bytes(fn.apply(new BytesProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> dissect(DissectProcessor v) {
			this._kind = Kind.Dissect;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> dissect(
				Function<DissectProcessor.Builder, ObjectBuilder<DissectProcessor>> fn) {
			return this.dissect(fn.apply(new DissectProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> setSecurityUser(SetSecurityUserProcessor v) {
			this._kind = Kind.SetSecurityUser;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> setSecurityUser(
				Function<SetSecurityUserProcessor.Builder, ObjectBuilder<SetSecurityUserProcessor>> fn) {
			return this.setSecurityUser(fn.apply(new SetSecurityUserProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> pipeline(PipelineProcessor v) {
			this._kind = Kind.Pipeline;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> pipeline(
				Function<PipelineProcessor.Builder, ObjectBuilder<PipelineProcessor>> fn) {
			return this.pipeline(fn.apply(new PipelineProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> drop(DropProcessor v) {
			this._kind = Kind.Drop;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> drop(Function<DropProcessor.Builder, ObjectBuilder<DropProcessor>> fn) {
			return this.drop(fn.apply(new DropProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> circle(CircleProcessor v) {
			this._kind = Kind.Circle;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> circle(Function<CircleProcessor.Builder, ObjectBuilder<CircleProcessor>> fn) {
			return this.circle(fn.apply(new CircleProcessor.Builder()).build());
		}

		public ObjectBuilder<Processor> inference(InferenceProcessor v) {
			this._kind = Kind.Inference;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Processor> inference(
				Function<InferenceProcessor.Builder, ObjectBuilder<InferenceProcessor>> fn) {
			return this.inference(fn.apply(new InferenceProcessor.Builder()).build());
		}

		public Processor build() {
			_checkSingleUse();
			return new Processor(this);
		}

	}

	protected static void setupProcessorDeserializer(ObjectDeserializer<Builder> op) {

		op.add(Builder::attachment, AttachmentProcessor._DESERIALIZER, "attachment");
		op.add(Builder::append, AppendProcessor._DESERIALIZER, "append");
		op.add(Builder::csv, CsvProcessor._DESERIALIZER, "csv");
		op.add(Builder::convert, ConvertProcessor._DESERIALIZER, "convert");
		op.add(Builder::date, DateProcessor._DESERIALIZER, "date");
		op.add(Builder::dateIndexName, DateIndexNameProcessor._DESERIALIZER, "date_index_name");
		op.add(Builder::dotExpander, DotExpanderProcessor._DESERIALIZER, "dot_expander");
		op.add(Builder::fail, FailProcessor._DESERIALIZER, "fail");
		op.add(Builder::foreach, ForeachProcessor._DESERIALIZER, "foreach");
		op.add(Builder::json, JsonProcessor._DESERIALIZER, "json");
		op.add(Builder::userAgent, UserAgentProcessor._DESERIALIZER, "user_agent");
		op.add(Builder::kv, KeyValueProcessor._DESERIALIZER, "kv");
		op.add(Builder::geoip, GeoIpProcessor._DESERIALIZER, "geoip");
		op.add(Builder::grok, GrokProcessor._DESERIALIZER, "grok");
		op.add(Builder::gsub, GsubProcessor._DESERIALIZER, "gsub");
		op.add(Builder::join, JoinProcessor._DESERIALIZER, "join");
		op.add(Builder::lowercase, LowercaseProcessor._DESERIALIZER, "lowercase");
		op.add(Builder::remove, RemoveProcessor._DESERIALIZER, "remove");
		op.add(Builder::rename, RenameProcessor._DESERIALIZER, "rename");
		op.add(Builder::script, Script._DESERIALIZER, "script");
		op.add(Builder::set, SetProcessor._DESERIALIZER, "set");
		op.add(Builder::sort, SortProcessor._DESERIALIZER, "sort");
		op.add(Builder::split, SplitProcessor._DESERIALIZER, "split");
		op.add(Builder::trim, TrimProcessor._DESERIALIZER, "trim");
		op.add(Builder::uppercase, UppercaseProcessor._DESERIALIZER, "uppercase");
		op.add(Builder::urldecode, UrlDecodeProcessor._DESERIALIZER, "urldecode");
		op.add(Builder::bytes, BytesProcessor._DESERIALIZER, "bytes");
		op.add(Builder::dissect, DissectProcessor._DESERIALIZER, "dissect");
		op.add(Builder::setSecurityUser, SetSecurityUserProcessor._DESERIALIZER, "set_security_user");
		op.add(Builder::pipeline, PipelineProcessor._DESERIALIZER, "pipeline");
		op.add(Builder::drop, DropProcessor._DESERIALIZER, "drop");
		op.add(Builder::circle, CircleProcessor._DESERIALIZER, "circle");
		op.add(Builder::inference, InferenceProcessor._DESERIALIZER, "inference");

	}

	public static final JsonpDeserializer<Processor> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			Processor::setupProcessorDeserializer, Builder::build);
}
