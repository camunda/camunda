/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect, vi, beforeEach} from 'vitest';
import {generateWidgets} from './llm';
import type {WidgetConfig} from './types';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const MOCK_WIDGET: WidgetConfig = {
  id: 'w-abc-123',
  type: 'metric',
  title: 'Running Instances',
  query: {
    endpoint: '/v2/process-instances/search',
    method: 'POST',
    body: {filter: {state: 'ACTIVE'}},
  },
  field: 'page.totalItems',
};

/**
 * Build the minimal Anthropic tool-use response envelope that llm.ts is
 * expected to parse.  The LLM returns widget configs via a tool call so the
 * JSON shape is deterministic.
 */
function makeAnthropicToolUseResponse(widgets: WidgetConfig[]) {
  return {
    id: 'msg_01',
    type: 'message',
    role: 'assistant',
    content: [
      {
        type: 'tool_use',
        id: 'tu_01',
        name: 'create_widgets',
        input: {widgets},
      },
    ],
    model: 'claude-3-5-haiku-20241022',
    stop_reason: 'tool_use',
    usage: {input_tokens: 10, output_tokens: 20},
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('generateWidgets', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  it('should throw a clear error when apiKey is undefined', async () => {
    // given
    const prompt = 'show me active process instances';

    // when / then
    await expect(generateWidgets(prompt, undefined)).rejects.toThrow(
      /api key/i,
    );
  });

  it('should call Anthropic API with the prompt and return parsed widget configs', async () => {
    // given
    const prompt = 'show me active process instances';
    const apiKey = 'sk-ant-test-key';
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => makeAnthropicToolUseResponse([MOCK_WIDGET]),
    });
    vi.stubGlobal('fetch', mockFetch);

    // when
    const result = await generateWidgets(prompt, apiKey);

    // then – the request must reach the Anthropic messages endpoint
    expect(mockFetch).toHaveBeenCalledOnce();
    const [url, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(url).toContain('anthropic');
    expect(url).toContain('messages');

    // request body must carry the user prompt and a tool definition
    const body = JSON.parse(init.body as string) as Record<string, unknown>;
    const messages = body['messages'] as Array<{role: string; content: string}>;
    expect(messages).toBeInstanceOf(Array);
    const userMsg = messages.find((m) => m.role === 'user');
    expect(userMsg?.content).toContain(prompt);
    expect(body['tools']).toBeInstanceOf(Array);
    expect((body['tools'] as unknown[]).length).toBeGreaterThan(0);

    // result is the parsed array
    expect(result).toEqual([MOCK_WIDGET]);
  });

  it('should return an array of WidgetConfig with at least one item', async () => {
    // given
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () =>
        makeAnthropicToolUseResponse([
          MOCK_WIDGET,
          {...MOCK_WIDGET, id: 'w-2'},
        ]),
    });
    vi.stubGlobal('fetch', mockFetch);

    // when
    const result = await generateWidgets('show me two widgets', 'sk-ant-key');

    // then
    expect(result.length).toBeGreaterThanOrEqual(1);
    result.forEach((w) => {
      expect(w).toHaveProperty('id');
      expect(w).toHaveProperty('type');
      expect(w).toHaveProperty('title');
      expect(w).toHaveProperty('query');
    });
  });

  it('should propagate API errors with a useful message', async () => {
    // given
    const mockFetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      json: async () => ({
        error: {type: 'authentication_error', message: 'invalid api key'},
      }),
    });
    vi.stubGlobal('fetch', mockFetch);

    // when / then
    await expect(generateWidgets('anything', 'bad-key')).rejects.toThrow(
      /401|authentication|invalid/i,
    );
  });
});
