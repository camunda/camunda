/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';

import {Loading} from 'components';
import {useErrorHandling} from 'hooks';
import {showError} from 'notifications';
import {t} from 'translation';

import {FailureReasonLabel} from './FailureReasonLabel';
import {formatCount, formatPercentage, MetricTable, type MetricColumn} from './MetricTable';
import {loadProcessFailures, type AgentFailures} from './service';

interface ProcessFailuresProps {
  processDefinitionKey: string;
  endedFrom: Date;
  onSelectAgent: (agentId: string) => void;
}

export function ProcessFailures({
  processDefinitionKey,
  endedFrom,
  onSelectAgent,
}: ProcessFailuresProps) {
  const [agents, setAgents] = useState<AgentFailures[] | null>(null);
  const {mightFail} = useErrorHandling();
  const endedFromIso = endedFrom.toISOString();

  useEffect(() => {
    setAgents(null);
    mightFail(
      loadProcessFailures(processDefinitionKey, new Date(endedFromIso)),
      setAgents,
      showError
    );
  }, [processDefinitionKey, endedFromIso, mightFail]);

  const columns: MetricColumn<AgentFailures>[] = [
    {
      titleKey: 'agenticControlPlane.failures.agent',
      render: ({agentId, name}) => (
        <button type="button" className="linkButton" onClick={() => onSelectAgent(agentId)}>
          {name}
        </button>
      ),
    },
    {
      titleKey: 'agenticControlPlane.failures.runs',
      numeric: true,
      render: ({runs}) => formatCount(runs),
    },
    {
      titleKey: 'agenticControlPlane.failures.failedRuns',
      numeric: true,
      render: ({failedRuns}) => formatCount(failedRuns),
    },
    {
      titleKey: 'agenticControlPlane.failures.successRate',
      numeric: true,
      render: ({successRate}) => formatPercentage(successRate),
    },
    {
      titleKey: 'agenticControlPlane.failures.topReasons',
      render: ({topReasons}) => (
        <ul className="reasons">
          {topReasons.map((reason, index) => (
            <li key={index}>
              <FailureReasonLabel reason={reason} />
            </li>
          ))}
        </ul>
      ),
    },
  ];

  return (
    <div className="ProcessFailures">
      <h3 className="section-title">{t('agenticControlPlane.failures.title')}</h3>
      <p className="section-description">{t('agenticControlPlane.failures.description')}</p>
      <hr className="section-divider" />
      {agents ? (
        <MetricTable
          columns={columns}
          rows={agents}
          rowKey={({agentId}) => agentId}
          emptyKey="agenticControlPlane.failures.empty"
        />
      ) : (
        <Loading />
      )}
    </div>
  );
}
