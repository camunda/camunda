/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  AgentConversationModel,
  AgentIteration,
  ConversationDocumentReference,
  ConversationMessage,
  DocumentReference,
  ToolCall,
  ToolDefinition,
  ToolResult,
} from './types';

/**
 * Detects whether a raw variable value string is a Camunda Document Reference
 * rather than inline JSON conversation data.
 *
 * Document references follow the pattern defined in `document/api`:
 *   { "documentId": "...", "contentHash": "...", "metadata": {...} }
 *
 * Or may be wrapped:
 *   { "storeId": "...", "storeReference": { "documentId": "..." } }
 */
function tryParseDocumentReference(
  parsed: unknown,
): DocumentReference | undefined {
  if (typeof parsed !== 'object' || parsed === null) {
    return undefined;
  }

  const obj = parsed as Record<string, unknown>;

  // Direct document reference shape
  if (typeof obj.documentId === 'string') {
    return {
      documentId: obj.documentId,
      contentHash:
        typeof obj.contentHash === 'string' ? obj.contentHash : undefined,
      metadata:
        typeof obj.metadata === 'object' && obj.metadata !== null
          ? (obj.metadata as DocumentReference['metadata'])
          : undefined,
    };
  }

  // Wrapped reference shape (storeReference sub-object)
  if (
    typeof obj.storeReference === 'object' &&
    obj.storeReference !== null &&
    typeof (obj.storeReference as Record<string, unknown>).documentId ===
      'string'
  ) {
    const ref = obj.storeReference as Record<string, unknown>;
    return {
      documentId: ref.documentId as string,
      contentHash:
        typeof ref.contentHash === 'string' ? ref.contentHash : undefined,
      metadata:
        typeof ref.metadata === 'object' && ref.metadata !== null
          ? (ref.metadata as DocumentReference['metadata'])
          : undefined,
    };
  }

  return undefined;
}

/**
 * Best-effort extraction of the message array from various shapes the
 * agentContext may take.
 */
function extractMessages(parsed: unknown): ConversationMessage[] {
  if (Array.isArray(parsed)) {
    return parsed.map(normalizeMessage);
  }

  if (typeof parsed === 'object' && parsed !== null) {
    const obj = parsed as Record<string, unknown>;

    // Real connector shape: conversation.messages
    if (typeof obj.conversation === 'object' && obj.conversation !== null) {
      const conv = obj.conversation as Record<string, unknown>;
      if (Array.isArray(conv.messages)) {
        return (conv.messages as unknown[]).map(normalizeMessage);
      }
    }

    // Common wrapper keys the connector might use
    for (const key of ['messages', 'conversationHistory', 'history', 'chat']) {
      if (Array.isArray(obj[key])) {
        return (obj[key] as unknown[]).map(normalizeMessage);
      }
    }

    // Nested under context.messages or similar paths
    if (typeof obj.context === 'object' && obj.context !== null) {
      const ctx = obj.context as Record<string, unknown>;
      if (Array.isArray(ctx.messages)) {
        return (ctx.messages as unknown[]).map(normalizeMessage);
      }
    }
  }

  return [];
}

function normalizeMessage(raw: unknown): ConversationMessage {
  if (typeof raw !== 'object' || raw === null) {
    return {role: 'user', content: String(raw)};
  }

  const obj = raw as Record<string, unknown>;

  const rawRole = obj.role as string;
  const role = (
    ['system', 'user', 'assistant', 'tool', 'tool_call_result'] as const
  ).includes(
    rawRole as 'system' | 'user' | 'assistant' | 'tool' | 'tool_call_result',
  )
    ? (rawRole as ConversationMessage['role'])
    : 'user';

  const content = resolveContentField(obj.content);

  const toolCalls = Array.isArray(obj.tool_calls ?? obj.toolCalls)
    ? (((obj.tool_calls ?? obj.toolCalls) as unknown[]).map(
        normalizeToolCall,
      ) as ToolCall[])
    : undefined;

  // Handle both "tool" and "tool_call_result" roles
  const toolResults =
    role === 'tool'
      ? [normalizeToolResult(obj)]
      : role === 'tool_call_result' && Array.isArray(obj.results)
        ? (obj.results as unknown[]).map(normalizeToolResultEntry)
        : undefined;

  const thinking = extractThinking(content);

  // Timestamps may be at metadata.timestamp or directly on the message
  const metadata =
    typeof obj.metadata === 'object' && obj.metadata !== null
      ? (obj.metadata as Record<string, unknown>)
      : undefined;

  const timestamp =
    typeof obj.timestamp === 'string'
      ? obj.timestamp
      : typeof metadata?.timestamp === 'string'
        ? metadata.timestamp
        : undefined;

  return {
    role,
    content,
    toolCalls,
    toolResult: toolResults?.[0],
    thinking,
    timestamp,
  };
}

