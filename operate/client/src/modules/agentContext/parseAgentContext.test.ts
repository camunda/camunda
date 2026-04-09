/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {parseAgentContext, mergeDocumentContent} from './parseAgentContext';

describe('parseAgentContext', () => {
  it('should parse real connector agentContext with conversation.messages', () => {
    // given — real payload from the AI Agent connector
    const raw = JSON.stringify({
      state: 'READY',
      metrics: {
        modelCalls: 2,
        tokenUsage: {
          inputTokenCount: 1121,
          outputTokenCount: 39,
        },
      },
      toolDefinitions: [
        {
          name: 'Jokes_API',
          description: 'Fetches a random joke from a Jokes REST API',
          inputSchema: {properties: {}, type: 'object', required: []},
        },
        {
          name: 'GetDateAndTime',
          description: 'Returns the current date and time.',
          inputSchema: {properties: {}, type: 'object', required: []},
        },
      ],
      conversation: {
        type: 'in-process',
        conversationId: '776b2278-f1e3-459e-92fe-0db6bdbe5522',
        messages: [
          {
            role: 'system',
            content: [{type: 'text', text: 'You are a helpful agent.'}],
          },
          {
            role: 'user',
            content: [{type: 'text', text: 'tell me a joke'}],
            metadata: {timestamp: '2026-04-08T10:16:21.229187334Z'},
          },
          {
            role: 'assistant',
            toolCalls: [{id: 'call_PXuJ', name: 'Jokes_API', arguments: {}}],
            metadata: {
              timestamp: '2026-04-08T10:16:23.025058543Z',
              framework: {
                finishReason: 'TOOL_EXECUTION',
                tokenUsage: {inputTokenCount: 540, outputTokenCount: 11},
              },
            },
          },
          {
            role: 'tool_call_result',
            results: [
              {
                id: 'call_PXuJ',
                name: 'Jokes_API',
                content: 'Student loans joke punchline.',
              },
            ],
            metadata: {timestamp: '2026-04-08T10:16:24.71160746Z'},
          },
          {
            role: 'assistant',
            content: [{type: 'text', text: "Here's a joke for you: ..."}],
            metadata: {
              timestamp: '2026-04-08T10:16:26.254920711Z',
              framework: {
                finishReason: 'STOP',
                tokenUsage: {inputTokenCount: 581, outputTokenCount: 28},
              },
            },
          },
        ],
      },
    });

    // when
    const result = parseAgentContext(raw, false);

    // then
    expect(result.storageType).toBe('inline');
    expect(result.isTruncated).toBe(false);
    expect(result.warnings).toHaveLength(0);
    expect(result.state).toBe('READY');
    expect(result.conversationId).toBe('776b2278-f1e3-459e-92fe-0db6bdbe5522');
    expect(result.toolDefinitions).toHaveLength(2);
    expect(result.toolDefinitions?.[0].name).toBe('Jokes_API');

    // Metrics from top-level metrics.tokenUsage
    expect(result.totalModelCalls).toBe(2);
    expect(result.totalInputTokens).toBe(1121);
    expect(result.totalOutputTokens).toBe(39);

    // Messages: content arrays resolved to strings
    expect(result.messages).toHaveLength(5);
    expect(result.systemPrompt).toBe('You are a helpful agent.');
    expect(result.userPrompt).toBe('tell me a joke');

    // Iterations
    expect(result.iterations).toHaveLength(2);

    // Iteration 0: tool call
    expect(result.iterations[0].decision).toBe('tool_call');
    expect(result.iterations[0].toolCalls).toHaveLength(1);
    expect(result.iterations[0].toolCalls[0].name).toBe('Jokes_API');
    expect(result.iterations[0].toolCalls[0].id).toBe('call_PXuJ');
    expect(result.iterations[0].toolResults).toHaveLength(1);
    expect(result.iterations[0].toolResults[0].content).toBe(
      'Student loans joke punchline.',
    );
    expect(result.iterations[0].timestamp).toBe(
      '2026-04-08T10:16:23.025058543Z',
    );

    // Iteration 1: direct response
    expect(result.iterations[1].decision).toBe('direct_response');
    expect(result.iterations[1].assistantMessage).toContain("Here's a joke");
    expect(result.iterations[1].timestamp).toBe(
      '2026-04-08T10:16:26.254920711Z',
    );
  });

  it('should parse inline conversation with flat messages array', () => {
    // given — OpenAI-style flat messages
    const raw = JSON.stringify({
      messages: [
        {role: 'system', content: 'You are a helpful agent.'},
        {role: 'user', content: 'What is the order status for #123?'},
        {
          role: 'assistant',
          content:
            '<thinking>\n<context>User wants order status</context>\n<reflection>I should call getOrderStatus</reflection>\n</thinking>',
          tool_calls: [
            {
              id: 'call_1',
              function: {
                name: 'getOrderStatus',
                arguments: '{"orderId": "123"}',
              },
            },
          ],
        },
        {
          role: 'tool',
          tool_call_id: 'call_1',
          name: 'getOrderStatus',
          content: '{"status": "shipped", "eta": "2026-04-10"}',
        },
        {
          role: 'assistant',
          content:
            'Order #123 has been shipped and will arrive by April 10, 2026.',
        },
      ],
      provider: 'anthropic',
      model: 'claude-3-5-sonnet-20240620',
    });

    // when
    const result = parseAgentContext(raw, false);

    // then
    expect(result.storageType).toBe('inline');
    expect(result.isTruncated).toBe(false);
    expect(result.warnings).toHaveLength(0);
    expect(result.provider).toBe('anthropic');
    expect(result.model).toBe('claude-3-5-sonnet-20240620');
    expect(result.systemPrompt).toBe('You are a helpful agent.');
    expect(result.userPrompt).toBe('What is the order status for #123?');
    expect(result.messages).toHaveLength(5);
    expect(result.iterations).toHaveLength(2);

    // First iteration: tool call
    expect(result.iterations[0].decision).toBe('tool_call');
    expect(result.iterations[0].toolCalls).toHaveLength(1);
    expect(result.iterations[0].toolCalls[0].name).toBe('getOrderStatus');
    expect(result.iterations[0].reasoning).toContain('User wants order status');
    expect(result.iterations[0].toolResults).toHaveLength(1);

    // Second iteration: direct response
    expect(result.iterations[1].decision).toBe('direct_response');
    expect(result.iterations[1].assistantMessage).toContain('shipped');
  });

  it('should detect document reference', () => {
    // given
    const raw = JSON.stringify({
      documentId: 'doc-abc-123',
      contentHash: 'sha256:deadbeef',
      metadata: {
        contentType: 'application/json',
        expiresAt: '2099-12-31T23:59:59Z',
        size: 15000,
      },
    });

    // when
    const result = parseAgentContext(raw, false);

    // then
    expect(result.storageType).toBe('document_reference');
    expect(result.documentReference?.documentId).toBe('doc-abc-123');
    expect(result.warnings).toEqual(
      expect.arrayContaining([expect.stringContaining('document reference')]),
    );
    expect(result.iterations).toHaveLength(0);
  });

  it('should detect expired document reference', () => {
    // given
    const raw = JSON.stringify({
      documentId: 'doc-expired',
      metadata: {expiresAt: '2020-01-01T00:00:00Z'},
    });

    // when
    const result = parseAgentContext(raw, false);

    // then
    expect(result.storageType).toBe('document_reference');
    expect(result.warnings).toEqual(
      expect.arrayContaining([expect.stringContaining('expired')]),
    );
  });

  it('should handle truncated values', () => {
    // given
    const raw = JSON.stringify({messages: []});

    // when
    const result = parseAgentContext(raw, true);

    // then
    expect(result.isTruncated).toBe(true);
    expect(result.warnings).toEqual(
      expect.arrayContaining([expect.stringContaining('truncated')]),
    );
  });

  it('should handle invalid JSON gracefully', () => {
    // given
    const raw = '{not valid json...';

    // when
    const result = parseAgentContext(raw, false);

    // then
    expect(result.storageType).toBe('unknown');
    expect(result.warnings).toEqual(
      expect.arrayContaining([expect.stringContaining('Failed to parse')]),
    );
    expect(result.iterations).toHaveLength(0);
  });

  it('should handle empty messages', () => {
    // given
    const raw = JSON.stringify({someOtherField: 'value'});

    // when
    const result = parseAgentContext(raw, false);

    // then
    expect(result.storageType).toBe('inline');
    expect(result.warnings).toEqual(
      expect.arrayContaining([
        expect.stringContaining('No conversation messages found'),
      ]),
    );
  });

  it('should parse root-level message array', () => {
    // given
    const raw = JSON.stringify([
      {role: 'user', content: 'Hello'},
      {role: 'assistant', content: 'Hi there!'},
    ]);

    // when
    const result = parseAgentContext(raw, false);

    // then
    expect(result.storageType).toBe('inline');
    expect(result.messages).toHaveLength(2);
    expect(result.iterations).toHaveLength(1);
    expect(result.iterations[0].decision).toBe('direct_response');
  });

  it('should warn on large payloads', () => {
    // given
    const largeContent = 'x'.repeat(600_000);
    const raw = JSON.stringify({messages: [], data: largeContent});

    // when
    const result = parseAgentContext(raw, false);

    // then
    expect(result.warnings).toEqual(
      expect.arrayContaining([expect.stringContaining('Large agentContext')]),
    );
  });

  it('should handle wrapped document reference', () => {
    // given
    const raw = JSON.stringify({
      storeId: 'in-memory',
      storeReference: {
        documentId: 'doc-wrapped-456',
        contentHash: 'sha256:beef',
      },
    });

    // when
    const result = parseAgentContext(raw, false);

    // then
    expect(result.storageType).toBe('document_reference');
    expect(result.documentReference?.documentId).toBe('doc-wrapped-456');
  });

  it('should extract metrics from top-level fields', () => {
    // given
    const raw = JSON.stringify({
      messages: [
        {role: 'user', content: 'test'},
        {role: 'assistant', content: 'response'},
      ],
      metrics: {
        modelCalls: 3,
        tokenUsage: {
          inputTokenCount: 500,
          outputTokenCount: 200,
        },
      },
    });

    // when
    const result = parseAgentContext(raw, false);

    // then
    expect(result.totalModelCalls).toBe(3);
    expect(result.totalInputTokens).toBe(500);
    expect(result.totalOutputTokens).toBe(200);
  });

  it('should detect camunda-document conversation type', () => {
    // given — real payload with camunda-document conversation storage
    const raw = JSON.stringify({
      state: 'READY',
      metrics: {
        modelCalls: 2,
        tokenUsage: {inputTokenCount: 1119, outputTokenCount: 36},
      },
      toolDefinitions: [
        {
          name: 'Jokes_API',
          description: 'Fetches a random joke',
          inputSchema: {type: 'object', properties: {}, required: []},
        },
      ],
      conversation: {
        type: 'camunda-document',
        conversationId: 'c2b7fd0a-7176-47e4-ac56-860ba057ca20',
        document: {
          storeId: 'in-memory',
          documentId: '7e3613f6-c290-4cb7-9334-b456c0995278',
          contentHash:
            'c18750aa0be7dc9471f0d6da3462329da3dbc7f4d80ad28e1754f3dfa8aad030',
          metadata: {
            contentType: 'application/json',
            size: 2890,
            fileName: 'AI_Agent_conversation.json',
            customProperties: {
              conversationId: 'c2b7fd0a-7176-47e4-ac56-860ba057ca20',
            },
          },
          'camunda.document.type': 'camunda',
        },
        previousDocuments: [
          {
            storeId: 'in-memory',
            documentId: '25133e07-da11-42b7-97bc-fed09e469a7f',
            contentHash: '524838db',
            metadata: {
              contentType: 'application/json',
              size: 1865,
              fileName: 'AI_Agent_conversation.json',
            },
          },
        ],
      },
    });

    // when
    const result = parseAgentContext(raw, false);

    // then
    expect(result.storageType).toBe('document_reference');
    expect(result.conversationType).toBe('camunda-document');
    expect(result.documentResolved).toBe(false);
    expect(result.conversationDocument).toBeDefined();
    expect(result.conversationDocument?.documentId).toBe(
      '7e3613f6-c290-4cb7-9334-b456c0995278',
    );
    expect(result.conversationDocument?.storeId).toBe('in-memory');
    expect(result.conversationDocument?.metadata?.size).toBe(2890);
    expect(result.conversationDocument?.metadata?.fileName).toBe(
      'AI_Agent_conversation.json',
    );
    expect(result.previousDocuments).toHaveLength(1);
    expect(result.previousDocuments?.[0].documentId).toBe(
      '25133e07-da11-42b7-97bc-fed09e469a7f',
    );

    // Top-level metadata should still be extracted
    expect(result.state).toBe('READY');
    expect(result.conversationId).toBe('c2b7fd0a-7176-47e4-ac56-860ba057ca20');
    expect(result.totalModelCalls).toBe(2);
    expect(result.totalInputTokens).toBe(1119);
    expect(result.totalOutputTokens).toBe(36);
    expect(result.toolDefinitions).toHaveLength(1);

    // No messages yet (not resolved)
    expect(result.iterations).toHaveLength(0);
    expect(result.messages).toHaveLength(0);
  });

  it('should merge document content into model', () => {
    // given — a model with document_reference storage type
    const raw = JSON.stringify({
      state: 'READY',
      metrics: {
        modelCalls: 2,
        tokenUsage: {inputTokenCount: 100, outputTokenCount: 50},
      },
      conversation: {
        type: 'camunda-document',
        conversationId: 'conv-123',
        document: {
          storeId: 'in-memory',
          documentId: 'doc-123',
          contentHash: 'abc',
          metadata: {size: 500},
        },
      },
    });
    const model = parseAgentContext(raw, false);

    // Document content (what would be fetched from the Document Store)
    const documentContent = JSON.stringify([
      {
        role: 'system',
        content: [{type: 'text', text: 'You are a helpful agent.'}],
      },
      {
        role: 'user',
        content: [{type: 'text', text: 'tell me a joke'}],
        metadata: {timestamp: '2026-04-08T10:16:21Z'},
      },
      {
        role: 'assistant',
        toolCalls: [{id: 'call_1', name: 'Jokes_API', arguments: {}}],
        metadata: {timestamp: '2026-04-08T10:16:23Z'},
      },
      {
        role: 'tool_call_result',
        results: [{id: 'call_1', name: 'Jokes_API', content: 'A joke!'}],
        metadata: {timestamp: '2026-04-08T10:16:24Z'},
      },
      {
        role: 'assistant',
        content: [{type: 'text', text: "Here's a joke: A joke!"}],
        metadata: {timestamp: '2026-04-08T10:16:26Z'},
      },
    ]);

    // when
    const merged = mergeDocumentContent(model, documentContent);

    // then
    expect(merged.documentResolved).toBe(true);
    expect(merged.messages).toHaveLength(5);
    expect(merged.systemPrompt).toBe('You are a helpful agent.');
    expect(merged.userPrompt).toBe('tell me a joke');
    expect(merged.iterations).toHaveLength(2);
    expect(merged.iterations[0].decision).toBe('tool_call');
    expect(merged.iterations[0].toolCalls[0].name).toBe('Jokes_API');
    expect(merged.iterations[1].decision).toBe('direct_response');

    // Top-level metadata should still be present
    expect(merged.state).toBe('READY');
    expect(merged.totalModelCalls).toBe(2);
    expect(merged.totalInputTokens).toBe(100);
  });

  it('should handle mergeDocumentContent with invalid JSON', () => {
    // given
    const raw = JSON.stringify({
      state: 'READY',
      conversation: {
        type: 'camunda-document',
        conversationId: 'conv-456',
        document: {documentId: 'doc-456'},
      },
    });
    const model = parseAgentContext(raw, false);

    // when
    const merged = mergeDocumentContent(model, '{invalid json');

    // then
    expect(merged.documentResolved).toBe(true);
    expect(merged.messages).toHaveLength(0);
    expect(merged.warnings).toEqual(
      expect.arrayContaining([
        expect.stringContaining('Failed to parse document content'),
      ]),
    );
  });
});
