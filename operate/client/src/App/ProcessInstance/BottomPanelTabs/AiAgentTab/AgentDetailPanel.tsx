/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, useState} from 'react';
import {Accordion, AccordionItem, IconButton, Modal} from '@carbon/react';
import {
  Maximize,
  Time,
  MeterAlt,
  DocumentBlank,
  Chip,
  Add,
  Chat,
  DocumentAttachment,
  SortAscending,
  SortDescending,
} from '@carbon/icons-react';
import type {
  AgentElementData,
  AgentIteration,
  AgentToolCall,
  ConversationMessage,
} from 'modules/mock-server/agentDemoData';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {MetaLabel, DetailSection, AgentAccordionContainer} from './styled';

const MonacoEditor = lazy(async () => {
  const [{loadMonaco}, Editor] = await Promise.all([
    import('modules/loadMonaco'),
    import('@monaco-editor/react'),
  ]);
  loadMonaco();
  return Editor;
});

const statCardStyle: React.CSSProperties = {
  padding: 'var(--cds-spacing-05)',
  background: 'var(--cds-layer-02)',
  borderRadius: 4,
  display: 'flex',
  flexDirection: 'column',
  gap: 'var(--cds-spacing-03)',
  width: '100%',
  minHeight: '100%',
};

const statLabelStyle: React.CSSProperties = {
  fontSize: 'var(--cds-label-01-font-size)',
  lineHeight: 'var(--cds-label-01-line-height)',
  letterSpacing: 'var(--cds-label-01-letter-spacing)',
  textTransform: 'uppercase',
  color: 'var(--cds-text-secondary)',
  fontWeight: 600,
};

const statValueStyle: React.CSSProperties = {
  fontSize: 'var(--cds-productive-heading-03-font-size, 1.25rem)',
  lineHeight: 'var(--cds-productive-heading-03-line-height, 1.4)',
  fontWeight: 400,
  color: 'var(--cds-text-primary)',
};

function TokensStatCard({
  usage,
}: {
  usage: {
    inputTokens: number;
    outputTokens: number;
    reasoningTokens?: number;
    totalTokens: number;
  };
}) {
  const {inputTokens, outputTokens, reasoningTokens = 0, totalTokens} = usage;
  const total = totalTokens || inputTokens + outputTokens + reasoningTokens;

  const swatches: Array<{color: string; label: string; value: number}> = [
    {color: 'var(--cds-interactive)', label: 'Input', value: inputTokens},
    {color: 'var(--cds-support-warning)', label: 'Output', value: outputTokens},
  ];
  if (reasoningTokens > 0) {
    swatches.push({
      color: 'var(--cds-support-info)',
      label: 'Reasoning',
      value: reasoningTokens,
    });
  }

  return (
    <div style={statCardStyle}>
      <div style={statLabelStyle}>Tokens used</div>
      <div style={statValueStyle}>{total.toLocaleString()}</div>
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 'var(--cds-spacing-02)',
        }}
      >
        {swatches.map((swatch) => (
          <div
            key={swatch.label}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 'var(--cds-spacing-03)',
              fontSize: 'var(--cds-helper-text-01-font-size, 0.75rem)',
              lineHeight: 'var(--cds-helper-text-01-line-height, 1.33333)',
              letterSpacing:
                'var(--cds-helper-text-01-letter-spacing, 0.32px)',
              color: 'var(--cds-text-secondary)',
            }}
          >
            <span
              style={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                background: swatch.color,
                flexShrink: 0,
              }}
            />
            <span>{swatch.label}</span>
            <span
              style={{
                color: 'var(--cds-text-primary)',
                fontVariantNumeric: 'tabular-nums',
              }}
            >
              {swatch.value.toLocaleString()}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function ToolsCalledStatCard({current}: {current: number}) {
  return (
    <div style={statCardStyle}>
      <div style={statLabelStyle}>Tools called</div>
      <div style={statValueStyle}>{current.toLocaleString()}</div>
      <div
        style={{
          fontSize: 'var(--cds-helper-text-01-font-size, 0.75rem)',
          color: 'var(--cds-text-secondary)',
        }}
      >
        Across all model calls in this instance.
      </div>
    </div>
  );
}

