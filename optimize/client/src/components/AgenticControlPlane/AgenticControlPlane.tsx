/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect, useCallback, useMemo} from 'react';
import {Tab, TabList, Tabs} from '@carbon/react';
import {sub} from 'date-fns';

import {useErrorHandling} from 'hooks';
import {showError} from 'notifications';
import {Loading} from 'components';
import {loadDefinitions, type Definition as ProcessDefinition} from 'services';
import {t} from 'translation';

import type {DashboardTile} from 'types';

import {AgentInventory} from './AgentInventory';
import {AgentView} from './AgentView';
import {DrillBreadcrumb} from './DrillBreadcrumb';
import {ALL_VERSIONS, DATE_PRESETS, FilterBar} from './FilterBar';
import {ProcessFailures} from './ProcessFailures';
import {loadAgentDetail, loadAgenticDashboard, type AgentDetail} from './service';
import {TileSections, type Definition} from './TileSections';

import './AgenticControlPlane.scss';

type AgenticTab = 'dashboard' | 'inventory';
const TABS: AgenticTab[] = ['dashboard', 'inventory'];

interface RollingFilter {
  type: 'instanceEndDate';
  filterLevel: 'instance';
  data: {
    type: 'rolling';
    start: {value: number; unit: string};
    end: null;
    excludeUndefined: boolean;
    includeUndefined: boolean;
  };
}

interface AgentRunFilter {
  type: 'agentRun';
  filterLevel: 'instance';
  data: {agentIds: string[]; models: string[]; providers: string[]};
}

function presetToGroupByDateUnit(presetId: string): string {
  if (presetId === '7d') {
    return 'day';
  }
  if (presetId === '30d') {
    return 'week';
  }
  return 'month';
}

function presetToFilter(preset: (typeof DATE_PRESETS)[number]): RollingFilter {
  return {
    type: 'instanceEndDate',
    filterLevel: 'instance',
    data: {
      type: 'rolling',
      start: {value: preset.value, unit: preset.unit},
      end: null,
      excludeUndefined: false,
      includeUndefined: false,
    },
  };
}

function buildFilter(
  preset: (typeof DATE_PRESETS)[number],
  agentIds: string[],
  models: string[]
): (RollingFilter | AgentRunFilter)[] {
  const filter: (RollingFilter | AgentRunFilter)[] = [presetToFilter(preset)];
  if (agentIds.length > 0 || models.length > 0) {
    filter.push({
      type: 'agentRun',
      filterLevel: 'instance',
      data: {agentIds, models, providers: []},
    });
  }
  return filter;
}

