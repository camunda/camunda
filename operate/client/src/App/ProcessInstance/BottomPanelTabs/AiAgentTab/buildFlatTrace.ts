/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  AgentElementData,
  ConversationMessage,
} from 'modules/contexts/agentData.types';

export type FlatTraceStep =
  | {kind: 'user'; key: string; content: string[]; timestamp?: string}
  | {
      kind: 'assistant';
      key: string;
      content: string[];
      timestamp?: string;
      tokens?: number;
      tokensInput?: number;
      tokensOutput?: number;
      durationMs?: number;
      toolNames: string[];
    }
  | {
      kind: 'tool';
      key: string;
      name: string;
      input: Record<string, unknown>;
      output?: unknown;
      hasInstance: boolean;
      durationMs?: number;
    };

const safeParse = (raw: string): unknown => {
  try {
    return JSON.parse(raw);
  } catch {
    return raw;
  }
};

const diffMs = (from?: string, to?: string): number | undefined => {
  if (!from || !to) {
    return undefined;
  }
  const ms = new Date(to).getTime() - new Date(from).getTime();
  return Number.isFinite(ms) && ms >= 0 ? ms : undefined;
};

// Derive a flat, chronologically-ordered trace from the agent conversation.
// Tool calls become their own steps (one per call), with output + duration
// pulled in order from the tool_call_result messages that follow the assistant
// message. Assistant token counts come from the matching iteration.
export function buildFlatTrace(agentData: AgentElementData): FlatTraceStep[] {
  const msgs = (agentData.conversation ?? []).filter(
    (m) => m.role !== 'system',
  );
  const steps: FlatTraceStep[] = [];
  let i = 0;

  const iterationFor = (n?: number) => {
    if (n === undefined) {
      return undefined;
    }
    return agentData.iterations[n - 1];
  };

  const tokensForIteration = (n?: number): number | undefined => {
    const it = iterationFor(n);
    if (!it) {
      return undefined;
    }
    return it.tokenUsage.input + it.tokenUsage.output;
  };

  const resultContent = (m: ConversationMessage): string | undefined =>
    m.toolResults && m.toolResults.length > 0
      ? m.toolResults[0]!.content
      : undefined;

  while (i < msgs.length) {
    const m = msgs[i]!;

    if (m.role === 'user') {
      steps.push({
        kind: 'user',
        key: `user-${i}`,
        content: m.content,
        timestamp: m.timestamp,
      });
      i += 1;
      continue;
    }

    if (m.role === 'assistant') {
      const calls = m.toolCalls ?? [];
      // Gather the following tool_call_result messages, in order, to pair with
      // this assistant's tool calls.
      const results: ConversationMessage[] = [];
      let r = i + 1;
      while (r < msgs.length && results.length < calls.length) {
        if (msgs[r]!.role === 'tool_call_result') {
          results.push(msgs[r]!);
        } else if (msgs[r]!.role === 'assistant' || msgs[r]!.role === 'user') {
          break;
        }
        r += 1;
      }

      const firstResultTs = results[0]?.timestamp;
      const iter = iterationFor(m.iterationNumber);
      steps.push({
        kind: 'assistant',
        key: `assistant-${i}`,
        content: m.content,
        timestamp: m.timestamp,
        tokens: tokensForIteration(m.iterationNumber),
        tokensInput: iter?.tokenUsage.input,
        tokensOutput: iter?.tokenUsage.output,
        durationMs: diffMs(m.timestamp, firstResultTs),
        toolNames: calls.map((c) => c.name),
      });

      calls.forEach((call, idx) => {
        const result = results[idx];
        const raw = result ? resultContent(result) : undefined;
        steps.push({
          kind: 'tool',
          key: `tool-${i}-${idx}-${call.id}`,
          name: call.name,
          input: call.arguments,
          output: raw !== undefined ? safeParse(raw) : undefined,
          hasInstance: raw !== undefined,
          durationMs: diffMs(m.timestamp, result?.timestamp),
        });
      });

      i = Math.max(i + 1, r);
      continue;
    }

    // Stray tool_call_result not consumed above — skip defensively.
    i += 1;
  }

  return steps;
}
