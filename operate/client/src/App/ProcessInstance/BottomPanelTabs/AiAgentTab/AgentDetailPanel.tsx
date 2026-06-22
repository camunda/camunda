/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, useLayoutEffect, useRef, useState} from 'react';
import styled from 'styled-components';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {
  Accordion,
  AccordionItem,
  Button,
  CodeSnippet,
  IconButton,
  Modal,
} from '@carbon/react';
import {
  Maximize,
  Time,
  CheckmarkOutline,
  MeterAlt,
  DocumentBlank,
  Chip,
  Add,
  Chat,
  DocumentAttachment,
  SortAscending,
  SortDescending,
} from '@carbon/icons-react';
import {Copy} from '@carbon/react/icons';
import type {
  AgentElementData,
  AgentIteration,
  AgentToolCall,
  ConversationMessage,
} from 'modules/contexts/agentData.types';
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

const SegmentRow = styled.div<{$alwaysShowActions?: boolean}>`
  display: flex;
  align-items: flex-start;
  gap: var(--cds-spacing-03);

  & > .segment-actions {
    opacity: ${({$alwaysShowActions}) => ($alwaysShowActions ? 1 : 0)};
    transition: opacity 120ms ease-out;
    flex-shrink: 0;
    align-self: flex-start;
    display: flex;
    flex-direction: row;
  }

  &:hover > .segment-actions,
  &:focus-within > .segment-actions {
    opacity: 1;
  }
`;

const SegmentBody = styled.div`
  position: relative;
  flex: 1;
  min-width: 0;
`;

const TruncationFade = styled.div`
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: var(--cds-spacing-07);
  background: linear-gradient(
    to bottom,
    rgba(0, 0, 0, 0),
    var(--cds-layer-02) 90%
  );
  pointer-events: none;
`;

const MarkdownBody = styled.div`
  /* Every markdown-produced element inherits the Carbon body type from the
     parent <div>, so <p>/<li>/<h*> don't render at browser defaults. */
  & *,
  & *::before,
  & *::after {
    font-size: inherit;
    line-height: inherit;
    font-family: inherit;
    letter-spacing: inherit;
  }
  & > :first-child {
    margin-top: 0;
  }
  & > :last-child {
    margin-bottom: 0;
  }
  & p {
    margin: 0 0 var(--cds-spacing-03) 0;
  }
  & ol,
  & ul {
    margin: 0 0 var(--cds-spacing-03) 0;
    padding-left: var(--cds-spacing-06);
  }
  & ol {
    list-style: decimal outside;
  }
  & ul {
    list-style: disc outside;
  }
  & li {
    margin: 0;
    padding-left: var(--cds-spacing-02);
  }
  & li + li {
    margin-top: var(--cds-spacing-02);
  }
  & li > p {
    margin: 0;
  }
  & h1,
  & h2,
  & h3,
  & h4,
  & h5,
  & h6 {
    font-weight: 600;
    margin: 0 0 var(--cds-spacing-02) 0;
  }
  & strong {
    font-weight: 600;
  }
  & em {
    font-style: italic;
  }
  & code {
    font-family: var(--cds-code-01-font-family);
    background: var(--cds-layer-01);
    padding: 0 var(--cds-spacing-02);
    border-radius: 2px;
  }
  & pre {
    background: var(--cds-layer-01);
    padding: var(--cds-spacing-03);
    border-radius: 4px;
    overflow-x: auto;
    margin: 0 0 var(--cds-spacing-03) 0;
  }
  & pre code {
    background: transparent;
    padding: 0;
  }
  & a {
    color: var(--cds-link-primary);
  }
`;

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

