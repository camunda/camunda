/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState, type KeyboardEvent, type ReactNode} from 'react';
import {
  Search,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Tag,
} from '@carbon/react';
import {format, parseISO} from 'date-fns';

import {Loading} from 'components';
import {useErrorHandling} from 'hooks';
import {showError} from 'notifications';
import {t} from 'translation';

import {
  formatCount,
  formatDecimal,
  formatDuration,
  formatPercentage,
  formatTokens,
} from './MetricTable';
import {
  loadAgentInventory,
  type AgentInventory as Inventory,
  type AgentInventoryEntry,
} from './service';

import './AgentInventory.scss';

type SortKey =
  | 'name'
  | 'process'
  | 'runs'
  | 'totalTokens'
  | 'avgTokens'
  | 'successRate'
  | 'durationP50Ms'
  | 'durationP95Ms'
  | 'avgHumanSteps'
  | 'lastRunDate';

interface Column {
  titleKey: string;
  sortKey?: SortKey;
  numeric?: boolean;
  render: (entry: AgentInventoryEntry) => ReactNode;
}

const COLUMNS: Column[] = [
  {
    titleKey: 'agenticControlPlane.inventory.agent',
    sortKey: 'name',
    render: (entry) => <span className="agentName">{entry.name}</span>,
  },
  {
    titleKey: 'agenticControlPlane.inventory.process',
    sortKey: 'process',
    render: (entry) => entry.processDefinitionName ?? entry.processDefinitionKey ?? '–',
  },
  {
    titleKey: 'agenticControlPlane.inventory.source',
    render: ({agentType}) =>
      agentType === 'EXTERNAL_AGENT' ? (
        <Tag size="sm" type="purple">
          {t('agenticControlPlane.inventory.sourceExternal')}
        </Tag>
      ) : (
        <Tag size="sm" type="cool-gray">
          {t('agenticControlPlane.inventory.sourceCamunda')}
        </Tag>
      ),
  },
  {
    titleKey: 'agenticControlPlane.inventory.model',
    render: ({models}) => (models.length > 0 ? models.join(', ') : '–'),
  },
  {
    titleKey: 'agenticControlPlane.inventory.runs',
    sortKey: 'runs',
    numeric: true,
    render: ({runs}) => formatCount(runs),
  },
  {
    titleKey: 'agenticControlPlane.inventory.totalTokens',
    sortKey: 'totalTokens',
    numeric: true,
    render: ({totalTokens}) => formatTokens(totalTokens),
  },
  {
    titleKey: 'agenticControlPlane.inventory.tokensPerRun',
    sortKey: 'avgTokens',
    numeric: true,
    render: ({avgTokens}) => formatTokens(avgTokens),
  },
  {
    titleKey: 'agenticControlPlane.inventory.successRate',
    sortKey: 'successRate',
    numeric: true,
    render: ({successRate}) => formatPercentage(successRate),
  },
  {
    titleKey: 'agenticControlPlane.inventory.durationP50',
    sortKey: 'durationP50Ms',
    numeric: true,
    render: ({durationP50Ms}) => formatDuration(durationP50Ms),
  },
  {
    titleKey: 'agenticControlPlane.inventory.durationP95',
    sortKey: 'durationP95Ms',
    numeric: true,
    render: ({durationP95Ms}) => formatDuration(durationP95Ms),
  },
  {
    titleKey: 'agenticControlPlane.inventory.humanSteps',
    sortKey: 'avgHumanSteps',
    numeric: true,
    render: ({avgHumanSteps}) => formatDecimal(avgHumanSteps),
  },
  {
    titleKey: 'agenticControlPlane.inventory.lastRun',
    sortKey: 'lastRunDate',
    numeric: true,
    render: ({lastRunDate}) => (lastRunDate ? format(parseISO(lastRunDate), 'PP') : '–'),
  },
];

function sortValue(entry: AgentInventoryEntry, key: SortKey): string | number | null {
  if (key === 'process') {
    return entry.processDefinitionName ?? entry.processDefinitionKey;
  }
  return entry[key];
}

// Empty values sort last in both directions so agents without runs never crowd the top.
export function sortInventory(
  entries: AgentInventoryEntry[],
  key: SortKey,
  direction: 'ASC' | 'DESC'
): AgentInventoryEntry[] {
  return [...entries].sort((a, b) => {
    const left = sortValue(a, key);
    const right = sortValue(b, key);
    if (left == null || right == null) {
      return left == null && right == null ? 0 : left == null ? 1 : -1;
    }
    const order =
      typeof left === 'number' && typeof right === 'number'
        ? left - right
        : String(left).localeCompare(String(right));
    return direction === 'ASC' ? order : -order;
  });
}