function ModelCallsStatCard({
  current,
  limit,
}: {
  current: number;
  limit: number;
}) {
  const safeLimit = limit > 0 ? limit : 1;
  const percent = Math.min(100, Math.round((current / safeLimit) * 100));

  return (
    <div style={statCardStyle}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 'var(--cds-spacing-03)',
        }}
      >
        <div style={statLabelStyle}>Model calls</div>
        <IconButton
          kind="ghost"
          size="sm"
          label="Extend limit"
          align="top"
          className="reset-usage-btn"
        >
          <Add size={16} />
        </IconButton>
      </div>
      <div style={statValueStyle}>{current.toLocaleString()}</div>
      <div
        style={{
          height: 4,
          background: 'var(--cds-border-subtle-01)',
          borderRadius: 2,
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            height: '100%',
            width: `${percent}%`,
            background: 'var(--cds-interactive)',
            transition: 'width 300ms',
          }}
        />
      </div>
      <div
        style={{
          fontSize: 'var(--cds-helper-text-01-font-size, 0.75rem)',
          color: 'var(--cds-text-secondary)',
        }}
      >
        of {limit.toLocaleString()} limit
      </div>
    </div>
  );
}

const attachmentLabelStyle: React.CSSProperties = {
  fontSize: 'var(--cds-label-01-font-size)',
  lineHeight: 'var(--cds-label-01-line-height)',
  letterSpacing: 'var(--cds-label-01-letter-spacing)',
  color: 'var(--cds-text-secondary)',
};

const tagStyle: React.CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 4,
  padding: '1px 8px',
  borderRadius: 24,
  fontSize: 'var(--cds-label-01-font-size)',
  lineHeight: 'var(--cds-label-01-line-height)',
  letterSpacing: 'var(--cds-label-01-letter-spacing)',
  color: 'var(--cds-text-primary)',
  background: 'var(--cds-layer-02)',
  border: '1px solid var(--cds-border-subtle-01)',
  whiteSpace: 'nowrap',
};

function StatusAccordion({agentData}: {agentData: AgentElementData}) {
  const {selectElement} = useProcessInstanceElementSelection();

  const statusLabels: Record<string, string> = {
    INITIALIZING: 'Initializing',
    TOOL_DISCOVERY: 'Discovering tools...',
    THINKING: 'Thinking...',
    WAITING_FOR_TOOL: 'Calling tools',
    COMPLETED: 'Completed',
    FAILED: 'Failed',
  };

  const statusLabel =
    statusLabels[agentData.status] ?? statusLabels.INITIALIZING;

  const currentMessage = (() => {
    if (!agentData.iterations.length) {
      return null;
    }
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

  const activeTools = (() => {
    if (
      agentData.status !== 'WAITING_FOR_TOOL' ||
      !agentData.iterations.length
    ) {
      return [];
    }
    const lastIteration =
      agentData.iterations[agentData.iterations.length - 1]!;
    return lastIteration.toolCalls.filter((t) => t.status === 'ACTIVE');
  })();

  const accordionTitle = (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 'var(--cds-spacing-03)',
      }}
    >
      <Time size={16} />
      Status: {statusLabel}
    </span>
  );

  return (
    <AccordionItem title={accordionTitle} open>
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 'var(--cds-spacing-04)',
          fontSize: 'var(--cds-body-compact-01-font-size)',
          lineHeight: '1.5',
          color: 'var(--cds-text-secondary)',
          width: '100%',
        }}
      >
        {activeTools.length > 0 && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 'var(--cds-spacing-02)',
              flexWrap: 'wrap',
            }}
          >
            <span style={attachmentLabelStyle}>
              {activeTools.length} tool{activeTools.length > 1 ? 's' : ''}{' '}
              called
            </span>
            {activeTools.map((t) => (
              <button
                key={t.toolElementId}
                type="button"
                onClick={() => selectElement({elementId: t.toolElementId})}
                style={{
                  all: 'unset',
                  cursor: 'pointer',
                  ...tagStyle,
                  color: 'var(--cds-link-primary)',
                }}
              >
                {t.toolName}
              </button>
            ))}
          </div>
        )}
        {currentMessage && (
          <div>
            <strong style={{color: 'var(--cds-text-primary)'}}>Message:</strong>{' '}
            {currentMessage}
          </div>
        )}
      </div>
    </AccordionItem>
  );
}