export function TokensStatCard({
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
              letterSpacing: 'var(--cds-helper-text-01-letter-spacing, 0.32px)',
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

export function ToolsCalledStatCard({current}: {current: number}) {
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

export function ModelCallsStatCard({
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

export const attachmentLabelStyle: React.CSSProperties = {
  fontSize: 'var(--cds-label-01-font-size)',
  lineHeight: 'var(--cds-label-01-line-height)',
  letterSpacing: 'var(--cds-label-01-letter-spacing)',
  color: 'var(--cds-text-secondary)',
};

export const tagStyle: React.CSSProperties = {
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

  const StatusIcon = agentData.status === 'COMPLETED' ? CheckmarkOutline : Time;

  const iterationCount = agentData.iterations.length;

  const accordionTitle = (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 'var(--cds-spacing-03)',
      }}
    >
      <StatusIcon size={16} />
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
        {(currentMessage ||
          (agentData.status !== 'COMPLETED' && activeTools.length > 0)) && (
          <ExpandableMessageBlock
            role="Assistant"
            borderColor="#8a3ffc"
            contents={currentMessage ? [currentMessage] : []}
            iterationNumber={iterationCount > 0 ? iterationCount : undefined}
          >
            {agentData.status !== 'COMPLETED' && activeTools.length > 0 && (
              <div
                style={{
                  marginTop: currentMessage ? 'var(--cds-spacing-05)' : 0,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 'var(--cds-spacing-02)',
                  flexWrap: 'wrap',
                }}
              >
                <span style={attachmentLabelStyle}>Tool calls</span>
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
          </ExpandableMessageBlock>
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
                <div style={{flex: 1, minWidth: 0}}>
                  <CodeSnippet type="multi" wrapText feedback="Copied!">
                    {inputText}
                  </CodeSnippet>
                </div>
                <Button
                  kind="ghost"
                  size="sm"
                  hasIconOnly
                  renderIcon={Maximize}
                  iconDescription="Expand"
                  tooltipPosition="top"
                  onClick={() => setIsInputModalOpen(true)}
                  aria-label="Expand"
                  // Match the vertical position of CodeSnippet's built-in
                  // Copy button (which sits ~spacing-02 from the snippet top).
                  style={{marginTop: 'var(--cds-spacing-03)'}}
                />
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
                <div style={{flex: 1, minWidth: 0}}>
                  <CodeSnippet type="multi" wrapText feedback="Copied!">
                    {outputText}
                  </CodeSnippet>
                </div>
                <Button
                  kind="ghost"
                  size="sm"
                  hasIconOnly
                  renderIcon={Maximize}
                  iconDescription="Expand"
                  tooltipPosition="top"
                  onClick={() => setIsOutputModalOpen(true)}
                  aria-label="Expand"
                  style={{marginTop: 'var(--cds-spacing-03)'}}
                />
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
        <div
          style={{
            display: 'flex',
            justifyContent: 'flex-end',
            padding: '0 var(--cds-spacing-05) var(--cds-spacing-03)',
          }}
        >
          <Button
            kind="ghost"
            size="sm"
            renderIcon={Copy}
            onClick={() => navigator.clipboard.writeText(inputText ?? '')}
          >
            Copy
          </Button>
        </div>
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
        <div
          style={{
            display: 'flex',
            justifyContent: 'flex-end',
            padding: '0 var(--cds-spacing-05) var(--cds-spacing-03)',
          }}
        >
          <Button
            kind="ghost"
            size="sm"
            renderIcon={Copy}
            onClick={() => navigator.clipboard.writeText(outputText ?? '')}
          >
            Copy
          </Button>
        </div>
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

// Normalize free-form text into CommonMark-friendly markdown so we can render
// it with react-markdown without falling back to pre-wrap. Two adjustments:
//   1. Insert a blank line before a list marker that follows non-blank text,
//      so `**Header**\n1. item` parses as a real list instead of one paragraph.
//   2. Convert remaining single newlines into markdown hard breaks (two trailing
//      spaces + newline), so `Line 1\nLine 2` renders as two visual lines.
function toMarkdown(text: string): string {
  const withListSeparators = text.replace(
    /([^\n])\n(\d+\.\s|[-*]\s)/g,
    '$1\n\n$2',
  );
  return withListSeparators.replace(/(?<!\n)\n(?!\n)/g, '  \n');
}

function ExpandableSegment({
  content,
  isFirst,
  blockIndex,
  blockCount,
  role,
}: {
  content: string;
  isFirst: boolean;
  blockIndex: number;
  blockCount: number;
  role: string;
}) {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isTruncated, setIsTruncated] = useState(false);
  const bodyRef = useRef<HTMLDivElement>(null);
  const modalHeading =
    blockCount > 1
      ? `${role} message — block ${blockIndex + 1} of ${blockCount}`
      : role === 'System'
        ? 'System prompt'
        : `${role} message`;

  useLayoutEffect(() => {
    const el = bodyRef.current;
    if (!el) {
      return;
    }
    setIsTruncated(el.scrollHeight > el.clientHeight + 1);
  }, [content]);

  return (
    <>
      <SegmentRow
        style={{marginTop: isFirst ? 0 : 'var(--cds-spacing-04)'}}
        $alwaysShowActions={isTruncated}
      >
        <SegmentBody>
          <MarkdownBody
            ref={bodyRef}
            style={{
              fontSize: 'var(--cds-body-compact-01-font-size)',
              lineHeight: '1.5',
              color: 'var(--cds-text-secondary)',
              wordBreak: 'break-word',
              maxHeight: 360,
              overflow: 'hidden',
            }}
          >
            <ReactMarkdown remarkPlugins={[remarkGfm]}>
              {toMarkdown(content)}
            </ReactMarkdown>
          </MarkdownBody>
          {isTruncated && <TruncationFade aria-hidden="true" />}
        </SegmentBody>
        <div className="segment-actions">
          <Button
            kind="ghost"
            size="sm"
            hasIconOnly
            renderIcon={Maximize}
            iconDescription="Expand"
            tooltipPosition="top"
            onClick={() => setIsModalOpen(true)}
            aria-label="Expand"
          />
          <Button
            kind="ghost"
            size="sm"
            hasIconOnly
            renderIcon={Copy}
            iconDescription="Copy"
            tooltipPosition="top"
            onClick={() => {
              navigator.clipboard.writeText(content);
            }}
            aria-label="Copy"
          />
        </div>
      </SegmentRow>
      <Modal
        open={isModalOpen}
        modalHeading={modalHeading}
        onRequestClose={() => setIsModalOpen(false)}
        size="lg"
        passiveModal
      >
        <div
          style={{
            display: 'flex',
            justifyContent: 'flex-end',
            padding: '0 var(--cds-spacing-05) var(--cds-spacing-03)',
          }}
        >
          <Button
            kind="ghost"
            size="sm"
            renderIcon={Copy}
            onClick={() => navigator.clipboard.writeText(content)}
          >
            Copy
          </Button>
        </div>
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
            language="markdown"
            value={content}
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

export function ExpandableMessageBlock({
  role,
  borderColor,
  contents,
  iterationNumber,
  children,
}: {
  role: string;
  borderColor: string;
  contents: string[];
  iterationNumber?: number;
  children?: React.ReactNode;
}) {
  const segments = contents.filter((c) => c.length > 0);

  return (
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
            color: 'var(--cds-text-secondary)',
          }}
        >
          {iterationNumber !== undefined
            ? `Iteration ${iterationNumber}`
            : role}
        </span>
      </div>
      {segments.map((segment, index) => (
        <ExpandableSegment
          key={index}
          content={segment}
          isFirst={index === 0}
          blockIndex={index}
          blockCount={segments.length}
          role={role}
        />
      ))}
      {children}
    </div>
  );
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
              borderColor="var(--cds-interactive)"
              contents={msg.content}
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
              borderColor="#8a3ffc"
              contents={msg.content}
              iterationNumber={msg.iterationNumber}
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
              borderColor="var(--cds-border-subtle-01)"
              contents={[agentData.systemPrompt]}
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

export {DefaultAgentDetail, IterationDetail, ToolCallDetail};
