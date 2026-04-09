/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Types representing the reconstructed conversation model from the raw
 * `agentContext` process variable that flows through an AI Agent AHSP element.
 *
 * The agentContext variable is opaque to the Zeebe engine—its shape is defined
 * by the AI Agent connector (io.camunda.agenticai:aiagent-job-worker).  The
 * types below are best-effort and intentionally permissive so the spike can
 * handle both known and unexpected payload shapes.
 */

// ---------- Document reference (pointer into Camunda Document Store) ---------

interface DocumentReference {
  documentId: string;
  contentHash?: string;
  metadata?: {
    contentType?: string;
    fileName?: string;
    expiresAt?: string;
    size?: number;
    processDefinitionId?: string;
    processInstanceKey?: string;
    customProperties?: Record<string, unknown>;
  };
}

// ---------- Conversation-level types ----------------------------------------

type MessageRole =
  | 'system'
  | 'user'
  | 'assistant'
  | 'tool'
  | 'tool_call_result';

interface ToolDefinition {
  name: string;
  description?: string;
  inputSchema?: Record<string, unknown>;
}

interface ToolCall {
  id?: string;
  name: string;
  arguments?: Record<string, unknown> | string;
}

interface ToolResult {
  id?: string;
  name?: string;
  content: string | Record<string, unknown>;
}

interface ConversationMessage {
  role: MessageRole;
  content?: string;
  toolCalls?: ToolCall[];
  toolResult?: ToolResult;
  thinking?: string;
  timestamp?: string;
}

interface IterationMetrics {
  inputTokens?: number;
  outputTokens?: number;
  totalTokens?: number;
  durationMs?: number;
}

interface AgentIteration {
  index: number;
  reasoning?: string;
  decision: 'tool_call' | 'direct_response' | 'unknown';
  toolCalls: ToolCall[];
  toolResults: ToolResult[];
  assistantMessage?: string;
  metrics?: IterationMetrics;
  timestamp?: string;
}

// ---------- Conversation document reference ---------------------------------

/**
 * When conversation.type === "camunda-document", the conversation messages
 * are stored in the Document Store rather than inline.
 */
interface ConversationDocumentReference {
  storeId?: string;
  documentId: string;
  contentHash?: string;
  metadata?: {
    contentType?: string;
    size?: number;
    fileName?: string;
    customProperties?: Record<string, unknown>;
  };
}

// ---------- Top-level reconstructed model -----------------------------------

interface AgentConversationModel {
  /** Whether the raw value was inline JSON or a document reference. */
  storageType: 'inline' | 'document_reference' | 'unknown';
  /** If storageType === 'document_reference', the parsed reference. */
  documentReference?: DocumentReference;
  /**
   * When conversation.type === "camunda-document", the document ref for
   * the current conversation content and any previous snapshots.
   */
  conversationDocument?: ConversationDocumentReference;
  previousDocuments?: ConversationDocumentReference[];
  /** Whether the document content has been resolved and merged. */
  documentResolved?: boolean;
  /** Raw payload size in characters (for perf analysis). */
  rawSize: number;
  /** Whether the JSON value was truncated by the variables API. */
  isTruncated: boolean;
  /** Parsing warnings / edge-case notes surfaced by the parser. */
  warnings: string[];

  // Agent state (e.g. "READY", "RUNNING")
  state?: string;
  /** Conversation ID from the connector runtime. */
  conversationId?: string;
  /** Conversation storage type from the connector (e.g. "in-process", "camunda-document"). */
  conversationType?: string;

  // Provider / model metadata (best-effort extraction)
  provider?: string;
  model?: string;

  // Prompts
  systemPrompt?: string;
  userPrompt?: string;

  // Tool definitions available to the agent
  toolDefinitions?: ToolDefinition[];

  // Iteration trail
  iterations: AgentIteration[];

  // Aggregate metrics (if present at top level)
  totalModelCalls?: number;
  totalIterations?: number;
  totalInputTokens?: number;
  totalOutputTokens?: number;
  totalTokens?: number;

  // Full message history (raw, for console dump)
  messages: ConversationMessage[];
}

export type {
  AgentConversationModel,
  AgentIteration,
  ConversationDocumentReference,
  ToolDefinition,
  ConversationMessage,
  DocumentReference,
  IterationMetrics,
  MessageRole,
  ToolCall,
  ToolResult,
};