/**
 * Content can be a plain string or an array of content blocks:
 *   [{type: "text", text: "..."}]
 */
function resolveContentField(content: unknown): string | undefined {
  if (typeof content === 'string') {
    return content;
  }
  if (Array.isArray(content)) {
    return content
      .map((block) => {
        if (typeof block === 'string') {
          return block;
        }
        if (
          typeof block === 'object' &&
          block !== null &&
          typeof (block as Record<string, unknown>).text === 'string'
        ) {
          return (block as Record<string, unknown>).text as string;
        }
        return JSON.stringify(block);
      })
      .join('\n');
  }
  if (content !== undefined && content !== null) {
    return JSON.stringify(content);
  }
  return undefined;
}

function normalizeToolCall(raw: unknown): ToolCall {
  if (typeof raw !== 'object' || raw === null) {
    return {name: 'unknown'};
  }
  const obj = raw as Record<string, unknown>;
  const fn =
    typeof obj.function === 'object' && obj.function !== null
      ? (obj.function as Record<string, unknown>)
      : obj;

  return {
    id: typeof obj.id === 'string' ? obj.id : undefined,
    name: typeof fn.name === 'string' ? fn.name : 'unknown',
    arguments:
      typeof fn.arguments === 'string'
        ? fn.arguments
        : typeof fn.arguments === 'object' && fn.arguments !== null
          ? (fn.arguments as Record<string, unknown>)
          : undefined,
  };
}

function normalizeToolResult(obj: Record<string, unknown>): ToolResult {
  return {
    id:
      typeof obj.tool_call_id === 'string'
        ? obj.tool_call_id
        : typeof obj.id === 'string'
          ? obj.id
          : undefined,
    name: typeof obj.name === 'string' ? obj.name : undefined,
    content:
      typeof obj.content === 'string'
        ? obj.content
        : typeof obj.content === 'object' && obj.content !== null
          ? (obj.content as Record<string, unknown>)
          : '',
  };
}

/**
 * Normalize a single entry from the `results` array in a `tool_call_result`
 * message (real connector format).
 */
function normalizeToolResultEntry(raw: unknown): ToolResult {
  if (typeof raw !== 'object' || raw === null) {
    return {content: String(raw)};
  }
  const obj = raw as Record<string, unknown>;
  return {
    id: typeof obj.id === 'string' ? obj.id : undefined,
    name: typeof obj.name === 'string' ? obj.name : undefined,
    content:
      typeof obj.content === 'string'
        ? obj.content
        : typeof obj.content === 'object' && obj.content !== null
          ? (obj.content as Record<string, unknown>)
          : '',
  };
}

/**
 * Extract <thinking>…</thinking> blocks from assistant messages,
 * following the system prompt pattern seen in the test fixtures.
 */
function extractThinking(content?: string): string | undefined {
  if (!content) {
    return undefined;
  }
  const match = content.match(/<thinking>([\s\S]*?)<\/thinking>/);
  return match?.[1]?.trim() || undefined;
}

/**
 * Build AgentIteration objects from the flat messages array.
 *
 * An "iteration" starts with each assistant message and includes any
 * subsequent tool / tool_call_result messages until the next assistant message.
 */
function buildIterations(messages: ConversationMessage[]): AgentIteration[] {
  const iterations: AgentIteration[] = [];
  let iterationIndex = 0;

  for (let i = 0; i < messages.length; i++) {
    const msg = messages[i];
    if (msg.role !== 'assistant') {
      continue;
    }

    const toolCalls = msg.toolCalls ?? [];
    const toolResults: ToolResult[] = [];

    // Collect subsequent tool results (both "tool" and "tool_call_result" roles)
    for (let j = i + 1; j < messages.length; j++) {
      const nextMsg = messages[j];
      if (
        (nextMsg.role === 'tool' || nextMsg.role === 'tool_call_result') &&
        nextMsg.toolResult
      ) {
        toolResults.push(nextMsg.toolResult);
      } else if (nextMsg.role === 'assistant') {
        break;
      }
    }

    const hasToolCalls = toolCalls.length > 0;

    iterations.push({
      index: iterationIndex++,
      reasoning: msg.thinking,
      decision: hasToolCalls ? 'tool_call' : 'direct_response',
      toolCalls,
      toolResults,
      assistantMessage: msg.content,
      timestamp: msg.timestamp,
    });
  }

  return iterations;
}

