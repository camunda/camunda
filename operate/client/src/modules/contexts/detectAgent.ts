/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import type {
  AgentElementData,
  AgentIteration,
  AgentToolCall,
  ConversationMessage,
} from 'modules/mock-server/agentDemoData';

/** Camunda AI Agent task definitions all start with this prefix. */
const AI_AGENT_TASK_DEFINITION_PREFIX = 'io.camunda.agenticai:aiagent';

function getTaskDefinitionType(bo: BusinessObject): string | undefined {
  const def = bo.extensionElements?.values?.find(
    (v) => v.$type === 'zeebe:taskDefinition',
  );
  return (def as {type?: string} | undefined)?.type;
}

function isAgentBusinessObject(bo: BusinessObject): boolean {
  const type = getTaskDefinitionType(bo);
  return (
    typeof type === 'string' && type.startsWith(AI_AGENT_TASK_DEFINITION_PREFIX)
  );
}

type DetectedAgent = {
  elementId: string;
  businessObject: BusinessObject;
  /** All descendant elements (useful for the ad-hoc subprocess pattern, where tools are children). */
  childElementIds: string[];
  /** Plain tool tasks discovered inside or alongside the agent — used to seed the placeholder. */
  tools: Array<{elementId: string; displayName: string}>;
};

function humanize(id: string): string {
  const spaced = id
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

function toolMeta(id: string, bo: BusinessObject | undefined) {
  const name = (bo as {name?: string} | undefined)?.name;
  return {
    elementId: id,
    displayName: name && name.length > 0 ? name : humanize(id),
  };
}

/**
 * Find the first element in the BPMN that is an AI agent (by its zeebe task
 * definition type). Returns the element id plus any child tool task ids so the
 * UI can surface them without hand-written mock data.
 */
function detectAgentElement(
  businessObjects: Record<string, BusinessObject> | undefined,
): DetectedAgent | null {
  if (!businessObjects) {
    return null;
  }

  for (const [id, bo] of Object.entries(businessObjects)) {
    if (!isAgentBusinessObject(bo)) {
      continue;
    }

    const childIds: string[] = [];
    const tools: Array<{elementId: string; displayName: string}> = [];

    // AI Agent ad-hoc subprocess: tools are direct children.
    if (bo.$type === 'bpmn:AdHocSubProcess' && Array.isArray(bo.flowElements)) {
      for (const child of bo.flowElements) {
        if (!child?.id) {
          continue;
        }
        childIds.push(child.id);
        if (
          child.$type === 'bpmn:ServiceTask' ||
          child.$type === 'bpmn:ScriptTask'
        ) {
          tools.push(toolMeta(child.id, child));
        }
      }
    }

    // AI Agent task: tools usually live in a sibling ad-hoc subprocess — look
    // for any ad-hoc subprocess in the same model and treat its service-task
    // children as tools.
    if (bo.$type === 'bpmn:ServiceTask' && tools.length === 0) {
      for (const [siblingId, siblingBo] of Object.entries(businessObjects)) {
        if (siblingId === id) {
          continue;
        }
        if (
          siblingBo.$type === 'bpmn:AdHocSubProcess' &&
          Array.isArray(siblingBo.flowElements)
        ) {
          for (const child of siblingBo.flowElements) {
            if (
              child?.id &&
              (child.$type === 'bpmn:ServiceTask' ||
                child.$type === 'bpmn:ScriptTask')
            ) {
              tools.push(toolMeta(child.id, child));
            }
          }
        }
      }
    }

    return {
      elementId: id,
      businessObject: bo,
      childElementIds: childIds,
      tools,
    };
  }

  return null;
}

/**
 * Build a fully-populated mock enrichment payload for a detected agent, scaled
 * to whichever tools live in its BPMN. Backend data for real agent instances
 * isn't available yet, so every panel on the Details view is populated with
 * plausible demo data. The placeholder mocks an in-flight "calling tools"
 * state so the live-execution UI (canvas overlay, Status accordion's active
 * tools list, pending tool outputs) has something to render against.
 */
function buildPlaceholderAgentData(detected: DetectedAgent): AgentElementData {
  const agentName =
    (detected.businessObject as {name?: string}).name ?? detected.elementId;
  const tools = detected.tools;
  const hasTools = tools.length > 0;

  const baseTime = Date.parse('2026-04-22T09:00:00.000Z');
  const iso = (offsetMs: number) => new Date(baseTime + offsetMs).toISOString();

  // Split detected tools across up to three iterations: [1..2], [3..4], [rest].
  // The last iteration's tool call(s) remain in-flight (status: ACTIVE) so the
  // canvas tag and the Status accordion render the "calling tools" state.
  const iterationToolGroups: Array<typeof tools> = [];
  if (tools.length <= 2) {
    if (tools.length > 0) {
      iterationToolGroups.push(tools);
    }
  } else if (tools.length <= 4) {
    iterationToolGroups.push(tools.slice(0, 1));
    iterationToolGroups.push(tools.slice(1));
  } else {
    iterationToolGroups.push(tools.slice(0, 1));
    iterationToolGroups.push(tools.slice(1, 3));
    iterationToolGroups.push(tools.slice(3));
  }

  const iterationReasonings = [
    `The user asked for something I don't have direct knowledge of, so I need to gather information with the available tools first.`,
    `I have partial information. I'll run the next round of tools in parallel to fill in the remaining gaps before composing an answer.`,
    `I have most of what I need. Calling one more tool to confirm the details before I respond.`,
  ];

  const iterations: AgentIteration[] = iterationToolGroups.map(
    (group, index) => {
      const isLast = index === iterationToolGroups.length - 1;
      const startOffsetMs = index * 2500;
      const endOffsetMs = startOffsetMs + 2300;
      const iterationNumber = index + 1;
      const inputTokens = 480 + index * 150 + group.length * 40;
      const outputTokens = 85 + index * 20;
      const reasoningTokens = 45 + index * 10;

      const toolCalls: AgentToolCall[] = group.map((tool, toolIndex) => {
        const isInFlight = isLast;
        return {
          toolName: tool.displayName,
          toolElementId: tool.elementId,
          toolDescription: `Invokes the "${tool.displayName}" activity. Real tool descriptions will come from the agent decision trail once the backend is connected.`,
          rationale: `Calling ${tool.displayName} — it's the most direct way to gather the context the user asked about.`,
          input: {
            query: `placeholder input for ${tool.displayName}`,
            requestedBy: 'agent',
          },
          output: isInFlight
            ? undefined
            : {
                status: 'ok',
                summary: `Placeholder response for ${tool.displayName}. Real tool output will appear here once available.`,
                generatedAt: iso(endOffsetMs - 500 + toolIndex * 50),
              },
          status: isInFlight ? 'ACTIVE' : 'COMPLETED',
          duration: isInFlight
            ? undefined
            : `${(1 + toolIndex * 0.2).toFixed(1)}s`,
        };
      });

      const agentMessageForAssistant = buildAssistantMessage(group);

      return {
        iterationNumber,
        startTimestamp: iso(startOffsetMs),
        endTimestamp: isLast ? undefined : iso(endOffsetMs),
        finishReason: 'TOOL_EXECUTION',
        messageId: `msg_placeholder_${iterationNumber}`,
        userMessage:
          index === 0
            ? 'Can you find me a good recipe for a weeknight dinner and include some wine pairings from the documents I attached?'
            : undefined,
        reasoning: iterationReasonings[index] ?? iterationReasonings.at(-1)!,
        agentMessage: agentMessageForAssistant,
        toolCalls,
        tokenUsage: {
          input: inputTokens,
          output: outputTokens,
          reasoning: reasoningTokens,
        },
      };
    },
  );

  // If the agent has no tools in the BPMN, still show one in-flight thinking iteration.
  if (!hasTools) {
    iterations.push({
      iterationNumber: 1,
      startTimestamp: iso(0),
      finishReason: 'TOOL_EXECUTION',
      messageId: 'msg_placeholder_1',
      userMessage:
        'Can you find me a good recipe for a weeknight dinner and include some wine pairings from the documents I attached?',
      reasoning:
        'No tools are configured for this agent — the model answers directly from its training data and the configured system prompt.',
      agentMessage: 'Composing a direct response to the request.',
      toolCalls: [],
      tokenUsage: {input: 380, output: 140, reasoning: 60},
    });
  }

  const totalInput = iterations.reduce(
    (sum, it) => sum + it.tokenUsage.input,
    0,
  );
  const totalOutput = iterations.reduce(
    (sum, it) => sum + it.tokenUsage.output,
    0,
  );
  const totalReasoning = iterations.reduce(
    (sum, it) => sum + (it.tokenUsage.reasoning ?? 0),
    0,
  );
  const toolsCalled = iterations.reduce(
    (sum, it) => sum + it.toolCalls.length,
    0,
  );

  const activeToolNames =
    iterations
      .at(-1)
      ?.toolCalls.filter((tc) => tc.status === 'ACTIVE')
      .map((tc) => tc.toolName) ?? [];

  const systemPromptLines = [
    `You are **${agentName}**, an AI agent that helps operators answer questions about the ${agentName} workflow.`,
    '',
    '## Available tools',
    '',
    ...(hasTools
      ? tools.map(
          (t) => `- **${t.displayName}** — discovered from the BPMN model.`,
        )
      : ['- No tools are configured. The agent answers directly.']),
    '',
    '## Guidelines',
    '',
    '1. Always verify information with a tool before acting on it.',
    '2. Be concise in your reasoning — operators should be able to scan the decision trail.',
    '3. When in doubt, delegate to the human operator via the appropriate approval tool.',
    '',
    '_Note: This system prompt is placeholder demo data. The configured prompt will appear here once the backend is connected._',
  ];

  const conversation: ConversationMessage[] = [
    {
      role: 'system',
      content: [systemPromptLines.join('\n')],
    },
    {
      role: 'user',
      content: [
        'Can you find me a good recipe for a weeknight dinner and include some wine pairings from the documents I attached?',
      ],
      timestamp: iso(-100),
      documents: [
        {name: 'weeknight-cookbook.pdf'},
        {name: 'wine-pairings.pdf'},
      ],
    },
  ];

  iterations.forEach((iteration) => {
    conversation.push({
      role: 'assistant',
      content: [iteration.agentMessage ?? "I'll take the next step now."],
      timestamp: iteration.startTimestamp,
      toolCalls: iteration.toolCalls.map((tc) => ({
        id: `toolu_placeholder_${tc.toolElementId}`,
        name: tc.toolElementId,
        arguments: tc.input as Record<string, unknown>,
      })),
    });

    if (iteration.toolCalls.length > 0 && iteration.endTimestamp) {
      conversation.push({
        role: 'tool_call_result',
        content: [],
        timestamp: iteration.endTimestamp,
        toolResults: iteration.toolCalls.map((tc) => ({
          id: `toolu_placeholder_${tc.toolElementId}`,
          name: tc.toolElementId,
          content: JSON.stringify(tc.output ?? {status: 'pending'}),
        })),
      });
    }
  });

  return {
    status: 'WAITING_FOR_TOOL',
    statusDetail:
      activeToolNames.length > 0 ? activeToolNames.join(', ') : agentName,
    modelProvider: 'AWS Bedrock',
    modelId: 'us.anthropic.claude-sonnet-4-20250514-v1:0',
    systemPrompt: systemPromptLines.join('\n'),
    userPrompt:
      'Can you find me a good recipe for a weeknight dinner and include some wine pairings from the documents I attached?',
    summary: hasTools
      ? `Running model call ${iterations.length} — waiting for ${toolsCalled} tool${toolsCalled === 1 ? '' : 's'} to finish.`
      : 'Thinking about the request.',
    iterations,
    usage: {
      modelCalls: {current: iterations.length, limit: 10},
      tokensUsed: {
        inputTokens: totalInput,
        outputTokens: totalOutput,
        reasoningTokens: totalReasoning,
        totalTokens: totalInput + totalOutput + totalReasoning,
      },
      toolsCalled: {current: toolsCalled, limit: 10},
    },
    toolDefinitions: tools.map((t) => ({
      name: t.displayName,
      description: `Activity "${t.displayName}" from the ${agentName} BPMN model.`,
      parameters: {
        type: 'object',
        properties: {
          query: {type: 'string'},
          requestedBy: {type: 'string'},
        },
      },
    })),
    conversation,
  };
}

/**
 * Compose a short assistant message that references the tools being called by
 * name inline, so the conversation reads naturally instead of as boilerplate.
 * The actual tool chips are rendered separately by the UI.
 */
function buildAssistantMessage(group: Array<{displayName: string}>): string {
  if (group.length === 0) {
    return "I'll think about this and respond directly.";
  }
  if (group.length === 1) {
    return 'Let me look that up before I answer — calling the tool below to fetch the data.';
  }
  return "I'll run these in parallel so we have the full picture, then compose an answer.";
}

export {
  detectAgentElement,
  buildPlaceholderAgentData,
  isAgentBusinessObject,
  type DetectedAgent,
};
