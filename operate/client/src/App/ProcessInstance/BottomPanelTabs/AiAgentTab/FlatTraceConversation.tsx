/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, useState} from 'react';
import styled from 'styled-components';
import {Button, Modal, Tag, DefinitionTooltip} from '@carbon/react';
import {
  ArrowUpRight,
  Maximize,
  SortAscending,
  SortDescending,
  Tools,
} from '@carbon/icons-react';
import {Copy} from '@carbon/react/icons';
import type {AgentElementData} from 'modules/contexts/agentData.types';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {TOOL_DESCRIPTIONS} from 'modules/queries/agentInstances/historyToAgentElementData';
import {buildFlatTrace, type FlatTraceStep} from './buildFlatTrace';
import {
  ExpandableMessageBlock,
  attachmentLabelStyle,
  tagStyle,
} from './AgentDetailPanel';

type ToolInstanceLink = {
  innerInstanceKey: string;
  innerElementId: string;
  anchorElementId: string;
  displayName: string;
};

export type {ToolInstanceLink};

const ToolRow = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-03);
  min-width: 0;
  padding: var(--cds-spacing-04);
  background: var(--cds-layer-02);
  border-radius: 4px;
  border-left: 3px solid var(--cds-border-subtle-01);

  & > .tool-actions {
    opacity: 0;
    transition: opacity 120ms ease-out;
    flex-shrink: 0;
    display: flex;
  }

  &:hover > .tool-actions,
  &:focus-within > .tool-actions {
    opacity: 1;
  }
`;

// Carbon's compact definition tooltip (lighter than the standalone Tooltip).
// Suppress the default dotted-underline trigger styling so it reads cleanly
// around the token tag.
const MetricTooltip = styled(DefinitionTooltip)`
  .cds--definition-term {
    border-bottom: none;
    cursor: default;
    font-size: inherit;
  }