export function AgenticControlPlane() {
  const [tab, setTab] = useState<AgenticTab>('dashboard');
  const [preset, setPreset] = useState('30d');
  const [dashboard, setDashboard] = useState<{tiles: DashboardTile[]} | null>(null);
  const [processDefinitions, setProcessDefinitions] = useState<ProcessDefinition[]>([]);
  const [processScope, setProcessScope] = useState<string | null>(null);
  const [versions, setVersions] = useState<string[]>([ALL_VERSIONS]);
  const [agentIds, setAgentIds] = useState<string[]>([]);
  const [models, setModels] = useState<string[]>([]);
  const [agentDetail, setAgentDetail] = useState<AgentDetail | null>(null);
  const {mightFail} = useErrorHandling();

  const selectedPreset = DATE_PRESETS.find((p) => p.id === preset)!;
  // Same rolling window as the tiles' instance end date filter, fixed per preset so the tables are
  // not reloaded on every render.
  const endedFrom = useMemo(
    () => sub(new Date(), {[selectedPreset.unit]: selectedPreset.value}),
    [selectedPreset]
  );
  // Exactly one agent, from an inventory row or the agent filter, opens the single agent view.
  const selectedAgentId = agentIds.length === 1 ? agentIds[0]! : null;

  useEffect(() => {
    mightFail(loadAgenticDashboard(), setDashboard, showError);
  }, [mightFail]);

  useEffect(() => {
    mightFail(loadDefinitions('process', null), setProcessDefinitions, showError);
  }, [mightFail]);

  useEffect(() => {
    setAgentDetail(null);
    if (selectedAgentId) {
      mightFail(loadAgentDetail(selectedAgentId, endedFrom), setAgentDetail, showError);
    }
  }, [selectedAgentId, endedFrom, mightFail]);

  const handleProcessScopeChange = useCallback(
    (key: string | null) => {
      // The process combobox also reports a selection it already shows as a change; that must
      // not wipe the agent selected along with it.
      if (key === processScope) {
        return;
      }
      setProcessScope(key);
      setVersions([ALL_VERSIONS]);
      // Agents and models are listed per process, so a selection from another process no longer
      // applies.
      setAgentIds([]);
      setModels([]);
    },
    [processScope]
  );

  const openAgent = useCallback((agentId: string) => setAgentIds([agentId]), []);

  const handleTabChange = ({selectedIndex}: {selectedIndex: number}) => {
    setTab(TABS[selectedIndex] ?? 'dashboard');
    // Switching tabs leaves the single agent view, keeping the process scope.
    if (selectedAgentId) {
      setAgentIds([]);
    }
  };

  const goToRoot = () => {
    setProcessScope(null);
    setVersions([ALL_VERSIONS]);
    setAgentIds([]);
    setModels([]);
  };

  // The agent view can be opened without a process selected; its process crumb then scopes the
  // dashboard or inventory to the agent's process.
  const crumbProcessKey =
    processScope ?? (selectedAgentId ? agentDetail?.processDefinitionKey : null);
  const goToProcess = () => {
    if (!crumbProcessKey || crumbProcessKey === processScope) {
      setAgentIds([]);
    } else {
      handleProcessScopeChange(crumbProcessKey);
    }
  };

  if (!dashboard) {
    return <Loading />;
  }

  const processName = (key: string | null | undefined) =>
    key ? (processDefinitions.find((definition) => definition.key === key)?.name ?? key) : null;
  const crumbProcessName =
    processScope || !agentDetail
      ? processName(crumbProcessKey)
      : (agentDetail.processDefinitionName ?? processName(crumbProcessKey));

  const tiles = dashboard.tiles ?? [];
  const groupByDateUnit = presetToGroupByDateUnit(preset);
  const scopeKey = [
    processScope ?? '__all__',
    versions.join(','),
    agentIds.join(','),
    models.join(','),
  ].join('-');

  const renderAgentView = (agent: AgentDetail) => {
    const agentProcess = agent.processDefinitionKey;
    const definitions: Definition[] = agentProcess
      ? [{key: agentProcess, versions: agentProcess === processScope ? versions : [ALL_VERSIONS]}]
      : [];
    return (
      <AgentView agent={agent}>
        <TileSections
          tiles={tiles}
          level="agent"
          sections={['kpi', 'agentTrends']}
          definitions={definitions}
          filter={buildFilter(selectedPreset, [agent.agentId], models)}
          groupByDateUnit={groupByDateUnit}
          scopeKey={scopeKey}
        />
      </AgentView>
    );
  };

  const renderDashboard = () => (
    <>
      <h3 className="section-title">
        {processScope
          ? t('agenticControlPlane.overview.processAgents', {process: processName(processScope)!})
          : t('agenticControlPlane.overview.allAgents')}
        <small className="section-subtitle">
          {t('agenticControlPlane.overview.completedRuns')}
        </small>
      </h3>
      <hr className="section-divider" />
      <TileSections
        tiles={tiles}
        level={processScope ? 'process' : 'overview'}
        sections={['kpi', 'trends']}
        definitions={processScope ? [{key: processScope, versions}] : []}
        filter={buildFilter(selectedPreset, agentIds, models)}
        groupByDateUnit={groupByDateUnit}
        scopeKey={scopeKey}
      />
      {processScope && (
        <ProcessFailures
          processDefinitionKey={processScope}
          endedFrom={endedFrom}
          onSelectAgent={openAgent}
        />
      )}
    </>
  );

  let content;
  if (selectedAgentId) {
    content = agentDetail ? renderAgentView(agentDetail) : <Loading />;
  } else if (tab === 'inventory') {
    content = (
      <AgentInventory
        endedFrom={endedFrom}
        processDefinitionKey={processScope}
        processName={processName(processScope)}
        agentIds={agentIds}
        models={models}
        onSelectAgent={openAgent}
      />
    );
  } else {
    content = renderDashboard();
  }

  return (
    <div className="AgenticControlPlane">
      <div className="header">
        <h1 className="title">{t('agenticControlPlane.title')}</h1>
        <p className="description">{t('agenticControlPlane.description')}</p>
      </div>
      <Tabs selectedIndex={TABS.indexOf(tab)} onChange={handleTabChange}>
        <TabList aria-label={t('agenticControlPlane.tabs.label') as string}>
          <Tab>{t('agenticControlPlane.tabs.dashboard')}</Tab>
          <Tab>{t('agenticControlPlane.tabs.inventory')}</Tab>
        </TabList>
      </Tabs>
      <FilterBar
        preset={preset}
        onPresetChange={setPreset}
        processScope={processScope}
        onProcessScopeChange={handleProcessScopeChange}
        versions={versions}
        onVersionsChange={setVersions}
        agentIds={agentIds}
        onAgentIdsChange={setAgentIds}
        models={models}
        onModelsChange={setModels}
      />
      <DrillBreadcrumb
        rootLabelKey={
          tab === 'inventory'
            ? 'agenticControlPlane.breadcrumb.agentInventory'
            : 'agenticControlPlane.breadcrumb.allProcesses'
        }
        processName={crumbProcessName}
        agentName={selectedAgentId ? (agentDetail?.name ?? selectedAgentId) : null}
        onRoot={goToRoot}
        onProcess={goToProcess}
      />
      {content}
    </div>
  );
}
