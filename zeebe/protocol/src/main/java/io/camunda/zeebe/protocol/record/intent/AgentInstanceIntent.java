/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.protocol.record.intent;

/**
 * Intents for agent instance records, aligned 1:1 with the {@code AgentInstanceStatus} state
 * diagram.
 *
 * <p>Instead of a generic {@code UPDATE}/{@code UPDATED} pair carrying a {@code status} payload,
 * each connector-reported state transition has its own command/event pair. This makes the event
 * stream self-describing — exporters and downstream consumers can filter by intent without decoding
 * the record body.
 *
 * <h2>Lifecycle intents (connector-driven)</h2>
 *
 * <ul>
 *   <li>{@link #CREATE} / {@link #CREATED} — agent instance enters {@code INITIALIZING}.
 *   <li>{@link #DISCOVER_TOOLS} / {@link #DISCOVERING_TOOLS} — agent enters {@code TOOL_DISCOVERY}
 *       to enumerate available tools.
 *   <li>{@link #THINK} / {@link #THINKING} — agent enters {@code THINKING}; the connector is about
 *       to (or just did) call the LLM.
 *   <li>{@link #CALL_TOOLS} / {@link #TOOL_CALLING} — agent enters {@code TOOL_CALLING}; the LLM
 *       chose one or more tools. Carries the metric deltas accrued during the just-finished {@code
 *       THINKING} phase (one model call + tokens).
 *   <li>{@link #WAIT_FOR_INPUT} / {@link #WAITING_FOR_INPUT} — agent enters {@code IDLE}; the LLM
 *       returned a final answer and the agent is awaiting external input. Carries the metric deltas
 *       from the just-finished {@code THINKING} phase.
 *   <li>{@link #DELETE} / {@link #DELETED} — terminal; reachable from any state.
 * </ul>
 *
 * <h2>Engine-driven intents</h2>
 *
 * <ul>
 *   <li>{@link #LIMIT_EXCEEDED} — engine-emitted event when a transition command would cause {@code
 *       maxTokens}, {@code maxModelCalls}, or {@code maxToolCalls} to be exceeded. Distinct from a
 *       generic command rejection so downstream consumers (incidents, telemetry) can react without
 *       parsing rejection reasons. Considered terminal for the agent instance.
 * </ul>
 *
 * <h2>Metric semantics</h2>
 *
 * <p>On commands, metric fields ({@code inputTokens}, {@code outputTokens}, {@code modelCalls},
 * {@code toolCalls}) carry <em>deltas</em> for the work performed during the state being
 * <em>left</em>. On events, the same fields carry the engine-aggregated <em>totals</em>. This
 * preserves the dual-semantics behavior of the generic-update design while pinning each delta to
 * the transition it belongs to.
 *
 * <h2>Trade-offs vs. generic UPDATE/UPDATED</h2>
 *
 * <p><b>Pros:</b> self-describing event stream; per-intent payload validation (e.g. {@code
 * CALL_TOOLS} can require {@code toolCalls>=1}); no redundant {@code status} field needed on the
 * event (intent encodes it); easier downstream filtering.
 *
 * <p><b>Cons:</b> every new state requires an intent pair (protocol churn); transitions are
 * connector-reported, not engine-controlled, so the engine cannot strictly enforce the state
 * machine the way it does for {@code ProcessInstanceIntent}; intent count grows from 6 to 13.
 */
public enum AgentInstanceIntent implements Intent {
  CREATE(0, false),
  CREATED(1, true),

  DISCOVER_TOOLS(2, false),
  DISCOVERING_TOOLS(3, true),

  THINK(4, false),
  THINKING(5, true),

  CALL_TOOLS(6, false),
  TOOL_CALLING(7, true),

  WAIT_FOR_INPUT(8, false),
  WAITING_FOR_INPUT(9, true),

  DELETE(10, false),
  DELETED(11, true),

  /** Engine-emitted event; no command counterpart. */
  LIMIT_EXCEEDED(12, true);

  private final short value;
  private final boolean event;

  AgentInstanceIntent(final int value, final boolean event) {
    this.value = (short) value;
    this.event = event;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return event;
  }
}
