/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.translate;

import io.camunda.analytics.lake.metrics.PollFedRider;
import io.camunda.analytics.lake.objects.CompiledObjectType;
import io.camunda.analytics.lake.objects.CompiledObjectTypes;
import io.camunda.analytics.lake.sink.RowAppender;
import io.camunda.analytics.lake.state.TranslatorState;
import io.camunda.analytics.lake.state.TranslatorState.BirthQualifier;
import io.camunda.analytics.lake.state.TranslatorState.FlowEndpoints;
import io.camunda.analytics.lake.state.TranslatorState.LifecycleStatus;
import io.camunda.analytics.lake.state.TranslatorState.ObjectLifecycle;
import io.camunda.analytics.lake.state.TranslatorState.ObjectSighting;
import io.camunda.analytics.lake.state.TranslatorState.ObjectSightingList;
import io.camunda.analytics.lake.state.TranslatorState.OpenElement;
import io.camunda.analytics.lake.state.TranslatorState.OpenInstance;
import io.camunda.analytics.lake.state.TranslatorState.VariantAccumulator;
import io.camunda.analytics.lake.state.TranslatorState.VariantElementKind;
import io.camunda.analytics.lake.state.TranslatorState.VariantName;
import io.camunda.analytics.lake.translate.RawTableSchemas.ActivityColumns;
import io.camunda.analytics.lake.translate.RawTableSchemas.InstanceColumns;
import io.camunda.analytics.lake.translate.RawTableSchemas.InstanceLinkColumns;
import io.camunda.analytics.lake.translate.RawTableSchemas.ObjectColumns;
import io.camunda.analytics.lake.translate.RawTableSchemas.ObjectLifecycleColumns;
import io.camunda.analytics.lake.translate.RawTableSchemas.ObjectRelationColumns;
import io.camunda.analytics.lake.translate.RawTableSchemas.VariantColumns;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Folds Zeebe {@code EVENT} records into finished-row appends, holding open entities in {@link
 * TranslatorState} until they complete. Only two value types matter: {@code PROCESS_INSTANCE}
 * (activation opens an instance/element row, completion/termination emits it and evicts it) and
 * {@code VARIABLE}.
 *
 * <p>Three locked rules govern correctness:
 *
 * <ul>
 *   <li><b>Root scope only</b> — an instance's variable payload is exactly the variables whose
 *       scope key is the process instance key; subprocess-/element-scoped variables are ignored by
 *       design, not omission.
 *   <li><b>Last value wins</b> — a variable put overwrites the prior value for that name, so the
 *       instance row carries the values visible at completion time.
 *   <li><b>Evict after emit</b> — an entity is deleted from state the instant its finished row is
 *       appended; state holds only <em>open</em> entities. Replaying a completion whose entity is
 *       already evicted (expected after a crash-resume before the lake's committed offset) finds no
 *       open row and is skipped silently — the same finished row is already durable in the lake.
 * </ul>
 *
 * <p>The translator never flushes or triggers the L0 sink pipelines — the app loop owns flush (and
 * thus offset-commit) policy via {@code SinkPipeline#onPollTick}.
 *
 * <h2>Origin-position dedup</h2>
 *
 * <p>The Event Bridge's Zeebe exporter is at-least-once, not exactly-once: after a failover it can
 * re-export a tail of records that were already exported before the failover, under brand new Event
 * Bridge offsets — so the per-table offset stamp {@code IcebergLakeWriter}/{@code DirectCommitSink}
 * already carry (keyed by Event Bridge <em>source</em> partition) cannot catch this; only a
 * record's own Zeebe origin coordinates can. {@link #onRecord} therefore checks {@code
 * record.getPartitionId()}/{@code record.getPosition()} — the Zeebe partition and log position the
 * record actually came from, not the Event Bridge envelope's own {@code partitionId}/{@code offset}
 * (see {@code ZeebeRecord}'s own javadoc for the distinction) — against a per-Zeebe- partition high
 * watermark, <b>before</b> any state mutation or row emission: a position at or below the watermark
 * for its partition is dropped entirely (treated as successfully consumed, not backpressure — the
 * caller's Event Bridge offset still advances), never even reaching the {@link RecordType#EVENT}
 * check below it. An equal position is a duplicate, dropped the same way as a lower one.
 *
 * <p><b>The watermark only advances once the record is actually consumed.</b> The dedup check
 * itself is pure (no mutation); the watermark for the record's partition is raised to its position
 * only after folding the record has run and is about to report success back to the caller. This
 * split matters because of {@link #onRecord}'s own backpressure contract (see this class's
 * "Backpressure" section below): a row-emitting record can make {@code onRecord} return {@code
 * false} on ring backpressure, and the caller then retries the exact same record later. If the
 * watermark had already advanced past that record's position on the first (failed) attempt, the
 * retry would see its own position at-or-below the watermark and get dropped as a "duplicate" —
 * silently losing a row that was never actually appended. Advancing only on paths that return
 * {@code true} (folded successfully, ignored by design, or dropped by the dedup check itself — all
 * of which must not re-admit the same position on a later rewind) keeps a backpressure retry
 * indistinguishable from the record's first attempt.
 *
 * <p>A plain per-partition high watermark (rather than a full seen-set of positions) suffices
 * because positions within one Zeebe partition arrive in order except for exactly this one failure
 * mode — a rewind back to an already-passed position, never a genuinely new position arriving out
 * of order. So "highest position folded so far" is always enough to recognize a rewind's entire
 * redelivered tail as duplicate, with no need to remember individual positions.
 *
 * <p><b>Restart rule:</b> the watermark is seeded at startup (see {@link #seedWatermark}) from the
 * durable {@code lake.zbpos.z*} stamp the lake tables themselves carry (see {@code
 * IcebergLakeWriter#committedZeebeWatermark}'s javadoc for the min-across-tables cut rule), never
 * from this translator's own {@link TranslatorState} — that state tracks only currently open
 * entities, not a record of what has already been folded. Any record whose position lands above the
 * seeded watermark folds again on replay; that is correct, not a gap, because its row was never
 * made durable.
 *
 * <h2>Millis in, micros at the wire</h2>
 *
 * <p>{@link TranslatorState}/{@code OpenInstance}/{@code OpenElement} and every {@code
 * Record#getTimestamp()} this class reads stay in Zeebe's native epoch-<b>milli</b>seconds
 * throughout — {@link #millisToMicros} is the one and only place the ×1000 conversion to epoch
 * microseconds happens, right at the row-append boundary, for exactly the columns backing an
 * Iceberg {@code timestamptz} field ({@code started_at}/{@code ended_at}/{@code
 * instance_started_at} — see {@link RawTableSchemas}). {@code duration_ms} is a plain difference of
 * two (unconverted) millis values and is never touched by this conversion.
 *
 * <h2>Backpressure</h2>
 *
 * <p>Each Zeebe record touches at most one {@link RowAppender} (an instance-completion emits to
 * {@code instances}, an element-completion emits to {@code activities}, never both). {@link
 * #onRecord(ZeebeRecord)} returns {@code false} the moment a row append's {@link
 * RowAppender#begin()} reports backpressure (ring full) — per {@code RowAppender}'s own contract,
 * the caller must then retry the very same record later rather than drop it or advance past it.
 * This is safe to do by simply calling {@link #onRecord(ZeebeRecord)} again: everything that runs
 * before the failing {@code begin()} call (state puts, {@code state.getInstance}/{@code getElement}
 * lookups) is idempotent and side-effect-free until the append actually succeeds, and eviction
 * ({@code state.deleteInstance}/{@code deleteElement}) only happens after it does.
 *
 * <h2>Allocation: budgeted per completed instance, not per record</h2>
 *
 * <p>{@code RowAppender}'s own zero-allocation contract covers the per-record append path (the
 * {@code begin}/{@code put*}/{@code endRow} calls this class makes on every record). Building the
 * {@code vars_json} payload in {@link #emitInstance} does allocate — the JSON string, its UTF-8
 * byte array, and (see {@code RocksDbTranslatorState#variablesOf}) the drained variable map itself
 * — but only once per <em>completed</em> instance, never once per record. That is bounded by
 * completion rate, not by however many records it took to reach it, and is the one explicitly
 * budgeted exception to the sink's otherwise strict zero-allocation hot path (see {@code
 * io.camunda.analytics.lake.sink} package-info and {@code RowAppender}'s own javadoc).
 *
 * <h2>Variant capture (scheme variant-k1)</h2>
 *
 * <p><b>Set semantics</b>: while an instance is open, every distinct activated element id (any
 * element type except the {@code PROCESS} root itself — {@code MULTI_INSTANCE_BODY} is a normal
 * element, needing no special handling: its inner instances all share the body's own element id, so
 * repeats are absorbed by the seen-set below) and every distinct taken sequence flow id contributes
 * <b>at most once</b> to a running XOR-folded hash held in a {@link
 * TranslatorState.VariantAccumulator}, one per open instance (see {@link #foldVariant} for the fold
 * itself and {@link VariantHash} for the frozen hash/mix functions it uses). A revisited loop body
 * or re-taken flow re-mixes nothing — only first sight of a given id counts.
 *
 * <p><b>Replay guard (load-bearing, separate from the origin-position dedup gate above)</b>: each
 * accumulator carries its own {@code lastPosition}; a record at or below it skips folding entirely,
 * because XOR is not idempotent — re-mixing a replayed record would <em>cancel</em> its own prior
 * contribution, and re-inserting an already-seen id into the seen-set would double-count it. This
 * must be a second, per-accumulator guard rather than relying on the class's own partition-wide
 * {@link #zeebeWatermarks} gate: after a crash, this translator's {@link TranslatorState} (RocksDB)
 * may already be durably <em>ahead of</em> the lake's own committed cut (the two are not atomically
 * coupled), so replay from committed+1 can legitimately re-deliver records this accumulator already
 * folded, even though the class-level watermark gate (seeded from the lake's own committed
 * position) admits them as "new". {@code lastPosition} always advances on a fold that is not
 * skipped, whether or not the id itself turned out to be a repeat.
 *
 * <p>At completion, the accumulator's hash is <em>read</em>, never recomputed, hex-encoded (see
 * {@link VariantHash#toHex16}) onto the instance row's {@code variant_hash} column, and the
 * accumulator is evicted — mirroring the instance/element eviction pattern this class already uses
 * everywhere else. A per-process, per-id name map ({@link TranslatorState#putVariantName}/ {@link
 * TranslatorState#getVariantName}, written on first sight of a given id and cached on heap per
 * process in {@link #knownVariantNamesByProcess} to make repeat writes rare) lets completion decode
 * the accumulator's compact {@code seenHashes} back into sorted element/flow id strings for the
 * {@code variants} dictionary table — one row per distinct (process id, version, variant hash)
 * triple, emitted only on a per-translator seen-cache miss ({@link #variantDictionarySeenCache});
 * duplicates across restarts/partitions are expected and harmless (rows are deterministic given the
 * key). See {@link #emitVariantDictionaryRowIfNew} for that emission, and {@link
 * #variantsAppender}'s own field javadoc for why it may be {@code null}.
 *
 * <h2>Variable profiling</h2>
 *
 * <p>At the exact moment an instance completes — {@link #emitInstance}, after its row's {@code
 * endRow()} and state eviction, on the success path only (see that method's own ordering comment) —
 * every one of its final root variables folds once into {@link #profilesRider}: dims (process id,
 * variable name), classified by the JSON value's own first token into a type-mix counter
 * (number/string/boolean/null/object-or-array), plus — for a numeric value only — the parsed double
 * folded as the {@code value} measure. See {@link #foldVariableProfiles} for the fold itself. No
 * string variable <em>value</em> is ever persisted, only its classification and (when numeric) its
 * parsed number — a deliberate privacy boundary, not an oversight.
 *
 * <h2>Object fabric capture (OCPM: object types, sightings, links, relations)</h2>
 *
 * <p>Capture-only: this class recognizes declared {@link CompiledObjectType}s in the record stream
 * and lands four dictionary tables — {@code objects} (sightings), {@code instance_links}
 * (call-activity parent/child), {@code object_relations} (derived containment edges) — plus {@code
 * activities}' own {@code flow_scope_key} column (schema v4). Object <em>lifecycle</em>/metrics are
 * a follow-up lane's concern, not this one's; nothing here interprets what a sighting means beyond
 * recording it.
 *
 * <p><b>Sightings</b> (see {@link #foldObjectSightingFromVariable}/{@link
 * #onProcessMessageSubscriptionCorrelated}/{@link #onMessageStartEventSubscriptionCorrelated}) come
 * from three sources, each folded through the shared {@link #foldObjectSighting} core, deduped by
 * {@link #objectSightingSeenCache} per distinct (type, id, instance, scope) — duplicates are
 * harmless (deterministic content), mirroring the variant dictionary's own seen-cache judgment
 * call:
 *
 * <ul>
 *   <li><b>{@code VARIABLE CREATED}/{@code UPDATED}</b>, ALL scopes (deliberately not root-only —
 *       see {@link #onVariable}'s own note): a declared {@link
 *       io.camunda.analytics.lake.objects.IdentifierSource.VariableIdentifier} names the variable;
 *       its value sights only when it is a JSON scalar <b>string or number</b> (never boolean,
 *       null, object, or array — see {@link #scalarObjectId}), using the raw token unquoted as the
 *       object id. {@code scope_key} is the variable's own {@code getScopeKey()}, {@code null} when
 *       it equals the instance key (root).
 *   <li><b>{@code PROCESS_MESSAGE_SUBSCRIPTION CORRELATED}</b>: the single object type declaring
 *       {@link io.camunda.analytics.lake.objects.IdentifierSource.CorrelationKeyIdentifier}
 *       identity (see {@link CompiledObjectTypes#correlationKeyIdentifiedType}), if any, sights the
 *       correlated message's own correlation key. <b>Documented v1 imprecision</b>: the record
 *       carries the key's value but never which BPMN scope the catching element lives in (its own
 *       {@code elementInstanceKey} names the catching element itself, not a flow scope), so this
 *       sighting is conservatively recorded at root scope ({@code scope_key = null}) even when the
 *       true scope is a nested subprocess/multi-instance iteration — a correlation-identified
 *       object may in fact belong to a non-root scope this capture cannot see.
 *   <li><b>{@code MESSAGE_START_EVENT_SUBSCRIPTION CORRELATED}</b>: same declared type, same
 *       treatment, {@code scope_key = null} — here that is not an approximation: this event is what
 *       creates the process instance, so its correlation key genuinely belongs to that instance's
 *       root.
 * </ul>
 *
 * <p><b>Instance links</b> ({@link #emitInstanceLinkIfNew}): a root {@code ELEMENT_ACTIVATED} whose
 * {@code getParentProcessInstanceKey() > 0} is a call-activity child — this does <em>not</em> sight
 * an object (a child's own objects reach it through its own propagated variables), it emits one
 * {@code instance_links} row, deduped by {@link #instanceLinkSeenCache} keyed by the child instance
 * key alone (at most one parent per child, ever).
 *
 * <p><b>Object relations</b> ({@link #emitObjectRelationsAtCompletion}): derived at instance
 * completion from that instance's accumulated sightings (kept in {@link TranslatorState}'s {@code
 * OBJECT_SIGHTINGS} column family, capped at {@link #MAX_OBJECT_SIGHTINGS_PER_INSTANCE} with an
 * overflow flag, deleted on eviction like every other per-instance state). <b>v1 rule shipped</b>:
 * a relation (parent contains child) is emitted for every (root-scope sighting, non-root-scope
 * sighting) pair of the same instance whose (type, id) differ — the declared-parent-type variant
 * the design allowed skipping was skipped, for simplicity; root⊇non-root alone is the whole rule.
 * Deduped by {@link #objectRelationSeenCache} per distinct (parent type, parent id, child type,
 * child id) edge, across the whole translator lifetime, not just one instance.
 *
 * <p>Every new appender ({@link #objectsAppender}, {@link #instanceLinksAppender}, {@link
 * #objectRelationsAppender}) follows the variants dictionary's own backpressure judgment call:
 * {@code null} disables that table's emission entirely (detection/state bookkeeping still runs),
 * and a ring-full {@code begin()} is absorbed (seen-cache entry unmarked, record fold still reports
 * success) rather than propagated as {@link #onRecord}-level backpressure — these are small,
 * dedicated dictionary pipelines that must never hold back the primary instances/activities row.
 *
 * <h2>Object lifecycle capture</h2>
 *
 * <p><b>Working assumption (v1, stated here once for every method below that relies on it): an
 * object's instances are partition-local.</b> Nothing in this scheme cross-checks a sighting
 * against other Zeebe partitions. Where that assumption is violated (the same object id genuinely
 * spans more than one partition), a birth on each partition that first sights it can double-count
 * {@code objects_born}, and a closing instance only ever sees the sightings its own partition's
 * CF-7 list accumulated — this is documented, not defended against.
 *
 * <p>{@link CompiledObjectType#closingRules()} (see {@code io.camunda.analytics.lake.objects}'s own
 * package) optionally declares which process completions close a type's instances; a type with no
 * closing rule simply never emits a lifecycle fact (the "default-open" case — its objects stay
 * {@link LifecycleStatus#OPEN} forever as far as this scheme is concerned).
 *
 * <ul>
 *   <li><b>Birth</b> ({@link #foldObjectLifecycleSighting}, called from {@link #foldObjectSighting}
 *       right after {@link #recordObjectSightingForRelations} concludes — see that method's own
 *       return value): the very first time {@link TranslatorState#getObjectLifecycle} finds no
 *       accumulator for a (type, id), one is created {@link LifecycleStatus#OPEN} with {@code
 *       birthTsMs} = this sighting's own record timestamp, and the {@link #objectsBornRider} folds
 *       once. If an accumulator already exists — {@link LifecycleStatus#OPEN} or {@link
 *       LifecycleStatus#CLOSED_TOMBSTONE} — nothing is created and nothing is folded: this single
 *       presence check is what makes birth safe against both a replay whose {@link TranslatorState}
 *       is durably ahead of the lake's own committed cut (see class javadoc's "Origin-position
 *       dedup" section for the same replay exposure the CF-7 sighting list itself already has to
 *       defend against) and a late sighting arriving after the object has already closed. While
 *       {@link LifecycleStatus#OPEN}, {@code nSightings} increments once per sighting {@link
 *       #recordObjectSightingForRelations} itself accepted as a genuinely new CF-7 entry (reusing
 *       that method's own duplicate-scan, not a second one) — a duplicate sighting recounts
 *       nothing.
 *   <li><b>Closing</b> ({@link #emitObjectLifecycleClosingsIfDeclared}, called from {@link
 *       #emitInstance} — see that method's own javadoc for exactly where and why): when the
 *       completing instance's process is a declared closing process, every one of its CF-7
 *       sightings whose object type closes on it is checked against its lifecycle accumulator; an
 *       {@link LifecycleStatus#OPEN} one emits one {@code object_lifecycle} row (outcome from this
 *       completion's {@code finalState}, duration from birth to this completion) and flips to
 *       {@link LifecycleStatus#CLOSED_TOMBSTONE} — never deleted, so a later replayed sighting or a
 *       second closing instance recognizes the object is already accounted for and skips silently.
 *       Unlike every other object-fabric dictionary appender in this class, backpressure here is
 *       <b>not</b> absorbed — see that method's own javadoc for why a lifecycle fact cannot be
 *       treated as redundant the way a dictionary row can, and for why this is consequently the one
 *       object-fabric emission in {@code emitInstance} that runs before any mutation, not after.
 *   <li><b>Sweep</b> ({@code io.camunda.analytics.lake.LakePocApp}'s own housekeeping tick, via
 *       {@link TranslatorState#sweepObjectLifecycleTombstones}): tombstones are not kept forever —
 *       one older than {@code lake.objectTombstoneRetentionMs} is deleted outright, since by then
 *       it has done its job of blocking a re-birth/double-close for as long as configured.
 * </ul>
 *
 * <p>Known limitation, shared with every other state-mutating emission in this translator: a crash
 * between a lifecycle state write (the accumulator flip) and this pipeline's own descriptor commit
 * can lose that row's uncommitted window — systemic, not specific to lifecycle capture, and fixed
 * only by the checkpoint-at-cut work globally, not by anything local to this scheme (see this
 * class's own "Evict after emit" rule in the class javadoc's introduction for the same caveat
 * already accepted everywhere else).
 */
public final class LakeTranslator {

  /**
   * Variables whose JSON value exceeds this many characters are skipped (debug-logged): a guardrail
   * against a pathological blob bloating the instance row.
   */
  private static final int MAX_VARIABLE_VALUE_CHARS = 8192;

  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

  /**
   * Sentinel for "no record has folded yet for this Zeebe partition" in {@link #zeebeWatermarks}.
   */
  private static final long NO_WATERMARK = -1L;

  /**
   * Initial length of {@link #zeebeWatermarks}; grows rarely, see {@link #ensureWatermarkCapacity}.
   */
  private static final int INITIAL_WATERMARK_CAPACITY = 4;

  /**
   * A freshly-opened instance's variant accumulator has folded nothing yet -- see {@link
   * TranslatorState.VariantAccumulator}'s own javadoc.
   */
  private static final int[] EMPTY_SEEN_HASHES = new int[0];

  /**
   * Capacity of {@link #variantDictionarySeenCache} — a PoC-tuned guess (mirrors {@link
   * #MAX_VARIABLE_VALUE_CHARS}'s own precedent), not a measured production figure. A cache miss on
   * a (process id, version, variant hash) triple that was in fact already emitted (evicted from the
   * LRU, or from before a restart) only ever costs one harmless duplicate dictionary row — see this
   * class's "Variant capture" javadoc section.
   */
  private static final int VARIANT_DICTIONARY_CACHE_CAPACITY = 4096;

  /**
   * Capacity of {@link #objectSightingSeenCache} — mirrors {@link
   * #VARIANT_DICTIONARY_CACHE_CAPACITY}'s own precedent and the same "a false negative costs one
   * harmless duplicate write" reasoning (see this class's "Object fabric capture" javadoc section).
   */
  private static final int OBJECT_SIGHTING_SEEN_CACHE_CAPACITY = 4096;

  /**
   * Capacity of {@link #instanceLinkSeenCache} — see {@link #OBJECT_SIGHTING_SEEN_CACHE_CAPACITY}.
   */
  private static final int INSTANCE_LINK_SEEN_CACHE_CAPACITY = 4096;

  /**
   * Capacity of {@link #objectRelationSeenCache} — see {@link
   * #OBJECT_SIGHTING_SEEN_CACHE_CAPACITY}.
   */
  private static final int OBJECT_RELATION_SEEN_CACHE_CAPACITY = 4096;

  /**
   * Soft cap on the number of distinct object sightings tracked per open instance for relations
   * derivation (see {@link #recordObjectSightingForRelations}) — a PoC-tuned guess, deliberately
   * well below {@code ObjectSightingListValue}'s own 16-bit hard encoding ceiling. An instance that
   * sights more than this many distinct objects has its list frozen at the cap with the overflow
   * flag set: further sightings are dropped from the relations-derivation list (not from the {@code
   * objects} dictionary table, which is uncapped and keeps recording every sighting regardless).
   * Chosen small (relative to the variant dictionary's own 65535-entry technical ceiling) because
   * relations derivation is O(roots × non-roots) per completed instance (see {@link
   * #emitObjectRelationsAtCompletion}) — worst case {@code MAX_OBJECT_SIGHTINGS_PER_INSTANCE}
   * squared pairs considered on one instance's completion, budgeted per-completion like the variant
   * dictionary decode already is.
   */
  private static final int MAX_OBJECT_SIGHTINGS_PER_INSTANCE = 64;

  /**
   * Guardrail against a pathological blob masquerading as a scalar identifier value (mirrors {@link
   * #MAX_VARIABLE_VALUE_CHARS}'s own precedent, just far smaller — real object ids are short).
   */
  private static final int MAX_OBJECT_ID_CHARS = 512;

  private static final String OBJECT_QUALIFIER_VARIABLE = "VARIABLE";
  private static final String OBJECT_QUALIFIER_MESSAGE = "MESSAGE";
  private static final String OBJECT_QUALIFIER_MESSAGE_START = "MESSAGE_START";

  private static final String INSTANCE_LINK_TYPE_CALL_ACTIVITY = "CALL_ACTIVITY";

  private static final Logger LOG = LoggerFactory.getLogger(LakeTranslator.class);

  // ---- poll-fed metrics dim indices (see PollFedRider's own class javadoc) ----
  private static final int FLOW_DIM_PROCESS_ID = 0;
  private static final int FLOW_DIM_VERSION = 1;
  private static final int FLOW_DIM_FLOW_ID = 2;
  private static final int FLOW_DIM_SOURCE_ELEMENT_ID = 3;
  private static final int FLOW_DIM_TARGET_ELEMENT_ID = 4;
  private static final int STARTED_DIM_PROCESS_ID = 0;
  private static final int STARTED_DIM_VERSION = 1;
  private static final int PROFILE_DIM_PROCESS_ID = 0;
  private static final int PROFILE_DIM_VAR_NAME = 1;
  private static final int PROFILE_MEASURE_VALUE = 0;
  private static final int OBJECT_BORN_DIM_OBJECT_TYPE = 0;

  // ---- variable-profile named counters (declaration order -- see LakePocApp's own entity
  // declaration and PollFedRider#incrementCounter's own javadoc) ----
  private static final int PROFILE_COUNTER_NUMBER = 0;
  private static final int PROFILE_COUNTER_STRING = 1;
  private static final int PROFILE_COUNTER_BOOLEAN = 2;
  private static final int PROFILE_COUNTER_NULL = 3;
  private static final int PROFILE_COUNTER_OBJECT_OR_ARRAY = 4;

  private final TranslatorState state;
  private final RowAppender instanceAppender;
  private final RowAppender activityAppender;

  /**
   * The variants dictionary table's {@link RowAppender}, or {@code null} when the dictionary
   * pipeline is not wired (e.g. an existing 3-arg-constructed translator, or a test exercising only
   * the raw instances/activities path). {@code null} disables dictionary-row emission entirely
   * ({@link #emitVariantDictionaryRowIfNew} becomes a no-op) — it never affects the instances row's
   * own {@code variant_hash} column, which is always populated from the accumulator regardless (see
   * {@link #emitInstance}).
   */
  private final RowAppender variantsAppender;

  /**
   * variant-k1 name-map write-avoidance cache: {@code bpmnProcessId -> every h32 already known to
   * be recorded in the durable name map} (see {@link TranslatorState#putVariantName}). Bounded by
   * the number of distinct processes times each process's own distinct element/flow count — small
   * and stable in practice, unlike {@link #variantDictionarySeenCache} this is never evicted: a
   * false negative here would cost only a redundant (idempotent) durable write, not a correctness
   * bug, so an unbounded cache is the simpler and cheaper choice for this specific purpose.
   */
  private final Map<String, Set<Integer>> knownVariantNamesByProcess = new HashMap<>();

  /** See {@link VariantDictionarySeenCache}'s own javadoc. */
  private final VariantDictionarySeenCache variantDictionarySeenCache =
      new VariantDictionarySeenCache();

  /**
   * Folds sequence-flow-taken events into branch counts, {@code null} when this translator isn't
   * wired for it (e.g. a test exercising unrelated behavior) — see this class's "Poll-fed metrics"
   * section below and {@code io.camunda.analytics.lake.metrics.PollFedRider}'s own class javadoc.
   */
  private final PollFedRider flowCountsRider;

  /** Folds process-instance-started events into started counters — see {@link #flowCountsRider}. */
  private final PollFedRider startedCountsRider;

  /**
   * Folds each completed instance's final root variables into per-(process id, variable name,
   * window) profiles — see this class's "Variable profiling" section for the fold itself, and
   * {@code io.camunda.analytics.lake.metrics.PollFedRider}'s own class javadoc for the rider
   * mechanics. {@code null} disables profiling entirely (e.g. a test exercising unrelated
   * behavior), mirroring {@link #flowCountsRider}/{@link #startedCountsRider}'s own optionality.
   */
  private final PollFedRider profilesRider;

  /**
   * The {@code objects} sightings dictionary table's {@link RowAppender}, or {@code null} to
   * disable that table's row emission (detection/CF-7 bookkeeping still runs regardless) — see this
   * class's "Object fabric capture" javadoc section and {@link #variantsAppender}'s own precedent
   * for what {@code null} means here.
   */
  private final RowAppender objectsAppender;

  /**
   * The {@code instance_links} dictionary table's {@link RowAppender} — see {@link
   * #objectsAppender}.
   */
  private final RowAppender instanceLinksAppender;

  /**
   * The {@code object_relations} dictionary table's {@link RowAppender} — see {@link
   * #objectsAppender}.
   */
  private final RowAppender objectRelationsAppender;

  /**
   * The validated registry of every declared {@link CompiledObjectType}, or {@code null} to disable
   * object-sighting detection entirely (call-activity instance links are unaffected — they do not
   * depend on any object-type declaration). See this class's "Object fabric capture" javadoc
   * section.
   */
  private final CompiledObjectTypes objectTypes;

  /** See this class's "Object fabric capture" javadoc section. */
  private final BoundedSeenCache<ObjectSightingKey> objectSightingSeenCache =
      new BoundedSeenCache<>(OBJECT_SIGHTING_SEEN_CACHE_CAPACITY);

  /** See this class's "Object fabric capture" javadoc section. */
  private final BoundedSeenCache<Long> instanceLinkSeenCache =
      new BoundedSeenCache<>(INSTANCE_LINK_SEEN_CACHE_CAPACITY);

  /** See this class's "Object fabric capture" javadoc section. */
  private final BoundedSeenCache<ObjectRelationKey> objectRelationSeenCache =
      new BoundedSeenCache<>(OBJECT_RELATION_SEEN_CACHE_CAPACITY);

  /**
   * The {@code object_lifecycle} fact table's {@link RowAppender}, or {@code null} to disable
   * closing-emission entirely (birth/{@code nSightings} bookkeeping in {@link TranslatorState}
   * still runs regardless — see this class's "Object lifecycle capture" javadoc section). Unlike
   * {@link #objectsAppender} and its siblings, {@code null} here also skips the closing-candidate
   * scan itself (not just the row write): with no appender to write to, there is nothing this class
   * could safely do with a would-be candidate other than leave its accumulator {@link
   * LifecycleStatus#OPEN} exactly as it already is.
   */
  private final RowAppender objectLifecycleAppender;

  /**
   * Folds an object's first-ever sighting into a birth counter — see this class's "Object lifecycle
   * capture" javadoc section and {@link PollFedRider}'s own class javadoc for the poll-fed rider
   * mechanics. {@code null} disables birth counting only; the lifecycle accumulator itself is still
   * created regardless (birth counting is a metric derived from it, not a precondition for it).
   */
  private final PollFedRider objectsBornRider;

  /**
   * Heap cache of {@link #onProcess}'s own persisted state, warmed two ways: eagerly, in full, the
   * moment a definition's own {@code PROCESS}/{@code CREATED} record is folded; lazily, one flow at
   * a time, on a cache miss in {@link #resolveFlowEndpoints} (the path a restart takes — the
   * deployment record itself is never replayed past this translator's bootstrap offset, so only the
   * durable {@link TranslatorState} store, not this cache, survives a restart). Poll thread only,
   * like every other field on this class; {@code null} inner maps are never stored — a definition
   * either has an entry (created on first touch) or doesn't.
   */
  private final Map<Long, Map<String, FlowEndpoints>> flowEndpointsCache = new HashMap<>();

  // Reused across every completed instance's vars_json build (see class javadoc's allocation
  // note) -- poll thread only, like everything else in this class; setLength(0) per use rather
  // than allocating a fresh StringBuilder per completed instance.
  private final StringBuilder varsJsonScratch = new StringBuilder(256);

  /**
   * Highest Zeebe {@code position} folded so far, per Zeebe {@code partitionId} — see class
   * javadoc's "Origin-position dedup" section. Index = partitionId; grows (rarely — partition ids
   * are small, dense integers) via {@link #ensureWatermarkCapacity}. Poll thread only, like every
   * other field on this class.
   */
  private long[] zeebeWatermarks;

  public LakeTranslator(
      final TranslatorState state,
      final RowAppender instanceAppender,
      final RowAppender activityAppender) {
    this(state, instanceAppender, activityAppender, null, null, null, null);
  }

  /**
   * Same as the 3-arg constructor, additionally wiring {@code variantsAppender} — see that field's
   * own javadoc for what {@code null} means and why every pre-existing 3-arg caller keeps working
   * unchanged.
   */
  public LakeTranslator(
      final TranslatorState state,
      final RowAppender instanceAppender,
      final RowAppender activityAppender,
      final RowAppender variantsAppender) {
    this(state, instanceAppender, activityAppender, variantsAppender, null, null, null);
  }

  /**
   * Same as the 3-arg constructor, additionally wiring the two poll-fed metrics riders — see {@link
   * #flowCountsRider}/{@link #startedCountsRider}. Either (or both) may be {@code null} to disable
   * that metric family without disturbing anything else (existing tests that only exercise raw-row
   * folding use the 3-arg constructor and never touch either rider).
   */
  public LakeTranslator(
      final TranslatorState state,
      final RowAppender instanceAppender,
      final RowAppender activityAppender,
      final PollFedRider flowCountsRider,
      final PollFedRider startedCountsRider) {
    this(
        state, instanceAppender, activityAppender, null, flowCountsRider, startedCountsRider, null);
  }

  /**
   * Same as the 7-arg constructor (raw-row appenders plus every optional metric hook), with every
   * object-fabric capture hook ({@link #objectsAppender}, {@link #instanceLinksAppender}, {@link
   * #objectRelationsAppender}, {@link #objectTypes}) disabled — kept so every pre-existing 7-arg
   * caller (and, transitively, every shorter overload above) keeps compiling and behaving
   * unchanged. See the 11-arg constructor below for the full picture.
   */
  public LakeTranslator(
      final TranslatorState state,
      final RowAppender instanceAppender,
      final RowAppender activityAppender,
      final RowAppender variantsAppender,
      final PollFedRider flowCountsRider,
      final PollFedRider startedCountsRider,
      final PollFedRider profilesRider) {
    this(
        state,
        instanceAppender,
        activityAppender,
        variantsAppender,
        flowCountsRider,
        startedCountsRider,
        profilesRider,
        null,
        null,
        null,
        null);
  }

  /**
   * Same as the 11-arg constructor below, with both object-lifecycle hooks ({@link
   * #objectLifecycleAppender}, {@link #objectsBornRider}) disabled — kept so every pre-existing
   * 11-arg caller (and, transitively, every shorter overload above) keeps compiling and behaving
   * unchanged. See the 13-arg constructor below for the full picture.
   */
  public LakeTranslator(
      final TranslatorState state,
      final RowAppender instanceAppender,
      final RowAppender activityAppender,
      final RowAppender variantsAppender,
      final PollFedRider flowCountsRider,
      final PollFedRider startedCountsRider,
      final PollFedRider profilesRider,
      final RowAppender objectsAppender,
      final RowAppender instanceLinksAppender,
      final RowAppender objectRelationsAppender,
      final CompiledObjectTypes objectTypes) {
    this(
        state,
        instanceAppender,
        activityAppender,
        variantsAppender,
        flowCountsRider,
        startedCountsRider,
        profilesRider,
        objectsAppender,
        instanceLinksAppender,
        objectRelationsAppender,
        objectTypes,
        null,
        null);
  }

  /**
   * The full constructor: every raw-row appender, every optional metric hook, every object-fabric
   * capture hook, and the two object-lifecycle hooks ({@link #objectLifecycleAppender}, {@link
   * #objectsBornRider}). Any of the thirteen may be {@code null} (where nullable) to disable that
   * capture without disturbing the others.
   */
  public LakeTranslator(
      final TranslatorState state,
      final RowAppender instanceAppender,
      final RowAppender activityAppender,
      final RowAppender variantsAppender,
      final PollFedRider flowCountsRider,
      final PollFedRider startedCountsRider,
      final PollFedRider profilesRider,
      final RowAppender objectsAppender,
      final RowAppender instanceLinksAppender,
      final RowAppender objectRelationsAppender,
      final CompiledObjectTypes objectTypes,
      final RowAppender objectLifecycleAppender,
      final PollFedRider objectsBornRider) {
    this.state = state;
    this.instanceAppender = instanceAppender;
    this.activityAppender = activityAppender;
    this.variantsAppender = variantsAppender;
    this.flowCountsRider = flowCountsRider;
    this.startedCountsRider = startedCountsRider;
    this.profilesRider = profilesRider;
    this.objectsAppender = objectsAppender;
    this.instanceLinksAppender = instanceLinksAppender;
    this.objectRelationsAppender = objectRelationsAppender;
    this.objectTypes = objectTypes;
    this.objectLifecycleAppender = objectLifecycleAppender;
    this.objectsBornRider = objectsBornRider;
    zeebeWatermarks = newWatermarkArray(INITIAL_WATERMARK_CAPACITY);
  }

  /**
   * @return {@code false} if a row append hit backpressure (ring full) — the caller must retry this
   *     same {@code zr} later instead of advancing past it (see class javadoc); the origin-position
   *     dedup watermark is deliberately NOT advanced on this path, so the retry is admitted exactly
   *     like the record's first attempt. {@code true} otherwise, including when the record needed
   *     no row append at all, or was dropped by the origin-position dedup gate below — every {@code
   *     true} path advances the watermark past this record's position (see class javadoc's
   *     "Origin-position dedup" section for why that must include the dropped-by-type/ignored paths
   *     too, not just a successful fold)
   */
  public boolean onRecord(final ZeebeRecord zr) {
    final Record<?> record = zr.record();
    final int zeebePartitionId = record.getPartitionId();
    final long position = record.getPosition();
    if (isDuplicateOrRewound(zeebePartitionId, position)) {
      // See class javadoc's "Origin-position dedup" section: a redelivered (or otherwise
      // already-folded) Zeebe position is dropped before any state mutation or row emission, but
      // still counts as successfully consumed -- the caller's Event Bridge offset still advances.
      // No watermark write here: the check above already read it, and it is already at or above
      // this position.
      return true;
    }
    final boolean consumed = fold(record);
    if (consumed) {
      // Only now, once the record has actually been folded (or determined to need no folding) and
      // is about to report success -- never on the return-false backpressure path, or a retry of
      // this same record would be misread as a duplicate of itself. See class javadoc for why this
      // ordering is load-bearing.
      advanceWatermark(zeebePartitionId, position);
    }
    return consumed;
  }

  /**
   * The actual record fold, run only once {@link #onRecord} has confirmed {@code record}'s origin
   * position is not a duplicate. Pulled out of {@link #onRecord} so the watermark advance there can
   * sit strictly after this returns, gated on its result.
   *
   * @return {@code false} if a row append hit backpressure (ring full); {@code true} otherwise —
   *     see {@link #onRecord}'s own javadoc for the full contract
   */
  private boolean fold(final Record<?> record) {
    if (record.getRecordType() != RecordType.EVENT) {
      return true;
    }
    final ValueType valueType = record.getValueType();
    if (valueType == ValueType.PROCESS_INSTANCE) {
      return onProcessInstance(record);
    } else if (valueType == ValueType.VARIABLE) {
      onVariable(record);
    } else if (valueType == ValueType.PROCESS) {
      // Deployment metadata, not instance data -- never touches a RowAppender, so it never reports
      // backpressure; see #onProcess's own javadoc for what this feeds.
      onProcess(record);
    } else if (valueType == ValueType.PROCESS_MESSAGE_SUBSCRIPTION
        && record.getIntent() == ProcessMessageSubscriptionIntent.CORRELATED) {
      // Object fabric capture (see class javadoc): backpressure on this path is absorbed, never
      // propagated -- see #onProcessMessageSubscriptionCorrelated's own callees.
      onProcessMessageSubscriptionCorrelated(record);
    } else if (valueType == ValueType.MESSAGE_START_EVENT_SUBSCRIPTION
        && record.getIntent() == MessageStartEventSubscriptionIntent.CORRELATED) {
      onMessageStartEventSubscriptionCorrelated(record);
    }
    return true;
  }

  /**
   * The origin-position dedup gate's read side — see class javadoc's "Origin-position dedup"
   * section. Pure: grows {@link #zeebeWatermarks} to admit {@code zeebePartitionId} if needed, but
   * never writes a watermark value itself (see {@link #advanceWatermark} for that half) — {@link
   * #onRecord} must be able to call this before running {@code record}'s fold and only commit the
   * new watermark value after the fold succeeds.
   *
   * @return {@code true} if {@code position} is at or below the watermark already recorded for
   *     {@code zeebePartitionId} (a duplicate or an already-passed rewind) and must be dropped
   *     entirely; {@code false} if it is new
   */
  private boolean isDuplicateOrRewound(final int zeebePartitionId, final long position) {
    ensureWatermarkCapacity(zeebePartitionId);
    return position <= zeebeWatermarks[zeebePartitionId];
  }

  /**
   * The origin-position dedup gate's write side — see {@link #isDuplicateOrRewound} for the read
   * side and class javadoc for why the two are split. Assumes {@code zeebePartitionId} has already
   * been admitted by {@link #isDuplicateOrRewound} in the same {@link #onRecord} call (so capacity
   * is already ensured) and that {@code position} is strictly greater than the current watermark.
   */
  private void advanceWatermark(final int zeebePartitionId, final long position) {
    zeebeWatermarks[zeebePartitionId] = position;
  }

  /**
   * An immutable point-in-time copy of every Zeebe partition's dedup watermark this translator has
   * observed so far, keyed by {@code partitionId}. Intended to be called once per poll-loop tick
   * (see {@code LakePocApp}'s per-partition {@code onPollTick} wiring and {@code
   * io.camunda.analytics.lake.sink.pipeline.SealSnapshot}'s own capture of it), never once per
   * record — the allocation here is bounded by poll frequency, the same budget the seal snapshot's
   * own offset/frontier capture already spends.
   */
  public Map<Integer, Long> watermarkSnapshot() {
    final Map<Integer, Long> snapshot = new LinkedHashMap<>();
    for (int partitionId = 0; partitionId < zeebeWatermarks.length; partitionId++) {
      final long watermark = zeebeWatermarks[partitionId];
      if (watermark != NO_WATERMARK) {
        snapshot.put(partitionId, watermark);
      }
    }
    return Map.copyOf(snapshot);
  }

  /**
   * Seeds this translator's watermark for Zeebe partition {@code zeebePartitionId} at process
   * startup, from the durable {@code lake.zbpos.z*} stamp {@code IcebergLakeWriter} reports for it
   * (see {@code IcebergLakeWriter#committedZeebeWatermark}'s javadoc for the min-across-tables cut
   * rule) — never from this translator's own {@link TranslatorState}, which holds only currently
   * open entities, not a record of what has already folded (see class javadoc's "Restart rule").
   * Must be called before this translator processes its first record for {@code zeebePartitionId}:
   * seeding a translator that has already folded live records for that partition would let the
   * watermark regress and reopen the dedup window the gate exists to close, so this only ever
   * raises the watermark (never lowers it).
   */
  public void seedWatermark(final int zeebePartitionId, final long position) {
    ensureWatermarkCapacity(zeebePartitionId);
    zeebeWatermarks[zeebePartitionId] = Math.max(zeebeWatermarks[zeebePartitionId], position);
  }

  /**
   * Grows {@link #zeebeWatermarks} (rarely — partition ids are small, dense integers) to admit
   * index {@code zeebePartitionId}, doubling capacity (or exactly enough, whichever is larger); new
   * slots default to {@link #NO_WATERMARK}, matching every existing slot's own initial value.
   */
  private void ensureWatermarkCapacity(final int zeebePartitionId) {
    if (zeebePartitionId < zeebeWatermarks.length) {
      return;
    }
    final long[] grown =
        newWatermarkArray(Math.max(zeebePartitionId + 1, zeebeWatermarks.length * 2));
    System.arraycopy(zeebeWatermarks, 0, grown, 0, zeebeWatermarks.length);
    zeebeWatermarks = grown;
  }

  private static long[] newWatermarkArray(final int length) {
    final long[] array = new long[length];
    Arrays.fill(array, NO_WATERMARK);
    return array;
  }

  private boolean onProcessInstance(final Record<?> record) {
    final ProcessInstanceRecordValue value = (ProcessInstanceRecordValue) record.getValue();
    final long timestamp = record.getTimestamp();
    final long processInstanceKey = value.getProcessInstanceKey();
    final long elementInstanceKey = record.getKey();
    // The process instance is the root element; its element instance key == the process instance
    // key.
    final boolean root = value.getBpmnElementType() == BpmnElementType.PROCESS;

    if (record.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED) {
      if (root) {
        state.putInstance(
            processInstanceKey,
            new OpenInstance(
                value.getProcessDefinitionKey(),
                value.getBpmnProcessId(),
                value.getVersion(),
                value.getTenantId(),
                timestamp));
        // variant-k1: a fresh accumulator per OPEN instance -- see class javadoc's "Variant
        // capture" section. Nothing folded yet: lastPosition sits below any legitimate position.
        state.putVariantAccumulator(
            processInstanceKey, new VariantAccumulator(-1L, 0L, 0, EMPTY_SEEN_HASHES));
        // ---- poll-fed metrics: started counters (see PollFedRider's own class javadoc) ----
        // A brand-new instance, right at activation, produces no raw row of its own (that only
        // happens on completion/termination) -- fold it into the started-count entity directly.
        if (startedCountsRider != null) {
          startedCountsRider
              .putDict(STARTED_DIM_PROCESS_ID, value.getBpmnProcessId())
              .putInt(STARTED_DIM_VERSION, value.getVersion());
          startedCountsRider.fold(millisToMicros(timestamp));
        }
        // ---- end poll-fed metrics: started counters ----
        // Object fabric capture: a call-activity child's root activation emits an instance LINK,
        // never an object sighting -- see class javadoc's "Object fabric capture" section.
        if (value.getParentProcessInstanceKey() > 0) {
          emitInstanceLinkIfNew(
              value.getParentProcessInstanceKey(),
              processInstanceKey,
              value.getParentElementInstanceKey(),
              timestamp);
        }
      } else {
        final OpenInstance owner = state.getInstance(processInstanceKey);
        // Replay edge where the owner is unknown (e.g. resuming past the instance's own evict but
        // before this element's) -- the element's own timestamp is the best available family date.
        final long instanceStartMs = owner != null ? owner.startMs() : timestamp;
        state.putElement(
            elementInstanceKey,
            new OpenElement(
                processInstanceKey,
                value.getBpmnProcessId(),
                value.getVersion(),
                value.getTenantId(),
                value.getElementId(),
                value.getBpmnElementType().name(),
                timestamp,
                instanceStartMs));
        // variant-k1: every activated element except the PROCESS root (this branch) contributes,
        // MULTI_INSTANCE_BODY included as a normal element -- see class javadoc.
        foldVariant(
            processInstanceKey,
            value.getBpmnProcessId(),
            value.getElementId(),
            VariantElementKind.ELEMENT,
            record.getPosition());
      }
      return true;
    }

    if (record.getIntent() == ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN) {
      // variant-k1: a taken sequence flow contributes exactly like an element activation, keyed by
      // its own id (ProcessInstanceRecordValue#getElementId() is the flow's id for this intent) --
      // see class javadoc. No other state mutation for this intent.
      foldVariant(
          processInstanceKey,
          value.getBpmnProcessId(),
          value.getElementId(),
          VariantElementKind.FLOW,
          record.getPosition());
      // ---- poll-fed metrics: branch counts (see PollFedRider's own class javadoc) ----
      // A sequence-flow-taken event produces no raw row either -- resolve the flow's source/target
      // element ids (parsed once from the definition's deployed BPMN, see #onProcess) and fold it
      // into the branch-count entity directly.
      if (flowCountsRider != null) {
        final String flowId = value.getElementId();
        final FlowEndpoints endpoints =
            resolveFlowEndpoints(value.getProcessDefinitionKey(), flowId);
        flowCountsRider
            .putDict(FLOW_DIM_PROCESS_ID, value.getBpmnProcessId())
            .putInt(FLOW_DIM_VERSION, value.getVersion())
            .putDict(FLOW_DIM_FLOW_ID, flowId)
            .putDict(
                FLOW_DIM_SOURCE_ELEMENT_ID, endpoints == null ? null : endpoints.sourceElementId())
            .putDict(
                FLOW_DIM_TARGET_ELEMENT_ID, endpoints == null ? null : endpoints.targetElementId());
        flowCountsRider.fold(millisToMicros(timestamp));
      }
      // ---- end poll-fed metrics: branch counts ----
      return true;
    }

    final String finalState = finalStateOf(record.getIntent());
    if (finalState == null) {
      return true; // an intent other than COMPLETED/TERMINATED
    }
    if (root) {
      return emitInstance(processInstanceKey, timestamp, finalState);
    } else {
      return emitElement(elementInstanceKey, timestamp, finalState, value.getFlowScopeKey());
    }
  }

  private boolean emitElement(
      final long elementInstanceKey,
      final long timestamp,
      final String finalState,
      final long flowScopeKey) {
    final OpenElement element = state.getElement(elementInstanceKey);
    if (element == null) {
      return true; // replay past evict — expected, not an error
    }
    if (!activityAppender.begin()) {
      return false; // ring full — caller must retry this same record
    }
    activityAppender
        .putLong(ActivityColumns.INSTANCE_KEY, element.instanceKey())
        .putDict(ActivityColumns.PROCESS_ID, element.processId())
        .putInt(ActivityColumns.VERSION, element.version())
        .putDict(ActivityColumns.TENANT_ID, element.tenantId())
        .putDict(ActivityColumns.ELEMENT_ID, element.elementId())
        .putDict(ActivityColumns.ELEMENT_TYPE, element.elementType())
        .putLong(ActivityColumns.ELEMENT_KEY, elementInstanceKey)
        .putDict(ActivityColumns.STATE, finalState)
        .putLong(ActivityColumns.STARTED_AT, millisToMicros(element.startMs()))
        .putLong(ActivityColumns.ENDED_AT, millisToMicros(timestamp))
        .putLong(ActivityColumns.DURATION_MS, timestamp - element.startMs())
        .putLong(ActivityColumns.INSTANCE_STARTED_AT, millisToMicros(element.instanceStartMs()));
    // Schema v4 -- NULL when the element's own flow scope is its owning instance (a top-level
    // element's immediate parent scope is the root), see RawTableSchemas#activities's own javadoc.
    if (flowScopeKey == element.instanceKey()) {
      activityAppender.putNull(ActivityColumns.FLOW_SCOPE_KEY);
    } else {
      activityAppender.putLong(ActivityColumns.FLOW_SCOPE_KEY, flowScopeKey);
    }
    activityAppender.endRow();
    state.deleteElement(elementInstanceKey);
    return true;
  }

  private boolean emitInstance(
      final long processInstanceKey, final long timestamp, final String finalState) {
    final OpenInstance instance = state.getInstance(processInstanceKey);
    if (instance == null) {
      return true; // replay past evict — expected, not an error
    }
    // Object lifecycle capture: closing -- runs BEFORE any mutation for this record (including the
    // primary row's own instanceAppender.begin() below), not after endRow() like every other
    // object-fabric emission in this class -- see #emitObjectLifecycleClosingsIfDeclared's own
    // javadoc and class javadoc's "Object lifecycle capture" section for why that positioning is
    // load-bearing here.
    if (!emitObjectLifecycleClosingsIfDeclared(
        processInstanceKey, instance.processId(), timestamp, finalState)) {
      return false; // ring full on the lifecycle fact appender -- caller must retry this record
    }
    if (!instanceAppender.begin()) {
      return false; // ring full — caller must retry this same record
    }
    // Captured once and reused for both vars_json and the variable-profile fold below (see this
    // class's "Variable profiling" section) -- state.variablesOf is a RocksDB scan, so reading it
    // twice would double that cost for no reason.
    final Map<String, String> variables = state.variablesOf(processInstanceKey);
    final byte[] varsJson = varsJson(variables).getBytes(StandardCharsets.UTF_8);
    // variant-k1: read (never recompute) the accumulator's hash -- see class javadoc's "Variant
    // capture" section. null when state loss left no live accumulator; the column is nullable
    // exactly for this case.
    final VariantAccumulator variantAccumulator = state.getVariantAccumulator(processInstanceKey);
    final String variantHashHex =
        variantAccumulator != null ? VariantHash.toHex16(variantAccumulator.hash()) : null;
    instanceAppender
        .putLong(InstanceColumns.KEY, processInstanceKey)
        .putLong(InstanceColumns.PROCESS_DEFINITION_KEY, instance.processDefinitionKey())
        .putDict(InstanceColumns.PROCESS_ID, instance.processId())
        .putInt(InstanceColumns.VERSION, instance.version())
        .putDict(InstanceColumns.TENANT_ID, instance.tenantId())
        .putDict(InstanceColumns.STATE, finalState)
        .putLong(InstanceColumns.STARTED_AT, millisToMicros(instance.startMs()))
        .putLong(InstanceColumns.ENDED_AT, millisToMicros(timestamp))
        .putLong(InstanceColumns.DURATION_MS, timestamp - instance.startMs())
        .putBinary(InstanceColumns.VARS_JSON, varsJson, 0, varsJson.length);
    if (variantHashHex != null) {
      instanceAppender.putDict(InstanceColumns.VARIANT_HASH, variantHashHex);
    } else {
      instanceAppender.putNull(InstanceColumns.VARIANT_HASH);
    }
    instanceAppender.endRow();
    state.deleteInstance(processInstanceKey);
    state.deleteVariablesOf(processInstanceKey);
    // Variable profiling: sits strictly after endRow() (the row is already durable-bound to this
    // segment), never before -- see this class's "Variable profiling" section for why a
    // backpressure retry of this exact record must never double-fold.
    if (profilesRider != null) {
      foldVariableProfiles(instance.processId(), variables, timestamp);
    }
    if (variantAccumulator != null) {
      state.deleteVariantAccumulator(processInstanceKey);
      emitVariantDictionaryRowIfNew(instance, variantAccumulator, variantHashHex, timestamp);
    }
    // Object fabric capture: derive and emit this instance's relations from its accumulated
    // sightings, then evict the sighting list -- see class javadoc's "Object fabric capture"
    // section. Runs regardless of whether object-fabric capture is wired at all (a no-op then).
    emitObjectRelationsAtCompletion(processInstanceKey, timestamp);
    return true;
  }

  private void onVariable(final Record<?> record) {
    if (record.getIntent() != VariableIntent.CREATED
        && record.getIntent() != VariableIntent.UPDATED) {
      return;
    }
    final VariableRecordValue value = (VariableRecordValue) record.getValue();
    // Object fabric capture: ALL scopes, deliberately independent of the root-only rule below --
    // see class javadoc's "Object fabric capture" section for why sightings differ from vars_json.
    foldObjectSightingFromVariable(record, value);
    // Root scope only: a variable belongs to the instance row only when its scope is the process
    // instance itself. Non-root (subprocess-/element-scoped) variables are ignored by design.
    if (value.getScopeKey() != value.getProcessInstanceKey()) {
      return;
    }
    final String valueJson = value.getValue();
    if (valueJson.length() > MAX_VARIABLE_VALUE_CHARS) {
      LOG.debug(
          "Skipping oversized variable '{}' ({} chars) on instance {}",
          value.getName(),
          valueJson.length(),
          value.getProcessInstanceKey());
      return;
    }
    state.putVariable(value.getProcessInstanceKey(), value.getName(), valueJson);
  }

  /**
   * Resolves every sequence flow's source/target element ids from a newly deployed process
   * definition's own BPMN resource, and persists them — see {@link #flowCountsRider}'s own "branch
   * counts" fold, which is the only reader. Parsing happens once per deployment record (rare); the
   * per-flow-record hot path ({@link #resolveFlowEndpoints}) never parses anything, only looks up
   * already-resolved values.
   *
   * <p>Deliberately unscoped to just this record's own process: a BPMN resource may define more
   * than one process, and {@code sequenceFlow} {@code id}s are unique across an entire BPMN 2.0 XML
   * document (an XML Schema {@code ID} attribute), so extracting every flow in the resource once
   * per contained process is redundant across processes sharing one file, never incorrect — every
   * process's own {@link #flowEndpointsCache} entry ends up with the exactly correct answer for
   * every flow id it will ever be asked to resolve.
   *
   * <p>A resource that fails to parse (corrupt, or not actually BPMN) is logged and skipped rather
   * than failing the whole fold: every flow of that definition then resolves as unknown ({@code
   * null}) forever, which is the same honest answer {@link #resolveFlowEndpoints} already gives for
   * a definition whose deployment record was never seen at all.
   */
  private void onProcess(final Record<?> record) {
    if (record.getIntent() != ProcessIntent.CREATED) {
      return;
    }
    final Process value = (Process) record.getValue();
    final long processDefinitionKey = value.getProcessDefinitionKey();
    final BpmnModelInstance model;
    try {
      model = Bpmn.readModelFromStream(new ByteArrayInputStream(value.getResource()));
    } catch (final RuntimeException e) {
      LOG.warn(
          "Failed to parse BPMN resource for process definition {} ({}); its sequence flows will"
              + " resolve as unknown from now on",
          processDefinitionKey,
          value.getBpmnProcessId(),
          e);
      return;
    }
    final Map<String, FlowEndpoints> parsed = new HashMap<>();
    for (final SequenceFlow flow : model.getModelElementsByType(SequenceFlow.class)) {
      final FlowNode source = flow.getSource();
      final FlowNode target = flow.getTarget();
      if (source == null || target == null) {
        continue; // malformed/unlinked flow in the model -- nothing to resolve for it
      }
      final FlowEndpoints endpoints = new FlowEndpoints(source.getId(), target.getId());
      parsed.put(flow.getId(), endpoints);
      state.putFlowEndpoints(processDefinitionKey, flow.getId(), endpoints);
    }
    flowEndpointsCache.put(processDefinitionKey, parsed);
  }

  /**
   * The branch-count fold's per-record hot-path lookup: this definition's per-flow heap cache
   * (warmed in full by {@link #onProcess} when its deployment record was seen this run, or lazily
   * one flow at a time here — the path a restart takes, since the deployment record itself is never
   * replayed past this translator's bootstrap offset). Never fails, never guesses adjacency from
   * record ordering (unreliable under parallel-gateway interleaving) — {@code null} is the honest
   * answer for a flow whose definition was never resolved (e.g. the source log's head was
   * retention-trimmed before bootstrap).
   */
  private FlowEndpoints resolveFlowEndpoints(final long processDefinitionKey, final String flowId) {
    final Map<String, FlowEndpoints> cache =
        flowEndpointsCache.computeIfAbsent(processDefinitionKey, k -> new HashMap<>());
    if (cache.containsKey(flowId)) {
      return cache.get(flowId); // may itself be null -- a previously-cached "unknown" answer
    }
    final FlowEndpoints fromState = state.flowEndpoints(processDefinitionKey, flowId);
    cache.put(flowId, fromState); // cache the miss too, so a permanently-unknown flow isn't re-read
    return fromState;
  }

  /**
   * Converts an epoch-millisecond instant to the epoch microseconds a {@code timestamptz} column
   * stores.
   */
  private static long millisToMicros(final long epochMillis) {
    return epochMillis * 1000L;
  }

  private static String finalStateOf(final Intent intent) {
    if (intent == ProcessInstanceIntent.ELEMENT_COMPLETED) {
      return "COMPLETED";
    }
    if (intent == ProcessInstanceIntent.ELEMENT_TERMINATED) {
      return "TERMINATED";
    }
    return null;
  }

  /**
   * Builds the instance variable payload: a JSON object whose keys are JSON-escaped variable names
   * and whose values are inserted <b>raw</b> (variable values are already JSON documents). {@code
   * "{}"} when there are no variables. Uses {@link #varsJsonScratch} rather than a fresh {@code
   * StringBuilder} per call — see class javadoc's allocation note; the returned {@link String} (and
   * the caller's subsequent UTF-8 encoding of it) is still an unavoidable per-completed-instance
   * allocation, just no longer a doubled one.
   */
  private String varsJson(final Map<String, String> variables) {
    if (variables.isEmpty()) {
      return "{}";
    }
    varsJsonScratch.setLength(0);
    varsJsonScratch.append('{');
    boolean first = true;
    for (final Map.Entry<String, String> entry : variables.entrySet()) {
      if (!first) {
        varsJsonScratch.append(',');
      }
      first = false;
      varsJsonScratch.append('"');
      escapeJson(varsJsonScratch, entry.getKey());
      varsJsonScratch.append("\":").append(entry.getValue());
    }
    return varsJsonScratch.append('}').toString();
  }

  /** Appends {@code raw} to {@code out} escaped as the contents of a JSON string. */
  private static void escapeJson(final StringBuilder out, final String raw) {
    for (int i = 0; i < raw.length(); i++) {
      final char c = raw.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            appendUnicodeEscape(out, c);
          } else {
            out.append(c);
          }
        }
      }
    }
  }

  /**
   * Appends {@code \\uXXXX} for a control character manually (four hex digit appends), instead of
   * {@code String.format}: the format string parser and its boxing of {@code (int) c} allocate on
   * every call, which would otherwise run on every completed instance whose variable JSON happens
   * to contain a raw control character — rare, but avoidable at zero extra complexity.
   */
  private static void appendUnicodeEscape(final StringBuilder out, final char c) {
    out.append('\\').append('u');
    out.append(HEX_DIGITS[(c >> 12) & 0xF]);
    out.append(HEX_DIGITS[(c >> 8) & 0xF]);
    out.append(HEX_DIGITS[(c >> 4) & 0xF]);
    out.append(HEX_DIGITS[c & 0xF]);
  }

  // ===========================================================================================
  // Variable profiling -- see class javadoc's own section. Grouped as one block, called only from
  // the single "profilesRider != null" hook point in emitInstance, mirroring the variant-capture
  // block below for the same isolate-for-merge reason.
  // ===========================================================================================

  /**
   * Folds {@code instance}'s just-finished final root variables into the {@code profilesRider} (see
   * {@link #profilesRider}'s own field javadoc): one fold per variable, dims (process id, variable
   * name), classified by the JSON value's own first token into exactly one of {@link
   * #PROFILE_COUNTER_NUMBER}/{@link #PROFILE_COUNTER_STRING}/{@link #PROFILE_COUNTER_BOOLEAN}/
   * {@link #PROFILE_COUNTER_NULL}/{@link #PROFILE_COUNTER_OBJECT_OR_ARRAY} — a cheap check that
   * never fully parses a non-numeric value. Only a numeric value additionally stages the {@code
   * value} measure (see {@code io.camunda.analytics.lake.sink.algebra.DoubleScalarStatsAlgebra}/
   * {@code SignedDoubleExpHistogramAlgebra}'s own non-finite rule for why {@code "1e999"} — which
   * {@link Double#parseDouble} parses to {@link Double#POSITIVE_INFINITY}, not an exception — is a
   * real, correctly-handled path here, not an edge case this method needs to special-case itself).
   *
   * <p><b>No string variable VALUES are ever stored</b> — a privacy decision baked into this fold:
   * only the classification and (for numeric values) the parsed number ever reach the rider: a
   * string variable's own content is read only long enough to inspect its first character.
   *
   * <p>Allocation: bounded by the number of distinct variables on one completed instance, which is
   * exactly the same "per completed instance, not per record" budget {@link #emitInstance}'s own
   * {@code vars_json} build already spends (see class javadoc's allocation note) — this method
   * reuses the same {@code variables} map {@link #emitInstance} already read, allocating nothing
   * beyond what iterating it and (for a numeric value) {@link Double#parseDouble} themselves cost.
   */
  private void foldVariableProfiles(
      final String processId, final Map<String, String> variables, final long completedAtMs) {
    if (variables.isEmpty()) {
      return;
    }
    final long eventTimeMicros = millisToMicros(completedAtMs);
    for (final Map.Entry<String, String> entry : variables.entrySet()) {
      final String valueJson = entry.getValue();
      profilesRider
          .putDict(PROFILE_DIM_PROCESS_ID, processId)
          .putDict(PROFILE_DIM_VAR_NAME, entry.getKey());
      switch (valueJson.charAt(0)) {
        case '"' -> profilesRider.incrementCounter(PROFILE_COUNTER_STRING);
        case 't', 'f' -> profilesRider.incrementCounter(PROFILE_COUNTER_BOOLEAN);
        case 'n' -> profilesRider.incrementCounter(PROFILE_COUNTER_NULL);
        case '{', '[' -> profilesRider.incrementCounter(PROFILE_COUNTER_OBJECT_OR_ARRAY);
        default -> {
          profilesRider.incrementCounter(PROFILE_COUNTER_NUMBER);
          stageNumericValue(valueJson);
        }
      }
      profilesRider.fold(eventTimeMicros);
    }
  }

  /**
   * Parses {@code valueJson}'s numeric token and stages it as the {@code value} measure — see
   * {@link #foldVariableProfiles}'s own javadoc for the non-finite path this deliberately does not
   * special-case. A parse failure (never expected for validated JSON, but not this method's job to
   * assume) simply leaves the measure unstaged for this record — {@code
   * io.camunda.analytics.lake.metrics.PollFedRider}'s own "null measure = skip that measure, not
   * the row" rule already covers it, so the variable is still counted under {@link
   * #PROFILE_COUNTER_NUMBER}, just without a numeric value contribution this one time.
   */
  private void stageNumericValue(final String valueJson) {
    try {
      profilesRider.putDoubleMeasure(PROFILE_MEASURE_VALUE, Double.parseDouble(valueJson));
    } catch (final NumberFormatException e) {
      LOG.debug(
          "Variable value '{}' looked numeric but failed to parse; skipping its value", valueJson);
    }
  }

  // ===========================================================================================
  // Variant capture (scheme variant-k1) -- see class javadoc's own section. Grouped as one block
  // below the pre-existing translator logic above, called only from the small, clearly-marked
  // "variant-k1" hook points in onProcessInstance/emitInstance, to keep this addition easy to
  // isolate for merge purposes (see LakeTranslator.java's own history for other concurrent work).
  // ===========================================================================================

  /**
   * Folds one activated element or taken sequence flow into {@code processInstanceKey}'s variant
   * accumulator, honoring the replay guard — see class javadoc's "Variant capture" section for the
   * full scheme this implements.
   */
  private void foldVariant(
      final long processInstanceKey,
      final String bpmnProcessId,
      final String id,
      final VariantElementKind kind,
      final long position) {
    final VariantAccumulator accumulator = state.getVariantAccumulator(processInstanceKey);
    if (accumulator == null) {
      return; // replay past evict, or an accumulator never created for this instance -- expected
    }
    if (position <= accumulator.lastPosition()) {
      return; // REPLAY GUARD -- see class javadoc; must not re-mix or re-count a replayed record
    }
    final long idHash = VariantHash.h64(id);
    final int h32 = (int) idHash;
    final int[] seenHashes = accumulator.seenHashes();
    final int insertionPoint = Arrays.binarySearch(seenHashes, h32);
    if (insertionPoint < 0) {
      final int[] grown = insertSorted(seenHashes, -(insertionPoint + 1), h32);
      final long seed = VariantHash.h64(bpmnProcessId);
      final long newHash = accumulator.hash() ^ VariantHash.mix64(seed, idHash);
      recordVariantNameIfUnknown(bpmnProcessId, h32, id, kind);
      state.putVariantAccumulator(
          processInstanceKey, new VariantAccumulator(position, newHash, grown.length, grown));
    } else {
      // Set semantics: a repeat contributes no second entry and re-mixes nothing -- but
      // lastPosition still advances (see class javadoc: "Always update lastPosition when
      // folding").
      state.putVariantAccumulator(
          processInstanceKey,
          new VariantAccumulator(position, accumulator.hash(), accumulator.count(), seenHashes));
    }
  }

  /** Inserts {@code value} at {@code index} of a copy of {@code sorted}, keeping it sorted. */
  private static int[] insertSorted(final int[] sorted, final int index, final int value) {
    final int[] grown = new int[sorted.length + 1];
    System.arraycopy(sorted, 0, grown, 0, index);
    grown[index] = value;
    System.arraycopy(sorted, index, grown, index + 1, sorted.length - index);
    return grown;
  }

  /**
   * Writes {@code (bpmnProcessId, h32) -> (id, kind)} into the durable name map on a heap-cache
   * miss only — see {@link #knownVariantNamesByProcess}'s own javadoc for why a miss here is rare
   * in steady state (first sight of a given id, per process, per translator lifetime).
   */
  private void recordVariantNameIfUnknown(
      final String bpmnProcessId, final int h32, final String id, final VariantElementKind kind) {
    final Set<Integer> known =
        knownVariantNamesByProcess.computeIfAbsent(bpmnProcessId, ignored -> new HashSet<>());
    if (known.add(h32)) {
      state.putVariantName(bpmnProcessId, h32, new VariantName(id, kind));
    }
  }

  /**
   * Emits one {@code variants} dictionary row for {@code instance}'s just-finished variant, unless
   * {@link #variantsAppender} is unwired or this (process id, version, variant hash) triple was
   * already emitted by this translator (see {@link #variantDictionarySeenCache}'s own javadoc).
   * Decodes {@code variantAccumulator}'s {@code seenHashes} back into sorted element/flow id lists
   * via the name map, joins each with {@code '\n'}, and stamps {@code completedAtMs} as {@code
   * first_seen} — see class javadoc's "Variant capture" section for the full scheme.
   */
  private void emitVariantDictionaryRowIfNew(
      final OpenInstance instance,
      final VariantAccumulator variantAccumulator,
      final String variantHashHex,
      final long completedAtMs) {
    if (variantsAppender == null) {
      return; // dictionary pipeline not wired -- see #variantsAppender's own field javadoc
    }
    final VariantDictKey key =
        new VariantDictKey(instance.processId(), instance.version(), variantHashHex);
    if (!variantDictionarySeenCache.checkAndMarkSeen(key)) {
      return; // already emitted -- see the cache's own javadoc
    }

    final List<String> elementIds = new ArrayList<>();
    final List<String> flowIds = new ArrayList<>();
    for (final int h32 : variantAccumulator.seenHashes()) {
      final VariantName name = state.getVariantName(instance.processId(), h32);
      if (name == null) {
        continue; // name-map entry lost (state loss) -- omit rather than fail
      }
      (name.kind() == VariantElementKind.FLOW ? flowIds : elementIds).add(name.id());
    }
    Collections.sort(elementIds);
    Collections.sort(flowIds);
    final byte[] elementsBytes = String.join("\n", elementIds).getBytes(StandardCharsets.UTF_8);
    final byte[] flowsBytes = String.join("\n", flowIds).getBytes(StandardCharsets.UTF_8);

    if (!variantsAppender.begin()) {
      // Ring backpressure on the small, dedicated variants dictionary pipeline: unlike every other
      // RowAppender use in this class, this is deliberately absorbed rather than propagated as
      // onRecord() == false -- a dropped dictionary row costs nothing but a later duplicate write
      // (see the seen-cache's own javadoc), so it must never hold back the primary instances row
      // this method is called from. Un-marking the seen-cache entry lets a later completion of the
      // same variant retry the write.
      variantDictionarySeenCache.remove(key);
      return;
    }
    variantsAppender
        .putDict(VariantColumns.PROCESS_ID, instance.processId())
        .putInt(VariantColumns.VERSION, instance.version())
        .putDict(VariantColumns.VARIANT_HASH, variantHashHex)
        .putBinary(VariantColumns.ELEMENTS, elementsBytes, 0, elementsBytes.length)
        .putBinary(VariantColumns.FLOWS, flowsBytes, 0, flowsBytes.length)
        .putLong(VariantColumns.FIRST_SEEN, millisToMicros(completedAtMs));
    variantsAppender.endRow();
  }

  /** Dedup key for {@link #variantDictionarySeenCache}. */
  private record VariantDictKey(String processId, int version, String variantHash) {}

  /**
   * Bounded (LRU-evicted, access-order) per-translator cache of (process id, version, variant hash)
   * triples already known to have a dictionary row — see {@link #emitVariantDictionaryRowIfNew}. A
   * cache miss does not mean the {@code variants} table lacks the row (a prior instance, an earlier
   * eviction, or a fresh restart could already have written it) — readers dedup the table
   * themselves, since a row's content is fully determined by its key (sorted id lists) — so a false
   * negative here only ever costs one harmless duplicate row, never a correctness bug.
   */
  private static final class VariantDictionarySeenCache
      extends LinkedHashMap<VariantDictKey, Boolean> {

    private static final int INITIAL_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;

    VariantDictionarySeenCache() {
      super(INITIAL_CAPACITY, LOAD_FACTOR, true);
    }

    @Override
    protected boolean removeEldestEntry(final Map.Entry<VariantDictKey, Boolean> eldest) {
      return size() > VARIANT_DICTIONARY_CACHE_CAPACITY;
    }

    /**
     * @return {@code true} the first time {@code key} is checked (caller should emit); {@code
     *     false} on a repeat
     */
    boolean checkAndMarkSeen(final VariantDictKey key) {
      return put(key, Boolean.TRUE) == null;
    }
  }

  // ===========================================================================================
  // Object fabric capture -- see class javadoc's own section. Grouped as one block, mirroring the
  // variant-capture block above for the same isolate-for-merge reason.
  // ===========================================================================================

  /**
   * Sighting source 1 (see class javadoc): a {@code VARIABLE CREATED}/{@code UPDATED} record whose
   * name matches a declared {@link
   * io.camunda.analytics.lake.objects.IdentifierSource.VariableIdentifier} and whose value is a
   * JSON scalar string or number. Runs for every scope, not just root -- {@code scope_key} is the
   * variable's own {@code getScopeKey()}.
   */
  private void foldObjectSightingFromVariable(
      final Record<?> record, final VariableRecordValue value) {
    if (objectTypes == null) {
      return;
    }
    final CompiledObjectType type = objectTypes.variableIdentifiedType(value.getName());
    if (type == null) {
      return;
    }
    final String objectId = scalarObjectId(value.getValue());
    if (objectId == null) {
      return; // non-scalar value, or oversized -- never sights (see #scalarObjectId's own javadoc)
    }
    foldObjectSighting(
        value.getProcessInstanceKey(),
        type.name(),
        objectId,
        value.getScopeKey(),
        OBJECT_QUALIFIER_VARIABLE,
        record.getTimestamp());
  }

  /**
   * Sighting source 2 (see class javadoc): the instance-side of a message rendezvous. {@code
   * scope_key} is conservatively recorded as root ({@code instanceKey} itself) -- this is a
   * documented v1 approximation, not a precise scope, see class javadoc.
   */
  private void onProcessMessageSubscriptionCorrelated(final Record<?> record) {
    if (objectTypes == null) {
      return;
    }
    final CompiledObjectType type = objectTypes.correlationKeyIdentifiedType();
    if (type == null) {
      return;
    }
    final ProcessMessageSubscriptionRecordValue value =
        (ProcessMessageSubscriptionRecordValue) record.getValue();
    final String correlationKey = value.getCorrelationKey();
    if (correlationKey == null || correlationKey.isBlank()) {
      return;
    }
    final long instanceKey = value.getProcessInstanceKey();
    foldObjectSighting(
        instanceKey,
        type.name(),
        correlationKey,
        instanceKey,
        OBJECT_QUALIFIER_MESSAGE,
        record.getTimestamp());
  }

  /**
   * Sighting source 3 (see class javadoc): a message start event correlation, which is what creates
   * the new process instance -- {@code scope_key = instanceKey} (root) here is exact, not an
   * approximation.
   */
  private void onMessageStartEventSubscriptionCorrelated(final Record<?> record) {
    if (objectTypes == null) {
      return;
    }
    final CompiledObjectType type = objectTypes.correlationKeyIdentifiedType();
    if (type == null) {
      return;
    }
    final MessageStartEventSubscriptionRecordValue value =
        (MessageStartEventSubscriptionRecordValue) record.getValue();
    final long instanceKey = value.getProcessInstanceKey();
    if (instanceKey <= 0) {
      return; // defensive -- CORRELATED implies this is set, per the record value's own javadoc
    }
    final String correlationKey = value.getCorrelationKey();
    if (correlationKey == null || correlationKey.isBlank()) {
      return;
    }
    foldObjectSighting(
        instanceKey,
        type.name(),
        correlationKey,
        instanceKey,
        OBJECT_QUALIFIER_MESSAGE_START,
        record.getTimestamp());
  }

  /**
   * Extracts a scalar object id from a variable's raw JSON value token, per class javadoc's rule:
   * only a JSON string or number sights (never boolean, null, object, or array), the raw token used
   * unquoted. Also enforces {@link #MAX_OBJECT_ID_CHARS} as a guardrail against a pathological blob
   * masquerading as a scalar.
   *
   * @return the unquoted/unescaped id, or {@code null} when {@code valueJson} does not qualify
   */
  private static String scalarObjectId(final String valueJson) {
    if (valueJson.isEmpty()) {
      return null;
    }
    final char first = valueJson.charAt(0);
    final String id;
    if (first == '"') {
      id = unescapeJsonString(valueJson);
    } else if (first == '-' || (first >= '0' && first <= '9')) {
      id = valueJson; // raw numeric token, unquoted by construction
    } else {
      return null; // boolean/null/object/array -- never an identifier (see class javadoc)
    }
    if (id == null || id.isBlank() || id.length() > MAX_OBJECT_ID_CHARS) {
      return null;
    }
    return id;
  }

  /**
   * Unescapes a JSON string token's surrounding quotes and escape sequences — the inverse of {@link
   * #escapeJson}, supporting the same repertoire. {@code null} on a malformed token (missing
   * closing quote); never expected for validated JSON, but not this method's job to assume.
   */
  private static String unescapeJsonString(final String valueJson) {
    if (valueJson.length() < 2 || valueJson.charAt(valueJson.length() - 1) != '"') {
      return null;
    }
    final StringBuilder out = new StringBuilder(valueJson.length() - 2);
    for (int i = 1; i < valueJson.length() - 1; i++) {
      final char c = valueJson.charAt(i);
      if (c != '\\') {
        out.append(c);
        continue;
      }
      if (i + 1 >= valueJson.length() - 1) {
        return null; // trailing backslash with nothing to escape -- malformed
      }
      i++;
      final char escaped = valueJson.charAt(i);
      switch (escaped) {
        case '"' -> out.append('"');
        case '\\' -> out.append('\\');
        case '/' -> out.append('/');
        case 'b' -> out.append('\b');
        case 'f' -> out.append('\f');
        case 'n' -> out.append('\n');
        case 'r' -> out.append('\r');
        case 't' -> out.append('\t');
        case 'u' -> {
          if (i + 4 >= valueJson.length() - 1) {
            return null;
          }
          out.append((char) Integer.parseInt(valueJson.substring(i + 1, i + 5), 16));
          i += 4;
        }
        default -> out.append(escaped); // lenient: an unrecognized escape passes its char through
      }
    }
    return out.toString();
  }

  /**
   * The shared sighting fold every source above calls: seen-cache check, per-instance CF-7
   * bookkeeping (always, regardless of {@link #objectsAppender} wiring), the object-lifecycle
   * birth/{@code nSightings} fold (always, regardless of wiring — see class javadoc's "Object
   * lifecycle capture" section), then the dictionary row itself (only if wired).
   */
  private void foldObjectSighting(
      final long instanceKey,
      final String objectType,
      final String objectId,
      final long scopeKey,
      final String qualifier,
      final long timestamp) {
    final ObjectSightingKey key =
        new ObjectSightingKey(objectType, objectId, instanceKey, scopeKey);
    if (!objectSightingSeenCache.checkAndMarkSeen(key)) {
      return; // already sighted this translator lifetime -- see the cache's own javadoc
    }
    final boolean acceptedNewCf7Entry =
        recordObjectSightingForRelations(instanceKey, objectType, objectId, scopeKey);
    foldObjectLifecycleSighting(objectType, objectId, timestamp, acceptedNewCf7Entry);
    emitObjectDictionaryRowIfWired(
        key, instanceKey, objectType, objectId, scopeKey, qualifier, timestamp);
  }

  /**
   * Appends {@code (objectType, objectId, scopeKey)} to {@code instanceKey}'s accumulated sighting
   * list (read-modify-write, mirroring {@link #foldVariant}'s own accumulator pattern), capped at
   * {@link #MAX_OBJECT_SIGHTINGS_PER_INSTANCE} with an overflow flag once hit — see that constant's
   * own javadoc. Runs unconditionally: relations derivation needs this regardless of whether the
   * {@code objects}/{@code object_relations} dictionary appenders are even wired.
   *
   * <p><b>The list is a content-deduplicated SET in list clothing, not a plain append log.</b> The
   * caller's {@link #objectSightingSeenCache} is only a heap write-avoidance optimization — it is
   * NOT the correctness guard against a duplicate append, for two reasons neither of which it
   * covers: (1) crash-replay — {@link TranslatorState} (RocksDB) can be durably ahead of the lake's
   * own committed cut (the two are not atomically coupled, same as the variant accumulator's own
   * replay exposure — see class javadoc's "Origin-position dedup" section), so a resumed translator
   * can re-fold a VARIABLE record whose sighting this list already holds, with an empty (post-
   * restart) heap cache that admits it as "new"; (2) the heap cache is LRU-bounded and can evict a
   * still-open instance's own entry while the instance is still open, letting a genuine re-sighting
   * (e.g. an unchanged variable re-delivered) pass the cache again. Unlike the variant accumulator,
   * there is no {@code lastPosition} here to reject a replay by position — this list has no natural
   * "position" of its own (unlike a single running hash, entries are unordered facts) — so
   * correctness instead comes from this method scanning the existing list (at most {@link
   * #MAX_OBJECT_SIGHTINGS_PER_INSTANCE} entries — the O(n) cost is paid only on a cache miss, the
   * rare path) for an entry already equal on {@code (objectType, objectId, scopeKey)} before ever
   * appending. Without this, a duplicate append at the {@link #MAX_OBJECT_SIGHTINGS_PER_INSTANCE}
   * boundary would silently consume a slot a genuinely new, later sighting needed — eroding the cap
   * into a missed relation, not just a harmless repeated row.
   *
   * @return {@code true} only on the genuine-append branch (a new entry was actually added to the
   *     list) — {@code false} on every early-return branch (already capped, already recorded, or
   *     hit the cap this call). Reused by {@link #foldObjectLifecycleSighting} as the signal for
   *     whether this sighting should increment an object's {@code nSightings} — see that method's
   *     own javadoc and class javadoc's "Object lifecycle capture" section for why recounting a
   *     duplicate there would be wrong.
   */
  private boolean recordObjectSightingForRelations(
      final long instanceKey, final String objectType, final String objectId, final long scopeKey) {
    final ObjectSightingList existing = state.getObjectSightings(instanceKey);
    if (existing != null && existing.overflowed()) {
      return false; // already capped -- see MAX_OBJECT_SIGHTINGS_PER_INSTANCE's own javadoc
    }
    final List<ObjectSighting> current = existing == null ? List.of() : existing.sightings();
    for (final ObjectSighting sighting : current) {
      if (sighting.objectType().equals(objectType)
          && sighting.objectId().equals(objectId)
          && sighting.scopeKey() == scopeKey) {
        return false; // already recorded -- see this method's own javadoc on why this scan is
        // load-bearing
      }
    }
    if (current.size() >= MAX_OBJECT_SIGHTINGS_PER_INSTANCE) {
      state.putObjectSightings(instanceKey, new ObjectSightingList(current, true));
      return false;
    }
    final List<ObjectSighting> grown = new ArrayList<>(current.size() + 1);
    grown.addAll(current);
    grown.add(new ObjectSighting(objectType, objectId, scopeKey));
    state.putObjectSightings(instanceKey, new ObjectSightingList(grown, false));
    return true;
  }

  /**
   * The object-lifecycle birth/{@code nSightings} fold — see class javadoc's "Object lifecycle
   * capture" section for the full scheme. Runs unconditionally from {@link #foldObjectSighting}
   * (mirroring {@link #recordObjectSightingForRelations}'s own "runs regardless of wiring" rule):
   * the lifecycle accumulator and the {@link #objectsBornRider} count are independent of whether
   * {@link #objectLifecycleAppender} (closing emission) is ever wired at all.
   *
   * @param acceptedNewCf7Entry {@link #recordObjectSightingForRelations}'s own return value for
   *     this exact sighting — {@code true} only when it actually appended a new CF-7 entry
   */
  private void foldObjectLifecycleSighting(
      final String objectType,
      final String objectId,
      final long timestamp,
      final boolean acceptedNewCf7Entry) {
    final ObjectLifecycle existing = state.getObjectLifecycle(objectType, objectId);
    if (existing == null) {
      // FIRST sighting ever for this (type, id) -- birth. Deliberately independent of
      // acceptedNewCf7Entry: the CF-7 per-instance cap (MAX_OBJECT_SIGHTINGS_PER_INSTANCE) can
      // reject this exact sighting's entry while it is still this OBJECT's own genuinely first
      // sighting anywhere -- birth is about the object, not about whether this instance's own
      // relations-derivation list had room. If an accumulator already exists (open or tombstoned),
      // this branch is never reached -- see class javadoc for why that single presence check alone
      // makes birth safe against replay-with-ahead-state and late-sightings-after-close alike.
      state.putObjectLifecycle(
          objectType,
          objectId,
          new ObjectLifecycle(
              LifecycleStatus.OPEN, timestamp, BirthQualifier.FIRST_SIGHTING, 1, 0L));
      foldObjectsBornCounter(objectType, timestamp);
      return;
    }
    if (existing.status() == LifecycleStatus.OPEN && acceptedNewCf7Entry) {
      state.putObjectLifecycle(
          objectType,
          objectId,
          new ObjectLifecycle(
              LifecycleStatus.OPEN,
              existing.birthTsMs(),
              existing.birthQualifier(),
              existing.nSightings() + 1,
              0L));
    }
    // else: CLOSED_TOMBSTONE -- documented v1 behavior, a late sighting after close never reopens
    // the object nor recounts it (see class javadoc's "Object lifecycle capture" section).
  }

  /**
   * Folds one object's birth into {@link #objectsBornRider} — see {@link PollFedRider}'s own class
   * javadoc for the poll-fed rider mechanics. A no-op when the rider isn't wired.
   */
  private void foldObjectsBornCounter(final String objectType, final long timestamp) {
    if (objectsBornRider == null) {
      return;
    }
    objectsBornRider.putDict(OBJECT_BORN_DIM_OBJECT_TYPE, objectType);
    objectsBornRider.fold(millisToMicros(timestamp));
  }

  /**
   * Object lifecycle capture: closing (see class javadoc's "Object lifecycle capture" section for
   * the full scheme and, in particular, for why this method's ordering relative to {@link
   * #emitInstance}'s own mutations is load-bearing). A no-op — {@code true}, nothing scanned — when
   * either {@link #objectTypes} or {@link #objectLifecycleAppender} is unwired, or when {@code
   * bpmnProcessId} is not a declared closing process for any object type, or when this instance
   * accumulated no sightings at all: every one of those is a plain map/null lookup, so a
   * non-closing process's completion (the overwhelming majority in any deployment) costs one cheap
   * check and returns immediately.
   *
   * <p>For each of this instance's CF-7 sightings whose object type closes on {@code
   * bpmnProcessId}: an {@link LifecycleStatus#OPEN} lifecycle accumulator emits one {@code
   * object_lifecycle} row and flips to {@link LifecycleStatus#CLOSED_TOMBSTONE} <em>in this same
   * per-candidate step</em> (write immediately, not batched) — a later candidate sighting the exact
   * same (type, id) at a different scope (e.g. root and non-root) therefore re-reads its own,
   * now-already-tombstoned accumulator and skips silently, which is what makes this loop safe to
   * resume from the top on a retry (see below) without a separate in-loop dedup set. An accumulator
   * that is {@code null} (an object was somehow never born — not expected, since every sighting
   * births one) or already {@link LifecycleStatus#CLOSED_TOMBSTONE} (an earlier closing instance,
   * or an earlier iteration of this very loop) is skipped silently — the documented double-close
   * suppression.
   *
   * <p><b>Backpressure is propagated, not absorbed</b> (see class javadoc): the moment {@link
   * #objectLifecycleAppender}'s {@code begin()} fails for a candidate, this method returns {@code
   * false} immediately, appending and flipping nothing for that candidate. Any earlier candidate in
   * this same call that already succeeded stays flipped — this is safe, not a partial-mutation bug,
   * precisely because of the same idempotent-on-retry property described above: {@link
   * #emitInstance} returns {@code false} in turn, before it has performed <em>any other</em>
   * mutation for this record (this method runs before its own {@code instanceAppender.begin()}), so
   * the caller's retry re-enters this exact method from the top; already-flipped candidates re-read
   * as {@link LifecycleStatus#CLOSED_TOMBSTONE} and are skipped, and only the still-{@link
   * LifecycleStatus#OPEN} candidate(s) — the one(s) that actually hit backpressure — are retried.
   *
   * @return {@code false} on backpressure — the caller ({@link #emitInstance}) must return {@code
   *     false} immediately in turn, before touching anything else
   */
  private boolean emitObjectLifecycleClosingsIfDeclared(
      final long processInstanceKey,
      final String bpmnProcessId,
      final long timestamp,
      final String finalState) {
    if (objectTypes == null || objectLifecycleAppender == null) {
      return true;
    }
    final List<CompiledObjectType> closingTypes = objectTypes.closingTypesForProcess(bpmnProcessId);
    if (closingTypes.isEmpty()) {
      return true;
    }
    final ObjectSightingList sightings = state.getObjectSightings(processInstanceKey);
    if (sightings == null || sightings.sightings().isEmpty()) {
      return true;
    }
    for (final ObjectSighting sighting : sightings.sightings()) {
      if (!closesOnThisProcess(closingTypes, sighting.objectType())) {
        continue;
      }
      final ObjectLifecycle lifecycle =
          state.getObjectLifecycle(sighting.objectType(), sighting.objectId());
      if (lifecycle == null || lifecycle.status() != LifecycleStatus.OPEN) {
        continue; // never born (unexpected), or already tombstoned -- see this method's own javadoc
      }
      if (!objectLifecycleAppender.begin()) {
        return false; // see this method's own javadoc for why this is safe to retry from the top
      }
      objectLifecycleAppender
          .putDict(ObjectLifecycleColumns.OBJECT_TYPE, sighting.objectType())
          .putDict(ObjectLifecycleColumns.OBJECT_ID, sighting.objectId())
          .putDict(ObjectLifecycleColumns.BIRTH_QUALIFIER, lifecycle.birthQualifier().name())
          .putLong(ObjectLifecycleColumns.BIRTH_TS, millisToMicros(lifecycle.birthTsMs()))
          .putLong(ObjectLifecycleColumns.CLOSED_AT, millisToMicros(timestamp))
          .putLong(ObjectLifecycleColumns.DURATION_MS, timestamp - lifecycle.birthTsMs())
          .putDict(ObjectLifecycleColumns.OUTCOME, finalState)
          .putInt(ObjectLifecycleColumns.N_SIGHTINGS, lifecycle.nSightings());
      objectLifecycleAppender.endRow();
      state.putObjectLifecycle(
          sighting.objectType(),
          sighting.objectId(),
          new ObjectLifecycle(
              LifecycleStatus.CLOSED_TOMBSTONE,
              lifecycle.birthTsMs(),
              lifecycle.birthQualifier(),
              lifecycle.nSightings(),
              timestamp));
    }
    return true;
  }

  /** Whether any of {@code closingTypes} is named {@code objectType}. */
  private static boolean closesOnThisProcess(
      final List<CompiledObjectType> closingTypes, final String objectType) {
    for (final CompiledObjectType type : closingTypes) {
      if (type.name().equals(objectType)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Emits one {@code objects} dictionary row for a first-sighted (objectType, objectId, instance,
   * scope), unless {@link #objectsAppender} is unwired. Looks up the owning instance's process
   * id/version from {@link TranslatorState#getInstance} (not carried by every sighting source's own
   * record) — a missing instance (state loss, or a message-start correlation racing ahead of its
   * own root activation) skips the row (the sighting is still recorded for relations) and un-marks
   * the seen-cache entry so a later retry (e.g. after a crash-restart re-delivers this record) gets
   * another chance, mirroring the backpressure-absorb path below.
   */
  private void emitObjectDictionaryRowIfWired(
      final ObjectSightingKey key,
      final long instanceKey,
      final String objectType,
      final String objectId,
      final long scopeKey,
      final String qualifier,
      final long timestamp) {
    if (objectsAppender == null) {
      return;
    }
    final OpenInstance instance = state.getInstance(instanceKey);
    if (instance == null) {
      objectSightingSeenCache.unmark(key);
      return;
    }
    if (!objectsAppender.begin()) {
      // Ring backpressure on this small, dedicated dictionary pipeline: absorbed, not propagated --
      // see class javadoc's "Object fabric capture" section for the judgment call this mirrors.
      objectSightingSeenCache.unmark(key);
      return;
    }
    objectsAppender
        .putDict(ObjectColumns.OBJECT_TYPE, objectType)
        .putDict(ObjectColumns.OBJECT_ID, objectId)
        .putLong(ObjectColumns.INSTANCE_KEY, instanceKey)
        .putDict(ObjectColumns.PROCESS_ID, instance.processId())
        .putInt(ObjectColumns.VERSION, instance.version());
    if (scopeKey == instanceKey) {
      objectsAppender.putNull(ObjectColumns.SCOPE_KEY);
    } else {
      objectsAppender.putLong(ObjectColumns.SCOPE_KEY, scopeKey);
    }
    objectsAppender
        .putDict(ObjectColumns.QUALIFIER, qualifier)
        .putLong(ObjectColumns.FIRST_SEEN, millisToMicros(timestamp));
    objectsAppender.endRow();
  }

  /**
   * Emits one {@code instance_links} row for {@code childInstanceKey}'s call-activity parent,
   * unless {@link #instanceLinksAppender} is unwired or this child instance already has a link row
   * (see {@link #instanceLinkSeenCache}: at most one parent per child, ever).
   */
  private void emitInstanceLinkIfNew(
      final long parentInstanceKey,
      final long childInstanceKey,
      final long viaElementInstanceKey,
      final long timestamp) {
    if (instanceLinksAppender == null) {
      return;
    }
    if (!instanceLinkSeenCache.checkAndMarkSeen(childInstanceKey)) {
      return;
    }
    if (!instanceLinksAppender.begin()) {
      instanceLinkSeenCache.unmark(childInstanceKey);
      return;
    }
    instanceLinksAppender
        .putLong(InstanceLinkColumns.PARENT_INSTANCE_KEY, parentInstanceKey)
        .putLong(InstanceLinkColumns.CHILD_INSTANCE_KEY, childInstanceKey)
        .putDict(InstanceLinkColumns.LINK_TYPE, INSTANCE_LINK_TYPE_CALL_ACTIVITY);
    if (viaElementInstanceKey > 0) {
      instanceLinksAppender.putLong(
          InstanceLinkColumns.VIA_ELEMENT_INSTANCE_KEY, viaElementInstanceKey);
    } else {
      instanceLinksAppender.putNull(InstanceLinkColumns.VIA_ELEMENT_INSTANCE_KEY);
    }
    instanceLinksAppender.putLong(InstanceLinkColumns.LINKED_AT, millisToMicros(timestamp));
    instanceLinksAppender.endRow();
  }

  /**
   * Derives and emits {@code object_relations} rows from {@code instanceKey}'s accumulated
   * sightings, then evicts the sighting list — called once per completed instance, regardless of
   * whether object-fabric capture is wired at all (the eviction must always happen, mirroring every
   * other per-instance state's evict-after-emit rule).
   *
   * <p><b>v1 rule</b> (see class javadoc): a relation (parent contains child) is emitted for every
   * (root-scope sighting, non-root-scope sighting) pair whose (type, id) differ — a sighting at
   * both root and non-root scope with the identical (type, id) is a self-relation and is skipped.
   */
  private void emitObjectRelationsAtCompletion(final long instanceKey, final long completedAtMs) {
    final ObjectSightingList sightings = state.getObjectSightings(instanceKey);
    state.deleteObjectSightings(instanceKey);
    if (sightings == null || objectRelationsAppender == null) {
      return;
    }
    final List<ObjectSighting> roots = new ArrayList<>();
    final List<ObjectSighting> nonRoots = new ArrayList<>();
    for (final ObjectSighting sighting : sightings.sightings()) {
      (sighting.scopeKey() == instanceKey ? roots : nonRoots).add(sighting);
    }
    for (final ObjectSighting parent : roots) {
      for (final ObjectSighting child : nonRoots) {
        if (parent.objectType().equals(child.objectType())
            && parent.objectId().equals(child.objectId())) {
          continue; // self-relation -- skip (see this method's own javadoc)
        }
        emitObjectRelationRowIfNew(parent, child, completedAtMs);
      }
    }
  }

  /**
   * Emits one {@code object_relations} row for {@code (parent, child)}, unless already emitted (see
   * {@link #objectRelationSeenCache}: deduped across the whole translator lifetime, not just one
   * instance, since the same edge can recur across many completed instances).
   */
  private void emitObjectRelationRowIfNew(
      final ObjectSighting parent, final ObjectSighting child, final long completedAtMs) {
    final ObjectRelationKey key =
        new ObjectRelationKey(
            parent.objectType(), parent.objectId(), child.objectType(), child.objectId());
    if (!objectRelationSeenCache.checkAndMarkSeen(key)) {
      return;
    }
    if (!objectRelationsAppender.begin()) {
      // Absorbed, not propagated -- same judgment call as every other dictionary appender in this
      // class (see class javadoc's "Object fabric capture" section).
      objectRelationSeenCache.unmark(key);
      return;
    }
    objectRelationsAppender
        .putDict(ObjectRelationColumns.PARENT_TYPE, parent.objectType())
        .putDict(ObjectRelationColumns.PARENT_ID, parent.objectId())
        .putDict(ObjectRelationColumns.CHILD_TYPE, child.objectType())
        .putDict(ObjectRelationColumns.CHILD_ID, child.objectId())
        .putLong(ObjectRelationColumns.FIRST_SEEN, millisToMicros(completedAtMs));
    objectRelationsAppender.endRow();
  }

  /** Dedup key for {@link #objectSightingSeenCache}. */
  private record ObjectSightingKey(
      String objectType, String objectId, long instanceKey, long scopeKey) {}

  /** Dedup key for {@link #objectRelationSeenCache}. */
  private record ObjectRelationKey(
      String parentType, String parentId, String childType, String childId) {}

  /**
   * Generic bounded (LRU-evicted, access-order) seen-cache backing the object-fabric capture paths'
   * dictionary emission (sightings/links/relations) — same shape and same "a false negative costs
   * one harmless duplicate write" reasoning as {@link VariantDictionarySeenCache}, kept as a
   * separate, reusable, generic class rather than retrofitting that one (which predates this and is
   * scoped to the variant-k1 scheme specifically).
   */
  private static final class BoundedSeenCache<K> extends LinkedHashMap<K, Boolean> {

    private static final int INITIAL_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;

    private final int capacity;

    BoundedSeenCache(final int capacity) {
      super(INITIAL_CAPACITY, LOAD_FACTOR, true);
      this.capacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(final Map.Entry<K, Boolean> eldest) {
      return size() > capacity;
    }

    /**
     * @return {@code true} the first time {@code key} is checked (caller should emit); {@code
     *     false} on a repeat
     */
    boolean checkAndMarkSeen(final K key) {
      return put(key, Boolean.TRUE) == null;
    }

    /**
     * Un-marks {@code key}, letting a later call re-emit — see call sites for when this applies.
     */
    void unmark(final K key) {
      remove(key);
    }
  }
}
