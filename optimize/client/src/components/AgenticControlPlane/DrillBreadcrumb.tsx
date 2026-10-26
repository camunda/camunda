/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {Breadcrumb, BreadcrumbItem} from '@carbon/react';

import {loadDefinitions} from 'services';
import type {Definition} from 'services';
import {t} from 'translation';

import {loadAgenticFilterOptions, type AgenticFilterOptions} from './service';

interface DrillBreadcrumbProps {
  processScope: string | null;
  agentIds: string[];
  onOverview: () => void;
  onProcess: () => void;
}

export function DrillBreadcrumb({
  processScope,
  agentIds,
  onOverview,
  onProcess,
}: DrillBreadcrumbProps) {
  const [processDefinitions, setProcessDefinitions] = useState<Definition[]>([]);
  const [agents, setAgents] = useState<AgenticFilterOptions['agents']>([]);

  useEffect(() => {
    loadDefinitions('process', null).then(setProcessDefinitions);
  }, []);

  useEffect(() => {
    if (processScope) {
      loadAgenticFilterOptions(processScope).then(({agents}) => setAgents(agents));
    } else {
      setAgents([]);
    }
  }, [processScope]);

  if (!processScope) {
    return null;
  }

  const processName =
    processDefinitions.find(({key}) => key === processScope)?.name || processScope;
  const agentNames = agentIds.map(
    (agentId) => agents.find((agent) => agent.agentId === agentId)?.name ?? agentId
  );

  return (
    <Breadcrumb className="DrillBreadcrumb" noTrailingSlash>
      <BreadcrumbItem>
        <button type="button" className="crumb" onClick={onOverview}>
          {t('agenticControlPlane.breadcrumb.allProcesses')}
        </button>
      </BreadcrumbItem>
      <BreadcrumbItem isCurrentPage={agentIds.length === 0}>
        {agentIds.length === 0 ? (
          processName
        ) : (
          <button type="button" className="crumb" onClick={onProcess}>
            {processName}
          </button>
        )}
      </BreadcrumbItem>
      {agentIds.length > 0 && (
        <BreadcrumbItem isCurrentPage>{agentNames.join(', ')}</BreadcrumbItem>
      )}
    </Breadcrumb>
  );
}