function ToolCallDetail({
  tool,
}: {
  tool: AgentToolCall;
  iteration: AgentIteration;
}) {
  const [isInputModalOpen, setIsInputModalOpen] = useState(false);
  const [isOutputModalOpen, setIsOutputModalOpen] = useState(false);

  const inputText =
    Object.keys(tool.input).length > 0
      ? JSON.stringify(tool.input, null, 2)
      : null;
  const outputText = tool.output ? JSON.stringify(tool.output, null, 2) : null;

  return (
    <>
      <DetailSection>
        <MetaLabel>Description</MetaLabel>
        <div
          style={{
            fontSize: 'var(--cds-body-compact-01-font-size)',
            lineHeight: '1.5',
            color: 'var(--cds-text-secondary)',
          }}
        >
          {tool.toolDescription}
        </div>
      </DetailSection>

      <AgentAccordionContainer>
        <Accordion align="start">
          <AccordionItem title="Input">
            {inputText ? (
              <div
                style={{
                  width: '100%',
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: 'var(--cds-spacing-03)',
                }}
              >
                <pre
                  style={{
                    flex: 1,
                    minWidth: 0,
                    margin: 0,
                    padding: 'var(--cds-spacing-05)',
                    fontFamily:
                      "var(--cds-code-01-font-family, 'IBM Plex Mono', monospace)",
                    fontSize: 13,
                    lineHeight: '1.5',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                    maxHeight: 260,
                    overflowY: 'auto',
                    background: 'var(--cds-layer-02)',
                    borderRadius: 4,
                  }}
                >
                  {inputText}
                </pre>
                <IconButton
                  kind="ghost"
                  size="sm"
                  label="Expand input"
                  onClick={() => setIsInputModalOpen(true)}
                  style={{flexShrink: 0}}
                >
                  <Maximize size={16} />
                </IconButton>
              </div>
            ) : (
              <div
                style={{
                  fontSize: 'var(--cds-body-compact-01-font-size)',
                  color: 'var(--cds-text-secondary)',
                }}
              >
                No input parameters
              </div>
            )}
          </AccordionItem>

          <AccordionItem title="Output">
            {outputText ? (
              <div
                style={{
                  width: '100%',
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: 'var(--cds-spacing-03)',
                }}
              >
                <pre
                  style={{
                    flex: 1,
                    minWidth: 0,
                    margin: 0,
                    padding: 'var(--cds-spacing-05)',
                    fontFamily:
                      "var(--cds-code-01-font-family, 'IBM Plex Mono', monospace)",
                    fontSize: 13,
                    lineHeight: '1.5',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                    maxHeight: 260,
                    overflowY: 'auto',
                    background: 'var(--cds-layer-02)',
                    borderRadius: 4,
                  }}
                >
                  {outputText}
                </pre>
                <IconButton
                  kind="ghost"
                  size="sm"
                  label="Expand output"
                  onClick={() => setIsOutputModalOpen(true)}
                  style={{flexShrink: 0}}
                >
                  <Maximize size={16} />
                </IconButton>
              </div>
            ) : (
              <div
                style={{
                  fontSize: 'var(--cds-body-compact-01-font-size)',
                  color: 'var(--cds-text-secondary)',
                }}
              >
                {tool.status === 'ACTIVE'
                  ? 'Output will appear once the tool completes.'
                  : 'No output available.'}
              </div>
            )}
          </AccordionItem>
        </Accordion>
      </AgentAccordionContainer>

      <Modal
        open={isInputModalOpen}
        modalHeading="Input"
        onRequestClose={() => setIsInputModalOpen(false)}
        size="lg"
        passiveModal
      >
        <Suspense
          fallback={
            <div
              style={{
                height: '60vh',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              Loading editor...
            </div>
          }
        >
          <MonacoEditor
            height="60vh"
            language="json"
            value={inputText ?? ''}
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

      <Modal
        open={isOutputModalOpen}
        modalHeading="Output"
        onRequestClose={() => setIsOutputModalOpen(false)}
        size="lg"
        passiveModal
      >
        <Suspense
          fallback={
            <div
              style={{
                height: '60vh',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              Loading editor...
            </div>
          }
        >
          <MonacoEditor
            height="60vh"
            language="json"
            value={outputText ?? ''}
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

function IterationDetail({iteration}: {iteration: AgentIteration}) {
  const input = iteration.tokenUsage.input;
  const output = iteration.tokenUsage.output;
  const reasoning = iteration.tokenUsage.reasoning ?? 0;
  const total = input + output + reasoning;

  return (
    <>
      <DetailSection>
        <MetaLabel>Reasoning</MetaLabel>
        <div
          style={{
            fontSize: 'var(--cds-body-compact-01-font-size)',
            lineHeight: '1.5',
            color: 'var(--cds-text-secondary)',
          }}
        >
          {iteration.reasoning}
        </div>
      </DetailSection>

      <DetailSection
        style={{maxWidth: 'calc(50% - var(--cds-spacing-07) / 2)'}}
      >
        <TokensStatCard
          usage={{
            inputTokens: input,
            outputTokens: output,
            reasoningTokens: reasoning,
            totalTokens: total,
          }}
        />
      </DetailSection>
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
  extraItems,
}: {
  details: ElementDetailsProps;
  extraItems?: {label: string; value: React.ReactNode}[];
}) {
  const items: {label: string; value: React.ReactNode}[] = [
    {label: 'Instance key', value: details.elementInstanceKey},
    {label: 'Duration', value: details.executionDuration},
  ];
  if (details.retriesLeft !== undefined) {
    items.push({label: 'Retries left', value: String(details.retriesLeft)});
  }
  if (extraItems) {
    items.push(...extraItems);
  }

  const columnCount = items.length <= 2 ? 2 : 3;

  return (
    <DetailSection>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: `repeat(${columnCount}, 1fr)`,
          gap: 'var(--cds-spacing-05)',
        }}
      >
        {items.map((item) => (
          <div key={item.label}>
            <div
              style={{
                fontSize: 'var(--cds-label-01-font-size)',
                lineHeight: 'var(--cds-label-01-line-height)',
                letterSpacing: 'var(--cds-label-01-letter-spacing)',
                color: 'var(--cds-text-secondary)',
                marginBottom: 'var(--cds-spacing-02)',
              }}
            >
              {item.label}
            </div>
            <div
              style={{
                fontSize: 'var(--cds-body-compact-01-font-size)',
                color: 'var(--cds-text-primary)',
                wordBreak: 'break-all',
              }}
            >
              {item.value}
            </div>
          </div>
        ))}
      </div>
    </DetailSection>
  );
}

function ExpandableSegment({
  content,
  isFirst,
}: {
  content: string;
  isFirst: boolean;
}) {
  return (
    <div
      style={{
        fontSize: 'var(--cds-body-compact-01-font-size)',
        lineHeight: '1.5',
        color: 'var(--cds-text-primary)',
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-word',
        maxHeight: 160,
        overflowY: 'auto',
        marginTop: isFirst ? 0 : 'var(--cds-spacing-04)',
      }}
    >
      {content}
    </div>
  );
}

function ExpandableMessageBlock({
  role,
  roleColor,
  borderColor,
  contents,
  modalHeading,
  expandedJson,
  children,
}: {
  role: string;
  roleColor: string;
  borderColor: string;
  contents: string[];
  modalHeading: string;
  /**
   * When provided, the expand-to-modal view shows the full message payload as
   * formatted JSON (matching the shape the agent decision trail records). When
   * omitted, the modal falls back to the joined markdown content (used for
   * non-message blocks like the System prompt).
   */
  expandedJson?: unknown;
  children?: React.ReactNode;
}) {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const segments = contents.filter((c) => c.length > 0);
  const fullContent = segments.join('\n\n');
  const modalLanguage = expandedJson !== undefined ? 'json' : 'markdown';
  const modalValue =
    expandedJson !== undefined
      ? JSON.stringify(expandedJson, null, 2)
      : fullContent;

  return (
    <>
      <div
        style={{
          padding: 'var(--cds-spacing-04)',
          background: 'var(--cds-layer-02)',
          borderRadius: 4,
          borderLeft: `3px solid ${borderColor}`,
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 'var(--cds-spacing-03)',
            marginBottom: 'var(--cds-spacing-02)',
          }}
        >
          <span
            style={{
              fontWeight: 600,
              textTransform: 'uppercase' as const,
              letterSpacing: '0.32px',
              fontSize: 10,
              color: roleColor,
            }}
          >
            {role}
          </span>
          <IconButton
            kind="ghost"
            size="sm"
            label="Expand"
            align="left"
            onClick={() => setIsModalOpen(true)}
          >
            <Maximize size={16} />
          </IconButton>
        </div>
        {segments.map((segment, index) => (
          <ExpandableSegment
            key={index}
            content={segment}
            isFirst={index === 0}
          />
        ))}
        {children}
      </div>
      <Modal
        open={isModalOpen}
        modalHeading={modalHeading}
        onRequestClose={() => setIsModalOpen(false)}
        size="lg"
        passiveModal
      >
        <Suspense
          fallback={
            <div
              style={{
                height: '60vh',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              Loading editor...
            </div>
          }
        >
          <MonacoEditor
            height="60vh"
            language={modalLanguage}
            value={modalValue}
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

/**
 * Reshape a conversation message into the agent decision-trail JSON shape
 * (content is an array of `{type: "text", text: ...}` blocks, matching what
 * the backend records). Used by the expand-to-modal view so operators can see
 * the raw payload behind each message.
 */
function messageToDecisionTrailShape(msg: ConversationMessage) {
  const out: Record<string, unknown> = {
    role: msg.role,
    content: msg.content
      .filter((text) => text.length > 0)
      .map((text) => ({type: 'text', text})),
  };
  if (msg.timestamp) {
    out.timestamp = msg.timestamp;
  }
  if (msg.documents && msg.documents.length > 0) {
    out.documents = msg.documents;
  }
  if (msg.toolCalls && msg.toolCalls.length > 0) {
    out.toolCalls = msg.toolCalls;
  }
  if (msg.toolResults && msg.toolResults.length > 0) {
    out.toolResults = msg.toolResults;
  }
  return out;
}

function ConversationHistory({
  messages,
  agentData,
}: {
  messages: ConversationMessage[];
  agentData: AgentElementData;
}) {
  const {selectElement} = useProcessInstanceElementSelection();
  const [isNewestFirst, setIsNewestFirst] = useState(true);

  const toolDisplayNames = new Map<string, string>();
  for (const iter of agentData.iterations) {
    for (const tc of iter.toolCalls) {
      toolDisplayNames.set(tc.toolElementId, tc.toolName);
    }
  }

  const filtered = messages
    .filter((msg) => msg.role !== 'system' && msg.role !== 'tool_call_result')
    .slice();

  if (isNewestFirst) {
    filtered.reverse();
  }

  const SortIcon = isNewestFirst ? SortDescending : SortAscending;

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--cds-spacing-04)',
        width: '100%',
      }}
    >
      <button
        type="button"
        onClick={() => setIsNewestFirst((prev) => !prev)}
        style={{
          all: 'unset',
          cursor: 'pointer',
          display: 'inline-flex',
          alignItems: 'center',
          alignSelf: 'flex-start',
          gap: 'var(--cds-spacing-02)',
          fontSize: 'var(--cds-helper-text-01-font-size, 0.75rem)',
          lineHeight: 'var(--cds-helper-text-01-line-height, 1.33333)',
          color: 'var(--cds-text-secondary)',
        }}
      >
        {isNewestFirst ? 'Most recent first' : 'Oldest first'}
        <SortIcon size={12} />
      </button>
      {filtered.map((msg, i) => {
        if (msg.role === 'user') {
          return (
            <ExpandableMessageBlock
              key={i}
              role="User"
              roleColor="var(--cds-interactive)"
              borderColor="var(--cds-interactive)"
              contents={msg.content}
              modalHeading="User message"
              expandedJson={messageToDecisionTrailShape(msg)}
            >
              {msg.documents && msg.documents.length > 0 && (
                <div
                  style={{
                    marginTop: 'var(--cds-spacing-05)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 'var(--cds-spacing-02)',
                    flexWrap: 'wrap',
                  }}
                >
                  <span style={attachmentLabelStyle}>Documents</span>
                  {msg.documents.map((doc) => (
                    <span key={doc.name} style={tagStyle}>
                      <DocumentAttachment size={12} />
                      {doc.name}
                    </span>
                  ))}
                </div>
              )}
            </ExpandableMessageBlock>
          );
        }

        if (msg.role === 'assistant') {
          return (
            <ExpandableMessageBlock
              key={i}
              role="Assistant"
              roleColor="#8a3ffc"
              borderColor="#8a3ffc"
              contents={msg.content}
              modalHeading="Assistant message"
              expandedJson={messageToDecisionTrailShape(msg)}
            >
              {msg.toolCalls && msg.toolCalls.length > 0 && (
                <div
                  style={{
                    marginTop: 'var(--cds-spacing-05)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 'var(--cds-spacing-02)',
                    flexWrap: 'wrap',
                  }}
                >
                  <span style={attachmentLabelStyle}>Tool calls</span>
                  {msg.toolCalls.map((tc) => {
                    const displayName =
                      toolDisplayNames.get(tc.name) ?? tc.name;
                    return (
                      <button
                        key={tc.id}
                        type="button"
                        onClick={() => selectElement({elementId: tc.name})}
                        style={{
                          all: 'unset',
                          cursor: 'pointer',
                          ...tagStyle,
                          color: 'var(--cds-link-primary)',
                        }}
                      >
                        {displayName}
                      </button>
                    );
                  })}
                </div>
              )}
            </ExpandableMessageBlock>
          );
        }

        return null;
      })}
    </div>
  );
}

function DefaultAgentDetail({agentData}: {agentData: AgentElementData}) {
  return (
    <AgentAccordionContainer>
      <Accordion align="start">
        <StatusAccordion agentData={agentData} />
        <AccordionItem
          title={
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 'var(--cds-spacing-03)',
              }}
            >
              <MeterAlt size={16} />
              Usage
            </span>
          }
        >
          <div
            style={{
              display: 'flex',
              gap: 'var(--cds-spacing-05)',
              alignItems: 'stretch',
              width: '100%',
            }}
          >
            <div style={{flex: 1, display: 'flex'}}>
              <ModelCallsStatCard
                current={agentData.usage.modelCalls.current}
                limit={agentData.usage.modelCalls.limit}
              />
            </div>
            <div style={{flex: 1, display: 'flex'}}>
              <TokensStatCard usage={agentData.usage.tokensUsed} />
            </div>
            <div style={{flex: 1, display: 'flex'}}>
              <ToolsCalledStatCard
                current={agentData.usage.toolsCalled.current}
              />
            </div>
          </div>
        </AccordionItem>

        {agentData.conversation && agentData.conversation.length > 0 && (
          <AccordionItem
            title={
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 'var(--cds-spacing-03)',
                }}
              >
                <Chat size={16} />
                Conversation history
              </span>
            }
          >
            <div style={{width: '100%'}}>
              <ConversationHistory
                messages={agentData.conversation}
                agentData={agentData}
              />
            </div>
          </AccordionItem>
        )}

        <AccordionItem
          title={
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 'var(--cds-spacing-03)',
              }}
            >
              <DocumentBlank size={16} />
              System prompt
            </span>
          }
        >
          <div style={{width: '100%'}}>
            <ExpandableMessageBlock
              role="System"
              roleColor="var(--cds-text-secondary)"
              borderColor="var(--cds-border-subtle-01)"
              contents={[agentData.systemPrompt]}
              modalHeading="System prompt"
            />
          </div>
        </AccordionItem>

        <AccordionItem
          title={
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 'var(--cds-spacing-03)',
              }}
            >
              <Chip size={16} />
              Model
            </span>
          }
        >
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 'var(--cds-spacing-02)',
              fontSize: 'var(--cds-body-compact-01-font-size)',
              lineHeight: '1.5',
              color: 'var(--cds-text-secondary)',
              width: '100%',
            }}
          >
            <div>
              <strong style={{color: 'var(--cds-text-primary)'}}>
                Provider:
              </strong>{' '}
              {agentData.modelProvider}
            </div>
            <div>
              <strong style={{color: 'var(--cds-text-primary)'}}>Model:</strong>{' '}
              {agentData.modelId}
            </div>
          </div>
        </AccordionItem>
      </Accordion>
    </AgentAccordionContainer>
  );
}

export {
  DefaultAgentDetail,
  IterationDetail,
  ToolCallDetail,
  ElementDetailsSection,
};
export type {ElementDetailsProps};
