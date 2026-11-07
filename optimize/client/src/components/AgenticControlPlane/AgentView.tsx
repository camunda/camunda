/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ReactNode} from 'react';

import {t} from 'translation';

import {FailureKindTag, failureElement, limitLabel} from './FailureReasonLabel';
import {
  formatCount,
  formatDecimal,
  formatDuration,
  formatPercentage,
  formatTokens,
  MetricTable,
  type MetricColumn,
} from './MetricTable';
import type {AgentDetail, AgentModelUsage, AgentTool, AgentUsage, FailureReason} from './service';

import './AgentView.scss';

interface AgentViewProps {
  agent: AgentDetail;
  // The agent's KPI and trend tiles, rendered above the tables.
  children?: ReactNode;
}

export function AgentView({agent, children}: AgentViewProps) {
  return (
    <div className="AgentView">
      {children}
      <Section titleKey="agenticControlPlane.agentView.failureReasons.title">
        <p className="section-description">
          {t('agenticControlPlane.agentView.failureReasons.description', {
            failed: formatCount(agent.failedRuns),
          })}
        </p>
        <MetricTable
          className="failureReasons"
          columns={FAILURE_COLUMNS}
          rows={agent.failureReasons}
          rowKey={(_, index) => String(index)}
          emptyKey="agenticControlPlane.agentView.failureReasons.empty"
        />
      </Section>
      <Section titleKey="agenticControlPlane.agentView.limitHits.title">
        <MetricTable
          className="limitHits"
          columns={LIMIT_HIT_COLUMNS}
          rows={agent.limitHits}
          rowKey={({limit}) => limit}
          emptyKey="agenticControlPlane.agentView.limitHits.empty"
        />
      </Section>
      <div className="tableGrid">
        <Section titleKey="agenticControlPlane.agentView.tools.title">
          {agent.toolsAvailable ? (
            <MetricTable
              className="tools"
              columns={TOOL_COLUMNS}
              rows={agent.tools}
              rowKey={({toolFlowNodeId}) => toolFlowNodeId}
              emptyKey="agenticControlPlane.agentView.tools.empty"
            />
          ) : (
            <p className="note toolsUnavailable">
              {t('agenticControlPlane.agentView.tools.unavailable')}
            </p>
          )}
        </Section>
        <Section titleKey="agenticControlPlane.agentView.models.title">
          <MetricTable
            className="models"
            columns={MODEL_COLUMNS}
            rows={agent.models}
            rowKey={({model, provider}) => `${provider ?? ''}-${model}`}
            emptyKey="agenticControlPlane.agentView.models.empty"
          />
        </Section>
      </div>
      <Section titleKey="agenticControlPlane.agentView.whereUsed.title">
        <MetricTable
          className="whereUsed"
          columns={whereUsedColumns(agent.whereUsed)}
          rows={agent.whereUsed}
          rowKey={({rootProcessDefinitionKey}) => rootProcessDefinitionKey}
          emptyKey="agenticControlPlane.agentView.whereUsed.empty"
        />
      </Section>
    </div>
  );
}

function Section({titleKey, children}: {titleKey: string; children: ReactNode}) {
  return (
    <section className="agentSection">
      <h3 className="section-title">{t(titleKey)}</h3>
      <hr className="section-divider" />
      {children}
    </section>
  );
}

const LIMIT_HIT_COLUMNS: MetricColumn<AgentDetail['limitHits'][number]>[] = [
  {
    titleKey: 'agenticControlPlane.agentView.limitHits.limit',
    render: ({limit}) => limitLabel(limit),
  },
  {
    titleKey: 'agenticControlPlane.agentView.limitHits.runs',
    numeric: true,
    render: ({runs}) => formatCount(runs),
  },
];

const FAILURE_COLUMNS: MetricColumn<FailureReason>[] = [
  {
    titleKey: 'agenticControlPlane.agentView.failureReasons.kind',
    render: ({kind}) => <FailureKindTag kind={kind} />,
  },
  {
    titleKey: 'agenticControlPlane.agentView.failureReasons.element',
    render: (reason) => failureElement(reason),
  },
  {
    titleKey: 'agenticControlPlane.agentView.failureReasons.errorType',
    render: ({errorType}) => errorType ?? '–',
  },
  {
    titleKey: 'agenticControlPlane.agentView.failureReasons.message',
    render: ({message}) => message ?? '–',
  },
  {
    titleKey: 'agenticControlPlane.agentView.failureReasons.runs',
    numeric: true,
    render: ({runs}) => formatCount(runs),
  },
];

const TOOL_COLUMNS: MetricColumn<AgentTool>[] = [
  {
    titleKey: 'agenticControlPlane.agentView.tools.tool',
    render: ({toolName, toolFlowNodeId}) => toolName ?? toolFlowNodeId,
  },
  {
    titleKey: 'agenticControlPlane.agentView.tools.calls',
    numeric: true,
    render: ({calls}) => formatCount(calls),
  },
  {
    titleKey: 'agenticControlPlane.agentView.tools.callsPerRun',
    numeric: true,
    render: ({callsPerRun}) => formatDecimal(callsPerRun),
  },
  {
    titleKey: 'agenticControlPlane.agentView.tools.failureRate',
    numeric: true,
    render: ({failureRate}) => formatPercentage(failureRate),
  },
];

const MODEL_COLUMNS: MetricColumn<AgentModelUsage>[] = [
  {
    titleKey: 'agenticControlPlane.agentView.models.model',
    render: ({model, provider}) => (
      <>
        {model}
        {provider && <span className="secondary">{provider}</span>}
      </>
    ),
  },
  {
    titleKey: 'agenticControlPlane.agentView.models.runs',
    numeric: true,
    render: ({runs}) => formatCount(runs),
  },
  {
    titleKey: 'agenticControlPlane.agentView.models.tokensPerRun',
    numeric: true,
    render: ({avgTokens}) => formatTokens(avgTokens),
  },
  {
    titleKey: 'agenticControlPlane.agentView.models.avgDuration',
    numeric: true,
    render: ({avgDurationMs}) => formatDuration(avgDurationMs),
  },
  {
    titleKey: 'agenticControlPlane.agentView.models.incidentRate',
    numeric: true,
    render: ({incidentRate}) => formatPercentage(incidentRate),
  },
];

function whereUsedColumns(usages: AgentUsage[]): MetricColumn<AgentUsage>[] {
  const totalRuns = usages.reduce((sum, {runs}) => sum + runs, 0);
  return [
    {
      titleKey: 'agenticControlPlane.agentView.whereUsed.startedFrom',
      render: ({rootProcessName, rootProcessDefinitionKey}) =>
        rootProcessName ?? rootProcessDefinitionKey,
    },
    {
      titleKey: 'agenticControlPlane.agentView.whereUsed.runs',
      numeric: true,
      render: ({runs}) => formatCount(runs),
    },
    {
      titleKey: 'agenticControlPlane.agentView.whereUsed.tokens',
      numeric: true,
      render: ({totalTokens}) => formatTokens(totalTokens),
    },
    {
      titleKey: 'agenticControlPlane.agentView.whereUsed.share',
      numeric: true,
      render: ({runs}) => formatPercentage(totalRuns > 0 ? (runs / totalRuns) * 100 : null),
    },
  ];
}
