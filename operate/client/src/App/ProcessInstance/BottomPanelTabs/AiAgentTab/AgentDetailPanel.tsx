/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, useState} from 'react';
import {
  Accordion,
  AccordionItem,
  IconButton,
  Modal,
  Tag,
  Tooltip,
} from '@carbon/react';
import {
  WatsonHealthAiResults,
  Tools,
  Time,
  Checkmark,
  WarningAlt,
  UserAvatar,
  Bot,
  Settings,
  MeterAlt,
  DocumentBlank,
  Maximize,
} from '@carbon/icons-react';
import type {
  AgentElementData,
  AgentIteration,
  AgentToolCall,
} from 'modules/mock-server/agentDemoData';
import {
  MetaLabel,
  CodeBlock,
  DetailSection,
  PropertyRow,
  PropertyLabel,
  PropertyValue,
  UsageBar,
  UsageBarTrack,
  UsageBarFill,
} from './styled';

const MonacoEditor = lazy(async () => {
  const [{loadMonaco}, Editor] = await Promise.all([
    import('modules/loadMonaco'),
    import('@monaco-editor/react'),
  ]);
  loadMonaco();
  return Editor;
});

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  });
}

function AgentUsageBarComponent({
  label,
  current,
  limit,
}: {
  label: string;
  current: number;
  limit: number;
}) {
  const pct = Math.min(100, (current / limit) * 100);
  return (
    <UsageBar>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          fontSize: 'var(--cds-label-01-font-size)',
        }}
      >
        <span style={{color: 'var(--cds-text-secondary)'}}>{label}</span>
        <span
          style={{
            fontFamily:
              "var(--cds-code-01-font-family, 'IBM Plex Mono', monospace)",
          }}
        >
          {current.toLocaleString()} / {limit.toLocaleString()}
        </span>
      </div>
      <UsageBarTrack>
        <UsageBarFill $percent={pct} />
      </UsageBarTrack>
    </UsageBar>
  );
}

function StatusBanner({
  agentData,
  showModel = false,
}: {
  agentData: AgentElementData;
  showModel?: boolean;
}) {
  const statusConfig: Record<
    string,
    {label: string; icon: React.ReactNode; tagType: string}
  > = {
    INITIALIZING: {
      label: 'Initializing',
      icon: <Settings size={16} />,
      tagType: 'gray',
    },
    TOOL_DISCOVERY: {
      label: 'Discovering tools...',
      icon: <Tools size={16} />,
      tagType: 'blue',
    },
    THINKING: {
      label: 'Thinking...',
      icon: <WatsonHealthAiResults size={16} />,
      tagType: 'blue',
    },
    WAITING_FOR_TOOL: {
      label: 'Waiting for tool results',
      icon: <Time size={16} />,
      tagType: 'blue',
    },
    COMPLETED: {
      label: 'Completed',
      icon: <Checkmark size={16} />,
      tagType: 'green',
    },
    FAILED: {
      label: 'Failed',
      icon: <WarningAlt size={16} />,
      tagType: 'red',
    },
  };

  const cfg = statusConfig[agentData.status] ?? statusConfig.INITIALIZING;

  const currentReasoning = (() => {
    if (!agentData.iterations.length) return null;
    const lastIteration =
      agentData.iterations[agentData.iterations.length - 1]!;
    if (
      agentData.status === 'WAITING_FOR_TOOL' &&
      lastIteration.toolCalls.length > 0
    ) {
      const activeTool = lastIteration.toolCalls.find(
        (t) => t.status === 'ACTIVE',
      );
      return (
        activeTool?.rationale ??
        lastIteration.toolCalls[lastIteration.toolCalls.length - 1]!.rationale
      );
    }
    return lastIteration.reasoning;
  })();

  return (
    <DetailSection>
      <MetaLabel>Status</MetaLabel>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--cds-spacing-03)',
          padding: 'var(--cds-spacing-04)',
          background: 'var(--cds-layer-02)',
          borderRadius: 4,
          border: '1px solid var(--cds-border-subtle)',
        }}
      >
        {cfg.icon}
        <div style={{flex: 1, minWidth: 0}}>
          <div
            style={{
              fontSize: 'var(--cds-body-compact-01-font-size)',
              fontWeight: 600,
            }}
          >
            {cfg.label}
          </div>
          {agentData.status === 'WAITING_FOR_TOOL' &&
            agentData.statusDetail && (
              <div
                style={{
                  fontSize: 'var(--cds-label-01-font-size)',
                  color: 'var(--cds-text-secondary)',
                  marginTop: 2,
                }}
              >
                Calling {agentData.statusDetail}
              </div>
            )}
        </div>
      </div>
      {currentReasoning && (
        <div style={{marginTop: 'var(--cds-spacing-03)'}}>
          <MetaLabel>Reasoning</MetaLabel>
          <div
            style={{
              fontSize: 'var(--cds-body-compact-01-font-size)',
              color: 'var(--cds-text-secondary)',
              fontStyle: 'italic',
              lineHeight: '1.5',
            }}
          >
            {currentReasoning}
          </div>
        </div>
      )}
      {showModel && (
        <div style={{marginTop: 'var(--cds-spacing-03)'}}>
          <MetaLabel>Model</MetaLabel>
          <code
            style={{
              fontSize: 12,
              fontFamily:
                "var(--cds-code-01-font-family, 'IBM Plex Mono', monospace)",
              background: 'var(--cds-layer-02)',
              padding: '2px 6px',
              borderRadius: 3,
            }}
          >
            {agentData.modelId}
          </code>
        </div>
      )}
    </DetailSection>
  );
}