`;

const MonacoEditor = lazy(async () => {
  const [{loadMonaco}, Editor] = await Promise.all([
    import('modules/loadMonaco'),
    import('@monaco-editor/react'),
  ]);
  loadMonaco();
  return Editor;
});

function formatDuration(ms?: number): string | null {
  if (ms === undefined) {
    return null;
  }
  if (ms < 1000) {
    return `${ms}ms`;
  }
  return `${(ms / 1000).toFixed(1)}s`;
}

function StepTags({
  tokens,
  tokensInput,
  tokensOutput,
  durationMs,
}: {
  tokens?: number;
  tokensInput?: number;
  tokensOutput?: number;
  durationMs?: number;
}) {
  const duration = formatDuration(durationMs);
  if (tokens === undefined && duration === null) {
    return null;
  }
  const hasBreakdown = tokensInput !== undefined && tokensOutput !== undefined;
  const tooltipLabel = hasBreakdown
    ? `Input: ${tokensInput.toLocaleString()} · Output: ${tokensOutput.toLocaleString()}`
    : undefined;

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 'var(--cds-spacing-02)',
      }}
    >
      {tokens !== undefined &&
        (tooltipLabel !== undefined ? (
          <MetricTooltip definition={tooltipLabel} align="bottom" openOnHover>
            <Tag type="gray" size="sm">
              {tokens.toLocaleString()} tokens
            </Tag>
          </MetricTooltip>
        ) : (
          <Tag type="gray" size="sm">
            {tokens.toLocaleString()} tokens
          </Tag>
        ))}
      {duration !== null && (
        <Tag type="gray" size="sm">
          {duration}
        </Tag>
      )}
    </span>
  );
}

function prettifyToolId(id: string): string {
  return id.replace(/([a-z0-9])([A-Z])/g, '$1 $2');
}

function ToolBlock({
  step,
  toolInstanceMap,
  resolveDisplayName,
}: {
  step: Extract<FlatTraceStep, {kind: 'tool'}>;
  toolInstanceMap: Map<string, ToolInstanceLink>;
  resolveDisplayName: (toolId: string) => string;
}) {
  const {selectElementInstance} = useProcessInstanceElementSelection();
  const [isModalOpen, setIsModalOpen] = useState(false);

  const link = toolInstanceMap.get(step.name);

  const compactOutput =
    step.output !== undefined
      ? JSON.stringify(step.output).replace(/\s+/g, ' ')
      : undefined;
  const outputPreview =
    compactOutput === undefined
      ? 'No output'
      : compactOutput.slice(0, 80) + (compactOutput.length > 80 ? '…' : '');

  const inputKeys = Object.keys(step.input);
  const inputText =
    inputKeys.length > 0 ? JSON.stringify(step.input, null, 2) : '';
  const outputText =
    step.output !== undefined ? JSON.stringify(step.output, null, 2) : '';
  const description =
    TOOL_DESCRIPTIONS[step.name] ?? 'No description available.';

  return (
    <>
      <ToolRow data-testid="tool-detail-block">
        {/* Tool icon */}
        <span
          style={{
            color: 'var(--cds-icon-secondary)',
            display: 'flex',
            flexShrink: 0,
          }}
        >
          <Tools size={16} />
        </span>

        {/* Tool name */}
        <span
          style={{
            fontWeight: 400,
            fontSize: 'var(--cds-body-compact-01-font-size)',
            color: 'var(--cds-text-secondary)',
            flexShrink: 0,
          }}
        >
          {resolveDisplayName(step.name)}
        </span>

        {/* Monospace output preview — fills remaining space, truncates */}
        <span
          style={{
            fontFamily: 'var(--cds-code-01-font-family)',
            fontSize: 'var(--cds-code-01-font-size, 12px)',
            color: 'var(--cds-text-secondary)',
            flex: 1,
            minWidth: 0,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {outputPreview}
        </span>

        {/* Action buttons — revealed on hover/focus-within */}
        <div className="tool-actions">
          <Button
            kind="ghost"
            size="sm"
            hasIconOnly
            renderIcon={Maximize}
            iconDescription="Expand"
            tooltipPosition="bottom"
            tooltipAlignment="end"
            aria-label="Expand"
            data-testid="expand-tool-detail"
            onClick={() => setIsModalOpen(true)}
            style={{color: 'var(--cds-icon-secondary)'}}
          />
          {link !== undefined && (
            <Button
              kind="ghost"
              size="sm"
              hasIconOnly
              renderIcon={ArrowUpRight}
              iconDescription="Execution details"
              tooltipPosition="bottom"
              tooltipAlignment="end"
              aria-label="Execution details"
              data-testid="open-tool-execution-details"
              onClick={() =>
                selectElementInstance({
                  elementId: link.innerElementId,
                  elementInstanceKey: link.innerInstanceKey,
                  anchorElementId: link.anchorElementId,
                })
              }
              style={{color: 'var(--cds-icon-secondary)'}}
            />
          )}
        </div>
      </ToolRow>

      <Modal
        open={isModalOpen}
        modalHeading={resolveDisplayName(step.name)}
        onRequestClose={() => setIsModalOpen(false)}
        size="lg"
        passiveModal
      >
        {/* Tool description */}
        <div
          style={{
            paddingBottom: 'var(--cds-spacing-04)',
            fontSize: 'var(--cds-body-compact-01-font-size)',
            lineHeight: '1.5',
            color: 'var(--cds-text-secondary)',
          }}
        >
          {description}
        </div>

        {/* Input and Output side by side */}
        <Suspense
          fallback={<div style={{height: '40vh'}}>Loading editor…</div>}
        >
          <div
            style={{
              display: 'flex',
              gap: 'var(--cds-spacing-05)',
              paddingBottom: 'var(--cds-spacing-05)',
            }}
          >
            {/* Input column */}
            <div
              style={{
                flex: 1,
                minWidth: 0,
                display: 'flex',
                flexDirection: 'column',
                gap: 'var(--cds-spacing-03)',
              }}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                }}
              >
                <p
                  style={{
                    margin: 0,
                    fontWeight: 600,
                    fontSize: 'var(--cds-body-compact-01-font-size)',
                  }}
                >
                  Input
                </p>
                <Button
                  kind="ghost"
                  size="sm"
                  hasIconOnly
                  renderIcon={Copy}
                  iconDescription="Copy input"
                  tooltipPosition="left"
                  aria-label="Copy input"
                  data-testid="copy-tool-input"
                  onClick={() =>
                    navigator.clipboard.writeText(inputText || '{}')
                  }
                />
              </div>
              <MonacoEditor
                height="40vh"
                language="json"
                value={inputText || '{}'}
                options={{
                  readOnly: true,
                  minimap: {enabled: false},
                  fontSize: 13,
                  wordWrap: 'on',
                  scrollBeyondLastLine: false,
                }}
              />
            </div>

            {/* Output column */}
            <div
              style={{
                flex: 1,
                minWidth: 0,
                display: 'flex',
                flexDirection: 'column',
                gap: 'var(--cds-spacing-03)',
              }}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                }}
              >
                <p
                  style={{
                    margin: 0,
                    fontWeight: 600,
                    fontSize: 'var(--cds-body-compact-01-font-size)',
                  }}
                >
                  Output
                </p>
                <Button
                  kind="ghost"
                  size="sm"
                  hasIconOnly
                  renderIcon={Copy}
                  iconDescription="Copy output"
                  tooltipPosition="left"
                  aria-label="Copy output"
                  data-testid="copy-tool-output"
                  onClick={() =>
                    navigator.clipboard.writeText(outputText || 'No output')
                  }
                />
              </div>
              <MonacoEditor
                height="40vh"
                language="json"
                value={outputText || 'No output'}
                options={{
                  readOnly: true,
                  minimap: {enabled: false},
                  fontSize: 13,
                  wordWrap: 'on',
                  scrollBeyondLastLine: false,
                }}
              />
            </div>
          </div>
        </Suspense>
      </Modal>
    </>
  );
}

function orderSteps(
  steps: FlatTraceStep[],
  isNewestFirst: boolean,
): FlatTraceStep[] {
  if (!isNewestFirst) {
    return steps;
  }
  // A turn begins at a user/assistant step and includes the tool steps that follow it.
  const turns: FlatTraceStep[][] = [];
  for (const step of steps) {
    if (step.kind === 'tool' && turns.length > 0) {
      turns[turns.length - 1]!.push(step);
    } else {
      turns.push([step]);
    }
  }
  return turns.reverse().flat();
}

function FlatTraceConversation({
  agentData,
  toolInstanceMap,
}: {
  agentData: AgentElementData;
  toolInstanceMap: Map<string, ToolInstanceLink>;
}) {
  const [isNewestFirst, setIsNewestFirst] = useState(true);
  const steps = orderSteps(buildFlatTrace(agentData), isNewestFirst);

  const resolveDisplayName = (toolId: string): string =>
    toolInstanceMap.get(toolId)?.displayName ?? prettifyToolId(toolId);

  const SortIcon = isNewestFirst ? SortDescending : SortAscending;

  return (
    <div
      data-testid="flat-trace-conversation"
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--cds-spacing-04)',
        width: '100%',
      }}
    >
      <button
        type="button"
        data-testid="toggle-flat-trace-order"
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
      {steps.map((step) => {
        if (step.kind === 'user') {
          return (
            <ExpandableMessageBlock
              key={step.key}
              role="User"
              borderColor="var(--cds-interactive)"
              contents={step.content}
            />
          );
        }
        if (step.kind === 'assistant') {
          return (
            <ExpandableMessageBlock
              key={step.key}
              role="Assistant"
              borderColor="#8a3ffc"
              contents={step.content}
              headerMeta={
                <StepTags
                  tokens={step.tokens}
                  tokensInput={step.tokensInput}
                  tokensOutput={step.tokensOutput}
                  durationMs={step.durationMs}
                />
              }
            >
              {step.toolNames.length > 0 && (
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
                  {step.toolNames.map((name) => (
                    <span key={name} style={tagStyle}>
                      {resolveDisplayName(name)}
                    </span>
                  ))}
                </div>
              )}
            </ExpandableMessageBlock>
          );
        }
        return (
          <ToolBlock
            key={step.key}
            step={step as Extract<FlatTraceStep, {kind: 'tool'}>}
            toolInstanceMap={toolInstanceMap}
            resolveDisplayName={resolveDisplayName}
          />
        );
      })}
    </div>
  );
}

export {FlatTraceConversation};