export function matchesQuery(entry: AgentInventoryEntry, query: string): boolean {
  const needle = query.trim().toLowerCase();
  if (!needle) {
    return true;
  }
  return [entry.name, entry.processDefinitionName, entry.processDefinitionKey, ...entry.models]
    .filter(Boolean)
    .some((value) => value!.toLowerCase().includes(needle));
}

interface AgentInventoryProps {
  endedFrom: Date;
  processDefinitionKey: string | null;
  processName: string | null;
  // The inventory endpoint has no agent or model parameter, so the shared filters apply here.
  agentIds: string[];
  models: string[];
  onSelectAgent: (agentId: string) => void;
}

export function AgentInventory({
  endedFrom,
  processDefinitionKey,
  processName,
  agentIds,
  models,
  onSelectAgent,
}: AgentInventoryProps) {
  const [inventory, setInventory] = useState<Inventory | null>(null);
  const [sortKey, setSortKey] = useState<SortKey>('runs');
  const [direction, setDirection] = useState<'ASC' | 'DESC'>('DESC');
  const [query, setQuery] = useState('');
  const {mightFail} = useErrorHandling();
  const endedFromIso = endedFrom.toISOString();

  useEffect(() => {
    setInventory(null);
    mightFail(
      loadAgentInventory(new Date(endedFromIso), processDefinitionKey),
      setInventory,
      showError
    );
  }, [endedFromIso, processDefinitionKey, mightFail]);

  const toggleSort = (key: SortKey, numeric?: boolean) => {
    if (key === sortKey) {
      setDirection(direction === 'ASC' ? 'DESC' : 'ASC');
    } else {
      setSortKey(key);
      // Metrics are most useful largest first; names alphabetically.
      setDirection(numeric ? 'DESC' : 'ASC');
    }
  };

  const title = processName
    ? t('agenticControlPlane.inventory.agentsInProcess', {process: processName})
    : t('agenticControlPlane.inventory.title');

  if (!inventory) {
    return <Loading />;
  }

  const {summary} = inventory;
  const rows = sortInventory(
    inventory.agents.filter(
      (entry) =>
        (agentIds.length === 0 || agentIds.includes(entry.agentId)) &&
        (models.length === 0 || entry.models.some((model) => models.includes(model))) &&
        matchesQuery(entry, query)
    ),
    sortKey,
    direction
  );

  return (
    <div className="AgentInventory">
      <h3 className="section-title">
        {title}
        <small className="summary">
          {t('agenticControlPlane.inventory.summary', {
            agents: summary.agents,
            processes: summary.processes,
            external: summary.externalAgents,
          })}
        </small>
      </h3>
      <hr className="section-divider" />
      <div className="toolbar">
        <Search
          size="md"
          labelText={t('agenticControlPlane.inventory.search')}
          placeholder={t('agenticControlPlane.inventory.searchPlaceholder') as string}
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
      </div>
      {inventory.agents.length === 0 ? (
        <p className="empty">{t('agenticControlPlane.inventory.empty')}</p>
      ) : (
        <>
          <div className="tableWrapper">
            <Table size="md" useZebraStyles={false}>
              <TableHead>
                <TableRow>
                  {COLUMNS.map(({titleKey, sortKey: key, numeric}) =>
                    key ? (
                      <TableHeader
                        key={titleKey}
                        isSortable
                        isSortHeader={key === sortKey}
                        sortDirection={key === sortKey ? direction : 'NONE'}
                        onClick={() => toggleSort(key, numeric)}
                        className={numeric ? 'numeric' : undefined}
                      >
                        {t(titleKey)}
                      </TableHeader>
                    ) : (
                      <TableHeader key={titleKey}>{t(titleKey)}</TableHeader>
                    )
                  )}
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={COLUMNS.length} className="empty">
                      {t('agenticControlPlane.inventory.noMatch', {query})}
                    </TableCell>
                  </TableRow>
                )}
                {rows.map((entry) => (
                  <TableRow
                    key={entry.agentId}
                    className="drillable"
                    tabIndex={0}
                    onClick={() => onSelectAgent(entry.agentId)}
                    onKeyDown={(event: KeyboardEvent) => {
                      if (event.key === 'Enter') {
                        onSelectAgent(entry.agentId);
                      }
                    }}
                  >
                    {COLUMNS.map(({titleKey, numeric, render}) => (
                      <TableCell key={titleKey} className={numeric ? 'numeric' : undefined}>
                        {render(entry)}
                      </TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <p className="hint">{t('agenticControlPlane.inventory.selectHint')}</p>
        </>
      )}
    </div>
  );
}
