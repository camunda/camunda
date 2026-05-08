/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect, vi, beforeEach} from 'vitest';
import {generateWidgets, type BedrockCredentials} from './llm';
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

const VALID_CREDENTIALS: BedrockCredentials = {
  arn: 'arn:aws:bedrock:us-east-1:123456789012:inference-profile/my-profile',
  accessKeyId: 'AKIAIOSFODNN7EXAMPLE',
  secretAccessKey: 'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY',
};

/**
 * Build the Bedrock/Anthropic tool-use response envelope that llm.ts is
 * expected to parse. Bedrock returns the same JSON shape as the direct
 * Anthropic Messages API.
 */
function makeToolUseResponse(widgets: WidgetConfig[]) {
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

  it('should throw a clear error when credentials are undefined', async () => {
    // given
    const prompt = 'show me active process instances';

    // when / then
    await expect(generateWidgets(prompt, undefined)).rejects.toThrow(
      /VITE_AWS_BEDROCK_ARN/i,
    );
  });

  it('should throw a clear error when arn is missing', async () => {
    // given
    const creds: BedrockCredentials = {
      ...VALID_CREDENTIALS,
      arn: '',
    };

    // when / then
    await expect(generateWidgets('any prompt', creds)).rejects.toThrow(
      /VITE_AWS_BEDROCK_ARN/i,
    );
  });

  it('should throw a clear error when accessKeyId is missing', async () => {
    // given
    const creds: BedrockCredentials = {
      ...VALID_CREDENTIALS,
      accessKeyId: '',
    };

    // when / then
    await expect(generateWidgets('any prompt', creds)).rejects.toThrow(
      /VITE_AWS_BEDROCK_ARN/i,
    );
  });

  it('should throw a clear error when secretAccessKey is missing', async () => {
    // given
    const creds: BedrockCredentials = {
      ...VALID_CREDENTIALS,
      secretAccessKey: '',
    };

    // when / then
    await expect(generateWidgets('any prompt', creds)).rejects.toThrow(
      /VITE_AWS_BEDROCK_ARN/i,
    );
  });

  it('should throw a clear error when ARN does not match expected format', async () => {
    // given
    const creds: BedrockCredentials = {
      ...VALID_CREDENTIALS,
      arn: 'not-a-valid-arn',
    };

    // when / then
    await expect(generateWidgets('any prompt', creds)).rejects.toThrow(
      /invalid bedrock arn/i,
    );
  });

  it('should call Bedrock API with the prompt and return parsed widget configs', async () => {
    // given
    const prompt = 'show me active process instances';
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => makeToolUseResponse([MOCK_WIDGET]),
    });
    vi.stubGlobal('fetch', mockFetch);

    // when
    const result = await generateWidgets(prompt, VALID_CREDENTIALS);

    // then – the request must reach the Bedrock runtime endpoint
    expect(mockFetch).toHaveBeenCalledOnce();
    const [url, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(url).toContain('bedrock-runtime.us-east-1.amazonaws.com');
    expect(url).toContain('/model/');
    expect(url).toContain('/invoke');

    // method must be POST
    expect(init.method).toBe('POST');

    // Authorization header must use SigV4
    const headers = init.headers as Record<string, string>;
    expect(headers['Authorization']).toMatch(/^AWS4-HMAC-SHA256 /);

    // request body must use bedrock-specific anthropic_version (not a header)
    const body = JSON.parse(init.body as string) as Record<string, unknown>;
    expect(body['anthropic_version']).toBe('bedrock-2023-05-31');
    // no "model" field — it is in the URL
    expect(body).not.toHaveProperty('model');

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
        makeToolUseResponse([MOCK_WIDGET, {...MOCK_WIDGET, id: 'w-2'}]),
    });
    vi.stubGlobal('fetch', mockFetch);

    // when
    const result = await generateWidgets(
      'show me two widgets',
      VALID_CREDENTIALS,
    );

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
        message: 'The security token included in the request is invalid.',
      }),
    });
    vi.stubGlobal('fetch', mockFetch);

    // when / then
    await expect(
      generateWidgets('anything', VALID_CREDENTIALS),
    ).rejects.toThrow(/401|security token|invalid/i);
  });

  it('should throw when the response contains no tool_use block', async () => {
    // given
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        content: [{type: 'text', text: 'I cannot help with that.'}],
        stop_reason: 'end_turn',
      }),
    });
    vi.stubGlobal('fetch', mockFetch);

    // when / then
    await expect(
      generateWidgets('anything', VALID_CREDENTIALS),
    ).rejects.toThrow(/tool_use/i);
  });

  it('should throw when the tool_use input fails Zod validation', async () => {
    // given — response is valid JSON but has wrong widget shape
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        content: [
          {
            type: 'tool_use',
            id: 'tu_01',
            name: 'create_widgets',
            input: {widgets: [{broken: true}]},
          },
        ],
        stop_reason: 'tool_use',
      }),
    });
    vi.stubGlobal('fetch', mockFetch);

    // when / then
    await expect(
      generateWidgets('anything', VALID_CREDENTIALS),
    ).rejects.toThrow(/malformed/i);
  });

  it('should URL-encode the ARN in the request path', async () => {
    // given
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => makeToolUseResponse([MOCK_WIDGET]),
    });
    vi.stubGlobal('fetch', mockFetch);

    // when
    await generateWidgets('test', VALID_CREDENTIALS);

    // then – colons and slashes in the ARN must be percent-encoded in the URL path
    const [url] = mockFetch.mock.calls[0] as [string, RequestInit];
    const encodedArn = encodeURIComponent(VALID_CREDENTIALS.arn);
    expect(url).toContain(encodedArn);
  });
});
