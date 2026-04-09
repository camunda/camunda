/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {Loading} from '@carbon/react';
import {useAgentContext} from 'modules/agentContext/useAgentContext';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {useVariableScopeKey} from 'modules/hooks/variables';
import type {
  AgentConversationModel,
  AgentIteration,
} from 'modules/agentContext/types';

/**
 * Dev-only tab that retrieves and visualizes the `agentContext` variable
 * for the currently selected AI Agent AHSP element instance.
 *
 * Gated by the IS_AGENT_CONTEXT_DEBUG_ENABLED feature flag and intended
 * for spike exploration only — not a production UI.
 */
function AgentContextTab() {
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const scopeKey = useVariableScopeKey();

  const {data, isLoading, isError, error} = useAgentContext({
    processInstanceKey: processInstanceId,
    elementInstanceKey: scopeKey ?? '',
    enabled: !!scopeKey,
  });

  // Dump the full model to console for developer inspection
  useEffect(() => {
    if (data) {
      console.group(
        '%c[Agent Context Debug] Conversation Model',
        'color: #A56EFF; font-weight: bold',
      );
      console.log('Storage type:', data.storageType);
      console.log('Raw size:', `${(data.rawSize / 1024).toFixed(1)} KB`);
      if (data.warnings.length > 0) {
        console.warn('Warnings:', data.warnings);
      }
      if (data.provider || data.model) {
        console.log('Provider:', data.provider, '| Model:', data.model);
      }
      if (data.systemPrompt) {
        console.log('System prompt:', data.systemPrompt);
      }
      if (data.userPrompt) {
        console.log('User prompt:', data.userPrompt);
      }
      console.log(`Iterations (${data.iterations.length}):`, data.iterations);
      if (data.totalTokens != null) {
        console.log('Token usage:', {
          input: data.totalInputTokens,
          output: data.totalOutputTokens,
          total: data.totalTokens,
        });
      }
      console.log('Full messages:', data.messages);
      console.log('Full model:', data);
      console.groupEnd();
    }
  }, [data]);

  if (!scopeKey) {
    return (
      <Panel>Select an element instance to inspect its agent context.</Panel>
    );
  }

  if (isLoading) {
    return <Loading data-testid="agent-context-spinner" />;
  }

  if (isError) {
    return (
      <Panel>
        Failed to fetch agentContext variable.
        {error instanceof Error ? ` ${error.message}` : ''}
      </Panel>
    );
  }

  if (!data) {
    return (
      <Panel>
        No <code>agentContext</code> variable found for this element instance.
        This element may not be an AI Agent AHSP, or the variable has not been
        set yet.
      </Panel>
    );
  }

  return (
    <div
      style={{
        padding: '1rem',
        overflow: 'auto',
        height: '100%',
        fontFamily: 'monospace',
        fontSize: '13px',
      }}
    >
      <WarningBanner warnings={data.warnings} />
      <MetadataSection model={data} />
      {data.storageType === 'document_reference' && !data.documentResolved && (
        <DocumentRefSection model={data} />
      )}
      {(data.storageType === 'inline' || data.documentResolved) && (
        <IterationsSection iterations={data.iterations} />
      )}
    </div>
  );
}

function Panel({children}: {children: React.ReactNode}) {
  return <div style={{padding: '1rem', color: '#525252'}}>{children}</div>;
}

function WarningBanner({warnings}: {warnings: string[]}) {
  if (warnings.length === 0) {
    return null;
  }
  return (
    <div
      style={{
        background: '#fdf0d5',
        border: '1px solid #f1c21b',
        borderRadius: 4,
        padding: '0.5rem 1rem',
        marginBottom: '1rem',
      }}
    >
      <strong>Spike findings / warnings:</strong>
      <ul style={{margin: '0.25rem 0 0', paddingLeft: '1.25rem'}}>
        {warnings.map((w, i) => (
          <li key={i}>{w}</li>
        ))}
      </ul>
    </div>
  );
}