function ToolCallDetail({
  tool,
}: {
  tool: AgentToolCall;
  iteration: AgentIteration;
}) {
  return (
    <>
      <DetailSection>
        <MetaLabel>Description</MetaLabel>
        <div
          style={{
            fontSize: 'var(--cds-body-compact-01-font-size)',
            lineHeight: '1.5',
          }}
        >
          {tool.toolDescription}
        </div>
      </DetailSection>

      <DetailSection>
        <MetaLabel>Input</MetaLabel>
        {Object.keys(tool.input).length > 0 ? (
          <CodeBlock>{JSON.stringify(tool.input, null, 2)}</CodeBlock>
        ) : (
          <div
            style={{
              fontSize: 'var(--cds-body-compact-01-font-size)',
              color: 'var(--cds-text-secondary)',
              fontStyle: 'italic',
            }}
          >
            No input parameters
          </div>
        )}
      </DetailSection>

      <DetailSection>
        <MetaLabel>Output</MetaLabel>
        {tool.output ? (
          <CodeBlock>
            {typeof tool.output === 'string'
              ? tool.output
              : JSON.stringify(tool.output, null, 2)}
          </CodeBlock>
        ) : (
          <div
            style={{
              fontSize: 'var(--cds-body-compact-01-font-size)',
              color: 'var(--cds-text-secondary)',
              fontStyle: 'italic',
            }}
          >
            {tool.status === 'ACTIVE' ? 'Waiting for result...' : 'No output'}
          </div>
        )}
      </DetailSection>
    </>
  );
}

