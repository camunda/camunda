/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, useState} from 'react';
import {Button, Modal} from '@carbon/react';
import {Tools, Time, ArrowUpRight} from '@carbon/icons-react';
import {Copy} from '@carbon/react/icons';
import type {AgentElementData} from 'modules/contexts/agentData.types';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
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

const lightTagStyle: React.CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 4,
  padding: '0 var(--cds-spacing-02)',
  borderRadius: 4,
  fontSize: 'var(--cds-label-01-font-size)',
  lineHeight: 'var(--cds-label-01-line-height)',
  letterSpacing: 'var(--cds-label-01-letter-spacing)',
  color: 'var(--cds-text-secondary)',
  background: 'var(--cds-layer-01)',
  whiteSpace: 'nowrap',
};

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
        marginTop: 'var(--cds-spacing-03)',
        display: 'flex',
        gap: 'var(--cds-spacing-02)',
        flexWrap: 'wrap',
      }}
    >
      {tokens !== undefined && (
        <span style={lightTagStyle}>{tokens.toLocaleString()} tokens</span>
      )}
      {duration !== null && (
        <span style={lightTagStyle}>
          <Time size={12} />
          {duration}
        </span>
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

  return (
    <>
      <div
        data-testid="open-tool-detail-block"
        role="button"
        tabIndex={0}
        onClick={() => setIsModalOpen(true)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            setIsModalOpen(true);
          }
        }}
        style={{
          cursor: 'pointer',
          padding: 'var(--cds-spacing-03) var(--cds-spacing-04)',
          background: 'var(--cds-layer-01)',
          borderRadius: 4,
          border: '1px solid var(--cds-border-subtle-01)',
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--cds-spacing-03)',
        }}
      >
        <Tools size={16} style={{flexShrink: 0}} />
        <span
          style={{
            fontWeight: 600,
            fontSize: 'var(--cds-body-compact-01-font-size)',
            flexShrink: 0,
          }}
        >
          {step.name}
        </span>
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
        {duration !== null && (
          <span style={{...lightTagStyle, flexShrink: 0}}>
            <Time size={12} />
            {duration}
          </span>
        )}
        {/* Jump to the BPMN element without opening the modal. Conditional on
            an inner instance existing (State A always has one). */}
        {step.hasInstance && (
          <Button
            kind="ghost"
            size="sm"
            hasIconOnly
            renderIcon={ArrowUpRight}
            iconDescription="Go to element"
            tooltipPosition="left"
            aria-label="Go to element"
            data-testid="goto-tool-element"
            onClick={(e) => {
              e.stopPropagation();
              selectElement({elementId: step.name});
            }}
          />
        )}
      </div>

      <Modal
        open={isModalOpen}
        modalHeading={step.name}
        onRequestClose={() => setIsModalOpen(false)}
        size="lg"
        passiveModal
      >
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '0 var(--cds-spacing-05) var(--cds-spacing-03)',
          }}
        >
          {step.hasInstance ? (
            <Button
              kind="ghost"
              size="sm"
              renderIcon={ArrowUpRight}
              onClick={() => {
                setIsModalOpen(false);
                selectElement({elementId: step.name});
              }}
            >
              Go to element
            </Button>
          ) : (
            <span />
          )}
          <Button
            kind="ghost"
            size="sm"
            renderIcon={Copy}
            onClick={() =>
              navigator.clipboard.writeText(
                `Input:\n${inputText}\n\nOutput:\n${outputText}`,
              )
            }
          >
            Copy
          </Button>
        </div>
        <Suspense
          fallback={<div style={{height: '30vh'}}>Loading editor…</div>}
        >
          <div style={{padding: '0 var(--cds-spacing-05)'}}>
            <p style={{margin: '0 0 var(--cds-spacing-02)', fontWeight: 600}}>
              Input
            </p>
            <MonacoEditor
              height="25vh"
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
            <p
              style={{
                margin: 'var(--cds-spacing-05) 0 var(--cds-spacing-02)',
                fontWeight: 600,
              }}
            >
              Output
            </p>
            <MonacoEditor
              height="25vh"
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
        </Suspense>
      </Modal>
    </>
  );
}

function FlatTraceConversation({agentData}: {agentData: AgentElementData}) {
  const steps = buildFlatTrace(agentData);

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
            >
              <StepTags tokens={step.tokens} durationMs={step.durationMs} />
            </ExpandableMessageBlock>
          );
        }
        return <ToolBlock key={step.key} step={step} />;
      })}
    </div>
  );
}

export {FlatTraceConversation};