function MetadataSection({model}: {model: AgentConversationModel}) {
  return (
    <div style={{marginBottom: '1rem'}}>
      <h4 style={{margin: '0 0 0.5rem'}}>Agent Context Overview</h4>
      <table style={{borderCollapse: 'collapse', width: '100%'}}>
        <tbody>
          <Row label="Storage" value={model.storageType} />
          {model.conversationType && (
            <Row label="Conversation type" value={model.conversationType} />
          )}
          {model.documentResolved != null && (
            <Row
              label="Document resolved"
              value={model.documentResolved ? 'Yes' : 'No'}
            />
          )}
          <Row
            label="Payload size"
            value={`${(model.rawSize / 1024).toFixed(1)} KB`}
          />
          <Row label="Truncated" value={model.isTruncated ? 'Yes' : 'No'} />
          {model.state && <Row label="Agent state" value={model.state} />}
          {model.conversationId && (
            <Row label="Conversation ID" value={model.conversationId} />
          )}
          {model.provider && <Row label="Provider" value={model.provider} />}
          {model.model && <Row label="Model" value={model.model} />}
          {model.totalModelCalls != null && (
            <Row label="Model calls" value={String(model.totalModelCalls)} />
          )}
          {model.totalIterations != null && (
            <Row label="Iterations" value={String(model.totalIterations)} />
          )}
          {(model.totalTokens != null ||
            model.totalInputTokens != null ||
            model.totalOutputTokens != null) && (
            <Row
              label="Tokens"
              value={`${model.totalInputTokens ?? '?'} in / ${model.totalOutputTokens ?? '?'} out / ${model.totalTokens ?? '?'} total`}
            />
          )}
        </tbody>
      </table>

      {model.systemPrompt && (
        <details style={{marginTop: '0.5rem'}}>
          <summary>System prompt</summary>
          <pre
            style={{
              whiteSpace: 'pre-wrap',
              background: '#f4f4f4',
              padding: '0.5rem',
              borderRadius: 4,
            }}
          >
            {model.systemPrompt}
          </pre>
        </details>
      )}
      {model.userPrompt && (
        <details style={{marginTop: '0.5rem'}}>
          <summary>User prompt</summary>
          <pre
            style={{
              whiteSpace: 'pre-wrap',
              background: '#f4f4f4',
              padding: '0.5rem',
              borderRadius: 4,
            }}
          >
            {model.userPrompt}
          </pre>
        </details>
      )}
    </div>
  );
}

function DocumentRefSection({model}: {model: AgentConversationModel}) {
  const convDoc = model.conversationDocument;
  const ref = model.documentReference;
  const docInfo = convDoc ?? ref;
  if (!docInfo) {
    return null;
  }
  return (
    <div
      style={{
        background: '#edf5ff',
        border: '1px solid #4589ff',
        borderRadius: 4,
        padding: '0.75rem 1rem',
        marginBottom: '1rem',
      }}
    >
      <strong>
        {convDoc
          ? 'Conversation Document (attempting to resolve…)'
          : 'Document Reference (cannot resolve from frontend)'}
      </strong>
      <table
        style={{borderCollapse: 'collapse', width: '100%', marginTop: '0.5rem'}}
      >
        <tbody>
          <Row label="documentId" value={docInfo.documentId} />
          {'storeId' in docInfo && docInfo.storeId && (
            <Row label="storeId" value={docInfo.storeId} />
          )}
          {docInfo.contentHash && (
            <Row label="contentHash" value={docInfo.contentHash} />
          )}
          {docInfo.metadata?.size != null && (
            <Row label="size" value={`${docInfo.metadata.size} bytes`} />
          )}
          {docInfo.metadata?.fileName && (
            <Row label="fileName" value={docInfo.metadata.fileName} />
          )}
        </tbody>
      </table>
      {model.previousDocuments && model.previousDocuments.length > 0 && (
        <details style={{marginTop: '0.5rem'}}>
          <summary>
            Previous document snapshots ({model.previousDocuments.length})
          </summary>
          {model.previousDocuments.map((prev, i) => (
            <div
              key={i}
              style={{
                marginTop: '0.25rem',
                padding: '0.25rem 0.5rem',
                background: '#f4f4f4',
                borderRadius: 4,
                fontSize: '12px',
              }}
            >
              {prev.documentId}
              {prev.metadata?.size != null && ` (${prev.metadata.size} bytes)`}
            </div>
          ))}
        </details>
      )}
    </div>
  );
}