/**
 * Extract top-level metadata fields from the parsed agentContext object.
 */
function extractMetadata(parsed: unknown): {
  provider?: string;
  model?: string;
  systemPrompt?: string;
  userPrompt?: string;
  totalIterations?: number;
  totalInputTokens?: number;
  totalOutputTokens?: number;
  totalTokens?: number;
} {
  if (typeof parsed !== 'object' || parsed === null) {
    return {};
  }

  const obj = parsed as Record<string, unknown>;
  const meta: ReturnType<typeof extractMetadata> = {};

  // Provider / model (may be nested under provider, config, or at top level)
  meta.provider = findString(obj, ['provider', 'provider.type']);
  meta.model = findString(obj, [
    'model',
    'provider.model',
    'provider.anthropic.model.model',
    'provider.openai.model',
  ]);

  // Metrics at top level — real connector uses metrics.tokenUsage.{inputTokenCount,...}
  meta.totalIterations = findNumber(obj, [
    'totalIterations',
    'iterationCount',
    'metrics.iterations',
    'metrics.modelCalls',
  ]);
  meta.totalInputTokens = findNumber(obj, [
    'totalInputTokens',
    'metrics.tokenUsage.inputTokenCount',
    'metrics.inputTokens',
    'usage.input_tokens',
    'usage.prompt_tokens',
  ]);
  meta.totalOutputTokens = findNumber(obj, [
    'totalOutputTokens',
    'metrics.tokenUsage.outputTokenCount',
    'metrics.outputTokens',
    'usage.output_tokens',
    'usage.completion_tokens',
  ]);
  meta.totalTokens = findNumber(obj, [
    'totalTokens',
    'metrics.tokenUsage.totalTokenCount',
    'metrics.totalTokens',
    'usage.total_tokens',
  ]);

  // Derive totalTokens from input + output if not explicitly present
  if (
    meta.totalTokens === undefined &&
    meta.totalInputTokens !== undefined &&
    meta.totalOutputTokens !== undefined
  ) {
    meta.totalTokens = meta.totalInputTokens + meta.totalOutputTokens;
  }

  return meta;
}

function findString(
  obj: Record<string, unknown>,
  paths: string[],
): string | undefined {
  for (const path of paths) {
    const val = getNestedValue(obj, path);
    if (typeof val === 'string') {
      return val;
    }
  }
  return undefined;
}

function findNumber(
  obj: Record<string, unknown>,
  paths: string[],
): number | undefined {
  for (const path of paths) {
    const val = getNestedValue(obj, path);
    if (typeof val === 'number') {
      return val;
    }
  }
  return undefined;
}

function getNestedValue(obj: Record<string, unknown>, path: string): unknown {
  const parts = path.split('.');
  let current: unknown = obj;
  for (const part of parts) {
    if (typeof current !== 'object' || current === null) {
      return undefined;
    }
    current = (current as Record<string, unknown>)[part];
  }
  return current;
}

// ---------- Main entry point ------------------------------------------------

/**
 * Parse a raw `agentContext` variable value (string) into a structured
 * {@link AgentConversationModel} for spike-level visualization.
 *
 * @param rawValue  The variable's string value as returned by GET /v2/variables.
 * @param isTruncated  Whether the API indicated the value was truncated.
 */