function IterationDetail({iteration}: {iteration: AgentIteration}) {
  return (
    <>
      <DetailSection>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 'var(--cds-spacing-03)',
            marginBottom: 'var(--cds-spacing-04)',
          }}
        >
          <WatsonHealthAiResults size={16} />
          <span
            style={{
              fontSize: 'var(--cds-body-compact-01-font-size)',
              fontWeight: 600,
              flex: 1,
            }}
          >
            Agent iteration {iteration.iterationNumber}
          </span>
          {iteration.finishReason && (
            <Tag
              type={
                iteration.finishReason === 'TOOL_EXECUTION'
                  ? 'blue'
                  : iteration.finishReason === 'STOP'
                    ? 'green'
                    : iteration.finishReason === 'ERROR'
                      ? 'red'
                      : 'gray'
              }
              size="sm"
            >
              {iteration.finishReason === 'TOOL_EXECUTION'
                ? 'Tool call'
                : iteration.finishReason === 'STOP'
                  ? 'Done'
                  : iteration.finishReason === 'MAX_TOKENS'
                    ? 'Max tokens'
                    : 'Error'}
            </Tag>
          )}
          <span
            style={{
              fontSize: 10,
              color: 'var(--cds-text-secondary)',
              fontFamily:
                "var(--cds-code-01-font-family, 'IBM Plex Mono', monospace)",
            }}
          >
            {formatTime(iteration.startTimestamp)}
          </span>
        </div>
      </DetailSection>

      {iteration.userMessage && (
        <DetailSection>
          <MetaLabel>
            <UserAvatar
              size={12}
              style={{marginRight: 4, verticalAlign: -2}}
            />
            User Message
          </MetaLabel>
          <div
            style={{
              fontSize: 'var(--cds-body-compact-01-font-size)',
              lineHeight: '1.5',
              background: 'var(--cds-layer-02)',
              borderRadius: 4,
              padding: 'var(--cds-spacing-03) var(--cds-spacing-04)',
            }}
          >
            {iteration.userMessage}
          </div>
        </DetailSection>
      )}

      <DetailSection>
        <MetaLabel>
          <WatsonHealthAiResults
            size={12}
            style={{marginRight: 4, verticalAlign: -2}}
          />
          Reasoning
        </MetaLabel>
        <div
          style={{
            fontSize: 'var(--cds-body-compact-01-font-size)',
            lineHeight: '1.5',
            background: 'var(--cds-layer-02)',
            borderRadius: 4,
            padding: 'var(--cds-spacing-03) var(--cds-spacing-04)',
            fontStyle: 'italic',
            color: 'var(--cds-text-secondary)',
          }}
        >
          {iteration.reasoning}
        </div>
      </DetailSection>

      {iteration.agentMessage && (
        <DetailSection>
          <MetaLabel>
            <Bot size={12} style={{marginRight: 4, verticalAlign: -2}} />
            Assistant Response
          </MetaLabel>
          <div
            style={{
              fontSize: 'var(--cds-body-compact-01-font-size)',
              lineHeight: '1.5',
            }}
          >
            {iteration.agentMessage}
          </div>
        </DetailSection>
      )}

      {iteration.toolCalls.length > 0 && (
        <DetailSection>
          <MetaLabel>
            <Tools size={12} style={{marginRight: 4, verticalAlign: -2}} />
            Tool{iteration.toolCalls.length > 1 ? 's' : ''} Called
          </MetaLabel>
          <div style={{display: 'flex', flexDirection: 'column', gap: 8}}>
            {iteration.toolCalls.map((tool, idx) => (
              <div
                key={idx}
                style={{
                  fontSize: 'var(--cds-body-compact-01-font-size)',
                  background:
                    tool.status === 'FAILED'
                      ? 'var(--cds-notification-background-error)'
                      : 'var(--cds-layer-02)',
                  borderRadius: 4,
                  padding: 'var(--cds-spacing-03) var(--cds-spacing-04)',
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 'var(--cds-spacing-03)',
                  }}
                >
                  <span
                    style={{
                      fontWeight: 600,
                      fontFamily:
                        "var(--cds-code-01-font-family, 'IBM Plex Mono', monospace)",
                    }}
                  >
                    {tool.toolName}
                  </span>
                  {tool.duration && (
                    <span
                      style={{
                        fontSize: 10,
                        color: 'var(--cds-text-secondary)',
                        marginLeft: 'auto',
                      }}
                    >
                      {tool.duration}
                    </span>
                  )}
                  {tool.status === 'FAILED' && (
                    <Tag type="red" size="sm">
                      Failed
                    </Tag>
                  )}
                </div>
                <div
                  style={{
                    color: 'var(--cds-text-secondary)',
                    marginTop: 2,
                    lineHeight: '1.4',
                  }}
                >
                  {tool.rationale}
                </div>
              </div>
            ))}
          </div>
        </DetailSection>
      )}

      {/* Token usage */}
      <DetailSection>
        <MetaLabel>Token Usage</MetaLabel>
        <div style={{display: 'flex', gap: 'var(--cds-spacing-04)'}}>
          <div
            style={{
              flex: 1,
              textAlign: 'center',
              background: 'var(--cds-layer-02)',
              borderRadius: 4,
              padding: 'var(--cds-spacing-03)',
            }}
          >
            <div
              style={{
                fontFamily:
                  "var(--cds-code-01-font-family, 'IBM Plex Mono', monospace)",
                fontWeight: 600,
              }}
            >
              {(
                iteration.tokenUsage.input + iteration.tokenUsage.output
              ).toLocaleString()}
            </div>
            <div
              style={{
                fontSize: 10,
                color: 'var(--cds-text-secondary)',
              }}
            >
              Total
            </div>
          </div>
          <div
            style={{
              flex: 1,
              textAlign: 'center',
              background: 'var(--cds-layer-02)',
              borderRadius: 4,
              padding: 'var(--cds-spacing-03)',
            }}
          >
            <div
              style={{
                fontFamily:
                  "var(--cds-code-01-font-family, 'IBM Plex Mono', monospace)",
                fontWeight: 600,
              }}
            >
              {iteration.tokenUsage.input.toLocaleString()}
            </div>
            <div
              style={{
                fontSize: 10,
                color: 'var(--cds-text-secondary)',
              }}
            >
              Input
            </div>
          </div>
          <div
            style={{
              flex: 1,
              textAlign: 'center',
              background: 'var(--cds-layer-02)',
              borderRadius: 4,
              padding: 'var(--cds-spacing-03)',
            }}
          >
            <div
              style={{
                fontFamily:
                  "var(--cds-code-01-font-family, 'IBM Plex Mono', monospace)",
                fontWeight: 600,
              }}
            >
              {iteration.tokenUsage.output.toLocaleString()}
            </div>
            <div
              style={{
                fontSize: 10,
                color: 'var(--cds-text-secondary)',
              }}
            >
              Output
            </div>
          </div>
        </div>
      </DetailSection>

      {iteration.messageId && (
        <DetailSection>
          <MetaLabel>Message ID</MetaLabel>
          <div
            style={{
              fontSize: 11,
              fontFamily:
                "var(--cds-code-01-font-family, 'IBM Plex Mono', monospace)",
              color: 'var(--cds-text-secondary)',
              background: 'var(--cds-layer-02)',
              borderRadius: 4,
              padding: 'var(--cds-spacing-02) var(--cds-spacing-03)',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {iteration.messageId}
          </div>
        </DetailSection>
      )}
    </>
  );
}