function IterationsSection({iterations}: {iterations: AgentIteration[]}) {
  if (iterations.length === 0) {
    return (
      <Panel>
        No iterations reconstructed. The message format may not match known
        patterns.
      </Panel>
    );
  }
  return (
    <div>
      <h4 style={{margin: '0 0 0.5rem'}}>
        Conversation Trail ({iterations.length} iteration
        {iterations.length !== 1 ? 's' : ''})
      </h4>
      {iterations.map((iter) => (
        <IterationCard key={iter.index} iteration={iter} />
      ))}
    </div>
  );
}

function IterationCard({iteration}: {iteration: AgentIteration}) {
  const decisionLabel =
    iteration.decision === 'tool_call'
      ? `Tool call → ${iteration.toolCalls.map((tc) => tc.name).join(', ')}`
      : 'Direct response';

  return (
    <div
      style={{
        border: '1px solid #e0e0e0',
        borderRadius: 4,
        padding: '0.75rem 1rem',
        marginBottom: '0.5rem',
        background: '#fff',
      }}
    >
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          marginBottom: '0.25rem',
        }}
      >
        <strong>Iteration {iteration.index}</strong>
        <span
          style={{
            fontSize: '12px',
            padding: '2px 8px',
            borderRadius: 12,
            background:
              iteration.decision === 'tool_call' ? '#d0e2ff' : '#defbe6',
            color: iteration.decision === 'tool_call' ? '#0043ce' : '#0e6027',
          }}
        >
          {decisionLabel}
        </span>
      </div>

      {iteration.reasoning && (
        <details>
          <summary
            style={{cursor: 'pointer', fontSize: '12px', color: '#6f6f6f'}}
          >
            Reasoning
          </summary>
          <pre
            style={{
              whiteSpace: 'pre-wrap',
              background: '#f4f4f4',
              padding: '0.5rem',
              borderRadius: 4,
              fontSize: '12px',
            }}
          >
            {iteration.reasoning}
          </pre>
        </details>
      )}

      {iteration.toolCalls.map((tc, i) => (
        <details key={i} style={{marginTop: '0.25rem'}}>
          <summary
            style={{cursor: 'pointer', fontSize: '12px', color: '#6f6f6f'}}
          >
            Tool: {tc.name} {tc.id ? `(${tc.id})` : ''}
          </summary>
          <pre
            style={{
              whiteSpace: 'pre-wrap',
              background: '#f4f4f4',
              padding: '0.5rem',
              borderRadius: 4,
              fontSize: '12px',
            }}
          >
            {typeof tc.arguments === 'string'
              ? tc.arguments
              : JSON.stringify(tc.arguments, null, 2)}
          </pre>
          {iteration.toolResults[i] && (
            <>
              <div
                style={{
                  fontSize: '12px',
                  color: '#6f6f6f',
                  marginTop: '0.25rem',
                }}
              >
                Result:
              </div>
              <pre
                style={{
                  whiteSpace: 'pre-wrap',
                  background: '#f4f4f4',
                  padding: '0.5rem',
                  borderRadius: 4,
                  fontSize: '12px',
                }}
              >
                {typeof iteration.toolResults[i].content === 'string'
                  ? iteration.toolResults[i].content
                  : JSON.stringify(iteration.toolResults[i].content, null, 2)}
              </pre>
            </>
          )}
        </details>
      ))}

      {iteration.assistantMessage &&
        iteration.decision === 'direct_response' && (
          <div style={{marginTop: '0.25rem', fontSize: '13px'}}>
            {iteration.assistantMessage}
          </div>
        )}

      {iteration.timestamp && (
        <div style={{fontSize: '11px', color: '#a8a8a8', marginTop: '0.25rem'}}>
          {iteration.timestamp}
        </div>
      )}
    </div>
  );
}

function Row({label, value}: {label: string; value: string}) {
  return (
    <tr>
      <td
        style={{
          padding: '2px 8px 2px 0',
          color: '#6f6f6f',
          whiteSpace: 'nowrap',
          verticalAlign: 'top',
        }}
      >
        {label}
      </td>
      <td style={{padding: '2px 0'}}>{value}</td>
    </tr>
  );
}

export {AgentContextTab};