function parseAgentContext(
  rawValue: string,
  isTruncated: boolean,
): AgentConversationModel {
  const warnings: string[] = [];
  const rawSize = rawValue.length;

  if (isTruncated) {
    warnings.push(
      `Variable value is truncated (${rawSize} chars). ` +
        'The conversation trail may be incomplete. ' +
        'A backend-resolving-references endpoint would avoid this limitation.',
    );
  }

  // Attempt JSON parse
  let parsed: unknown;
  try {
    parsed = JSON.parse(rawValue);
  } catch {
    warnings.push(
      'Failed to parse agentContext as JSON. Value may be corrupted or use an unsupported format.',
    );
    return {
      storageType: 'unknown',
      rawSize,
      isTruncated,
      warnings,
      iterations: [],
      messages: [],
    };
  }

  // Check for top-level document reference (entire agentContext is a doc ref)
  const docRef = tryParseDocumentReference(parsed);
  if (docRef) {
    warnings.push(
      `agentContext is a document reference (documentId: ${docRef.documentId}). ` +
        'The frontend cannot resolve this directly — a backend endpoint with ' +
        'document-store access would be required. Metadata shown is the reference only.',
    );
    if (docRef.metadata?.expiresAt) {
      const expiry = new Date(docRef.metadata.expiresAt);
      if (expiry < new Date()) {
        warnings.push(
          `Document reference has expired (expiresAt: ${docRef.metadata.expiresAt}). ` +
            'Even a backend resolver would need TTL handling or immutable snapshots.',
        );
      }
    }
    return {
      storageType: 'document_reference',
      documentReference: docRef,
      rawSize,
      isTruncated,
      warnings,
      iterations: [],
      messages: [],
    };
  }

  // Extract state, conversationId, toolDefinitions, and metrics from top-level
  const topLevel =
    typeof parsed === 'object' && parsed !== null
      ? (parsed as Record<string, unknown>)
      : ({} as Record<string, unknown>);

  const state = typeof topLevel.state === 'string' ? topLevel.state : undefined;
  const metadata = extractMetadata(parsed);
  const toolDefinitions = extractToolDefinitions(topLevel);

  const conversationObj =
    typeof topLevel.conversation === 'object' && topLevel.conversation !== null
      ? (topLevel.conversation as Record<string, unknown>)
      : undefined;
  const conversationId =
    typeof conversationObj?.conversationId === 'string'
      ? conversationObj.conversationId
      : undefined;
  const conversationType =
    typeof conversationObj?.type === 'string'
      ? conversationObj.type
      : undefined;

  // Shared base fields available regardless of conversation storage type
  const baseModel: Omit<
    AgentConversationModel,
    'storageType' | 'iterations' | 'messages' | 'systemPrompt' | 'userPrompt'
  > = {
    rawSize,
    isTruncated,
    warnings,
    state,
    conversationId,
    conversationType,
    toolDefinitions: toolDefinitions.length > 0 ? toolDefinitions : undefined,
    provider: metadata.provider,
    model: metadata.model,
    totalModelCalls: metadata.totalIterations,
    totalInputTokens: metadata.totalInputTokens,
    totalOutputTokens: metadata.totalOutputTokens,
    totalTokens: metadata.totalTokens,
  };

  // Check if conversation is stored as a Camunda document
  if (conversationType === 'camunda-document' && conversationObj) {
    const convDoc = extractConversationDocumentRef(conversationObj);
    const prevDocs = extractPreviousDocuments(conversationObj);

    if (convDoc) {
      return {
        ...baseModel,
        storageType: 'document_reference',
        conversationDocument: convDoc,
        previousDocuments: prevDocs.length > 0 ? prevDocs : undefined,
        documentResolved: false,
        iterations: [],
        messages: [],
      };
    }
  }

  // Inline conversation data — extract messages
  const messages = extractMessages(parsed);
  if (messages.length === 0) {
    warnings.push(
      'No conversation messages found in agentContext. ' +
        'The payload structure may not match any known message array pattern ' +
        '(tried: root array, .conversation.messages, .messages, .conversationHistory, .history, .chat, .context.messages).',
    );
  }

  const systemPrompt = messages.find((m) => m.role === 'system')?.content;
  const userPrompt = messages.find((m) => m.role === 'user')?.content;
  const iterations = buildIterations(messages);

  // Size warning for large payloads
  if (rawSize > 500_000) {
    warnings.push(
      `Large agentContext payload (${(rawSize / 1024).toFixed(0)} KB). ` +
        'Client-side parsing may impact performance. ' +
        'A backend endpoint could stream or paginate conversation data.',
    );
  }

  return {
    ...baseModel,
    storageType: 'inline',
    systemPrompt,
    userPrompt,
    iterations,
    totalIterations: iterations.length,
    messages,
  };
}

/**
 * Extract a ConversationDocumentReference from the conversation.document field.
 */