type ElementDetailsProps = {
  elementInstanceKey: string;
  executionDuration: string;
  retriesLeft?: number;
};

function ElementDetailsSection({
  details,
}: {
  details: ElementDetailsProps;
}) {
  return (
    <DetailSection>
      <PropertyRow>
        <PropertyLabel>Element Instance Key</PropertyLabel>
        <PropertyValue
          style={{
            fontFamily:
              "var(--cds-code-01-font-family, 'IBM Plex Mono', monospace)",
            fontSize: 12,
          }}
        >
          {details.elementInstanceKey}
        </PropertyValue>
      </PropertyRow>
      <PropertyRow>
        <PropertyLabel>Execution Duration</PropertyLabel>
        <PropertyValue>{details.executionDuration}</PropertyValue>
      </PropertyRow>
      {details.retriesLeft !== undefined && (
        <PropertyRow>
          <PropertyLabel>Retries Left</PropertyLabel>
          <PropertyValue>{details.retriesLeft}</PropertyValue>
        </PropertyRow>
      )}
    </DetailSection>
  );
}

function DefaultAgentDetail({
  agentData,
  elementDetails,
}: {
  agentData: AgentElementData;
  elementDetails?: ElementDetailsProps;
}) {
  const [isPromptModalOpen, setIsPromptModalOpen] = useState(false);

  return (
    <>
      {elementDetails && <ElementDetailsSection details={elementDetails} />}

      <StatusBanner agentData={agentData} showModel />

      <Accordion align="start">
        <AccordionItem
          title={
            <span style={{display: 'flex', alignItems: 'center', gap: 'var(--cds-spacing-03)'}}>
              <MeterAlt size={16} />
              Usage &amp; Limits
            </span>
          }
          open
        >
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 'var(--cds-spacing-04)',
              width: '100%',
            }}
          >
            <div style={{display: 'flex', gap: 'var(--cds-spacing-05)'}}>
              <div style={{flex: 1}}>
                <AgentUsageBarComponent
                  label="Model calls"
                  current={agentData.usage.modelCalls.current}
                  limit={agentData.usage.modelCalls.limit}
                />
              </div>
              <div style={{flex: 1}}>
                <AgentUsageBarComponent
                  label="Tools called"
                  current={agentData.usage.toolsCalled.current}
                  limit={agentData.usage.toolsCalled.limit}
                />
              </div>
            </div>
            <div style={{display: 'flex', gap: 'var(--cds-spacing-05)'}}>
              <div style={{flex: 1}}>
                <AgentUsageBarComponent
                  label="Tokens used"
                  current={agentData.usage.tokensUsed.current}
                  limit={agentData.usage.tokensUsed.limit}
                />
                <div
                  style={{
                    fontSize: 10,
                    color: 'var(--cds-text-secondary)',
                    fontFamily:
                      "var(--cds-code-01-font-family, 'IBM Plex Mono', monospace)",
                    marginTop: 2,
                  }}
                >
                  {agentData.usage.tokensUsed.inputTokens.toLocaleString()}{' '}
                  input &middot;{' '}
                  {agentData.usage.tokensUsed.outputTokens.toLocaleString()}{' '}
                  output
                </div>
              </div>
              <div style={{flex: 1}} />
            </div>
          </div>
        </AccordionItem>

        <AccordionItem
          title={
            <span style={{display: 'flex', alignItems: 'center', gap: 'var(--cds-spacing-03)'}}>
              <DocumentBlank size={16} />
              System Prompt
            </span>
          }
        >
          <div style={{width: '100%', position: 'relative'}}>
            <div
              style={{
                position: 'relative',
                background: 'var(--cds-layer-02)',
                border: '1px solid var(--cds-border-subtle)',
                borderRadius: 4,
              }}
            >
              <Tooltip label="Open system prompt" align="left">
                <IconButton
                  kind="ghost"
                  size="sm"
                  label="Open system prompt"
                  onClick={() => setIsPromptModalOpen(true)}
                  style={{
                    position: 'absolute',
                    top: 4,
                    right: 4,
                    zIndex: 1,
                  }}
                >
                  <Maximize size={16} />
                </IconButton>
              </Tooltip>
              <pre
                style={{
                  margin: 0,
                  padding: 'var(--cds-spacing-05)',
                  paddingRight: 'calc(var(--cds-spacing-05) + 32px)',
                  fontFamily:
                    "var(--cds-code-01-font-family, 'IBM Plex Mono', monospace)",
                  fontSize: 13,
                  lineHeight: '1.5',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  maxHeight: 260,
                  overflowY: 'auto',
                }}
              >
                {agentData.systemPrompt}
              </pre>
            </div>
          </div>
        </AccordionItem>
      </Accordion>

      <Modal
        open={isPromptModalOpen}
        modalHeading="System Prompt"
        onRequestClose={() => setIsPromptModalOpen(false)}
        size="lg"
        passiveModal
      >
        <Suspense
          fallback={
            <div style={{height: '60vh', display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
              Loading editor...
            </div>
          }
        >
          <MonacoEditor
            height="60vh"
            language="markdown"
            value={agentData.systemPrompt}
            options={{
              readOnly: true,
              minimap: {enabled: false},
              fontSize: 13,
              lineHeight: 20,
              tabSize: 2,
              wordWrap: 'on',
              scrollBeyondLastLine: false,
            }}
          />
        </Suspense>
      </Modal>
    </>
  );
}

export {
  DefaultAgentDetail,
  IterationDetail,
  ToolCallDetail,
  ElementDetailsSection,
};
export type {ElementDetailsProps};
