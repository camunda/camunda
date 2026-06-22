/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {lazy, Suspense, useState} from 'react';
import {Button, Modal, Tag} from '@carbon/react';
import {
  ArrowUpRight,
  Maximize,
  SortAscending,
  SortDescending,
} from '@carbon/icons-react';
import {Copy} from '@carbon/react/icons';
import type {AgentElementData} from 'modules/contexts/agentData.types';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {TOOL_DESCRIPTIONS} from 'modules/queries/agentInstances/historyToAgentElementData';
import {buildFlatTrace, type FlatTraceStep} from './buildFlatTrace';
import {ExpandableMessageBlock} from './AgentDetailPanel';

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
  durationMs,
}: {
  tokens?: number;
  durationMs?: number;
}) {
  const duration = formatDuration(durationMs);
  if (tokens === undefined && duration === null) {
    return null;
  }
  return (
    <div
      style={{
        display: 'flex',
        gap: 'var(--cds-spacing-02)',
        flexWrap: 'wrap',
      }}
    >
      {tokens !== undefined && (
        <Tag type="gray" size="sm">
          {tokens.toLocaleString()} tokens
        </Tag>
      )}
      {duration !== null && (
        <Tag type="gray" size="sm">
          {duration}
        </Tag>
      )}
    </div>
  );
}

function ToolBlock({step}: {step: Extract<FlatTraceStep, {kind: 'tool'}>}) {
  const {selectElement} = useProcessInstanceElementSelection();
  const [isModalOpen, setIsModalOpen] = useState(false);

  const inputKeys = Object.keys(step.input);
  const compactInput = JSON.stringify(step.input).replace(/\s+/g, ' ');
  const inputPreview =
    inputKeys.length === 0
      ? 'No input'
      : compactInput.slice(0, 80) + (compactInput.length > 80 ? '…' : '');

  const inputText =
    inputKeys.length > 0 ? JSON.stringify(step.input, null, 2) : '';
  const outputText =
    step.output !== undefined ? JSON.stringify(step.output, null, 2) : '';
  const duration = formatDuration(step.durationMs);
  const description =
    TOOL_DESCRIPTIONS[step.name] ?? 'No description available.';

  return (
    <>
      <div
        data-testid="tool-detail-block"
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--cds-spacing-03)',
          padding: 'var(--cds-spacing-02) 0',
          minWidth: 0,
        }}
      >
        {/* Tool name */}
        <span
          style={{
            fontWeight: 400,
            fontSize: 'var(--cds-body-compact-01-font-size)',
            color: 'var(--cds-text-secondary)',
            flexShrink: 0,
          }}
        >
          {step.name}
        </span>

        {/* Execution time tag */}
        {duration !== null && (
          <Tag type="gray" size="sm">
            {duration}
          </Tag>
        )}

        {/* Monospace input preview */}
        <span
          style={{
            fontFamily: 'var(--cds-code-01-font-family)',
            fontSize: 'var(--cds-code-01-font-size, 12px)',
            color: 'var(--cds-text-secondary)',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            flex: 1,
            minWidth: 0,
          }}
        >
          {inputPreview}
        </span>

        {/* Right-aligned action buttons */}
        <div style={{display: 'flex', flexShrink: 0, marginLeft: 'auto'}}>
          <Button
            kind="ghost"
            size="sm"
            hasIconOnly
            renderIcon={Maximize}
            iconDescription="Expand"
            tooltipPosition="left"
            aria-label="Expand"
            data-testid="expand-tool-detail"
            onClick={() => setIsModalOpen(true)}
            style={{color: 'var(--cds-icon-secondary)'}}
          />
          {step.hasInstance && (
            <Button
              kind="ghost"
              size="sm"
              hasIconOnly
              renderIcon={ArrowUpRight}
              iconDescription="Execution details"
              tooltipPosition="left"
              aria-label="Execution details"
              data-testid="open-tool-execution-details"
              onClick={() => selectElement({elementId: step.name})}
              style={{color: 'var(--cds-icon-secondary)'}}
            />
          )}
        </div>
      </div>

      <Modal
        open={isModalOpen}
        modalHeading={step.name}
        onRequestClose={() => setIsModalOpen(false)}
        size="lg"
        passiveModal
      >
        {/* Tool description */}
        <div
          style={{
            padding: '0 var(--cds-spacing-05) var(--cds-spacing-04)',
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
              padding: '0 var(--cds-spacing-05) var(--cds-spacing-05)',
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

function FlatTraceConversation({agentData}: {agentData: AgentElementData}) {
  const [isNewestFirst, setIsNewestFirst] = useState(false);
  const steps = orderSteps(buildFlatTrace(agentData), isNewestFirst);

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
      {(() => {
        const rendered: React.ReactNode[] = [];
        let i = 0;
        while (i < steps.length) {
          const step = steps[i]!;
          if (step.kind === 'user') {
            rendered.push(
              <ExpandableMessageBlock
                key={step.key}
                role="User"
                borderColor="var(--cds-interactive)"
                contents={step.content}
              />,
            );
            i++;
          } else if (step.kind === 'assistant') {
            rendered.push(
              <ExpandableMessageBlock
                key={step.key}
                role="Assistant"
                borderColor="#8a3ffc"
                contents={step.content}
                headerMeta={
                  <StepTags tokens={step.tokens} durationMs={step.durationMs} />
                }
              />,
            );
            i++;
          } else {
            // Collect all consecutive tool steps
            const toolGroup: Extract<FlatTraceStep, {kind: 'tool'}>[] = [];
            while (i < steps.length && steps[i]!.kind === 'tool') {
              toolGroup.push(
                steps[i] as Extract<FlatTraceStep, {kind: 'tool'}>,
              );
              i++;
            }
            rendered.push(
              <div
                key={toolGroup[0]!.key}
                style={{
                  background: 'var(--cds-layer-01)',
                  border: '1px solid var(--cds-border-subtle-01)',
                  borderRadius: '4px',
                  padding: 'var(--cds-spacing-02) var(--cds-spacing-03)',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 'var(--cds-spacing-02)',
                }}
              >
                {toolGroup.map((toolStep) => (
                  <ToolBlock key={toolStep.key} step={toolStep} />
                ))}
              </div>,
            );
          }
        }
        return rendered;
      })()}
    </div>
  );
}

export {FlatTraceConversation};