function extractConversationDocumentRef(
  conv: Record<string, unknown>,
): ConversationDocumentReference | undefined {
  const doc = conv.document;
  if (typeof doc !== 'object' || doc === null) {
    return undefined;
  }
  const d = doc as Record<string, unknown>;
  if (typeof d.documentId !== 'string') {
    return undefined;
  }
  return {
    documentId: d.documentId,
    storeId: typeof d.storeId === 'string' ? d.storeId : undefined,
    contentHash: typeof d.contentHash === 'string' ? d.contentHash : undefined,
    metadata:
      typeof d.metadata === 'object' && d.metadata !== null
        ? (d.metadata as ConversationDocumentReference['metadata'])
        : undefined,
  };
}

/**
 * Extract previous document snapshots from conversation.previousDocuments[].
 */
function extractPreviousDocuments(
  conv: Record<string, unknown>,
): ConversationDocumentReference[] {
  if (!Array.isArray(conv.previousDocuments)) {
    return [];
  }
  return (conv.previousDocuments as unknown[])
    .map((raw): ConversationDocumentReference | undefined => {
      if (typeof raw !== 'object' || raw === null) {
        return undefined;
      }
      const d = raw as Record<string, unknown>;
      if (typeof d.documentId !== 'string') {
        return undefined;
      }
      return {
        documentId: d.documentId,
        storeId: typeof d.storeId === 'string' ? d.storeId : undefined,
        contentHash:
          typeof d.contentHash === 'string' ? d.contentHash : undefined,
        metadata:
          typeof d.metadata === 'object' && d.metadata !== null
            ? (d.metadata as ConversationDocumentReference['metadata'])
            : undefined,
      };
    })
    .filter((d): d is ConversationDocumentReference => d !== undefined);
}

/**
 * Merge fetched document content (a JSON string containing conversation
 * messages) into an existing AgentConversationModel that was created
 * from a `camunda-document` type conversation.
 *
 * This is called as a second step after the document has been fetched
 * from the Document Store via `GET /v2/documents/{documentId}`.
 */
function mergeDocumentContent(
  model: AgentConversationModel,
  documentContent: string,
): AgentConversationModel {
  const warnings = [...model.warnings];

  let parsed: unknown;
  try {
    parsed = JSON.parse(documentContent);
  } catch {
    warnings.push(
      'Failed to parse document content as JSON. The conversation document may be corrupted.',
    );
    return {...model, warnings, documentResolved: true};
  }

  // The document content may be a messages array directly or wrapped
  let messages: ConversationMessage[];
  if (Array.isArray(parsed)) {
    messages = parsed.map(normalizeMessage);
  } else if (typeof parsed === 'object' && parsed !== null) {
    const obj = parsed as Record<string, unknown>;
    if (Array.isArray(obj.messages)) {
      messages = (obj.messages as unknown[]).map(normalizeMessage);
    } else {
      messages = extractMessages(parsed);
    }
  } else {
    warnings.push(
      'Document content is not a recognizable conversation format.',
    );
    return {...model, warnings, documentResolved: true};
  }

  if (messages.length === 0) {
    warnings.push(
      'No conversation messages found in the resolved document content.',
    );
  }

  const systemPrompt = messages.find((m) => m.role === 'system')?.content;
  const userPrompt = messages.find((m) => m.role === 'user')?.content;
  const iterations = buildIterations(messages);

  return {
    ...model,
    warnings,
    documentResolved: true,
    systemPrompt,
    userPrompt,
    iterations,
    totalIterations: iterations.length,
    messages,
  };
}

function extractToolDefinitions(
  obj: Record<string, unknown>,
): ToolDefinition[] {
  if (!Array.isArray(obj.toolDefinitions)) {
    return [];
  }
  return (obj.toolDefinitions as unknown[]).map((raw) => {
    if (typeof raw !== 'object' || raw === null) {
      return {name: 'unknown'};
    }
    const td = raw as Record<string, unknown>;
    return {
      name: typeof td.name === 'string' ? td.name : 'unknown',
      description:
        typeof td.description === 'string' ? td.description : undefined,
      inputSchema:
        typeof td.inputSchema === 'object' && td.inputSchema !== null
          ? (td.inputSchema as Record<string, unknown>)
          : undefined,
    };
  });
}

export {
  parseAgentContext,
  mergeDocumentContent,
  tryParseDocumentReference,
  extractMessages,
};
