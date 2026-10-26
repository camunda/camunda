/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow, Tag} from '@carbon/react';
import {format, parseISO} from 'date-fns';

import {Loading} from 'components';
import {formatters} from 'services';
import {t} from 'translation';

import {loadAgentInventory, type AgentInventoryEntry} from './service';

import './AgentInventory.scss';

type SortKey =
  | 'name'
  | 'process'
  | 'runs'
  | 'incidentRate'
  | 'durationP50Ms'
  | 'limitHitRate'
  | 'avgTokens'
  | 'lastRunDate';

interface Column {
  key: SortKey;
  titleKey: string;
  numeric?: boolean;
}

const COLUMNS: Column[] = [
  {key: 'name', titleKey: 'agenticControlPlane.inventory.agent'},
  {key: 'process', titleKey: 'agenticControlPlane.inventory.process'},
  {key: 'runs', titleKey: 'agenticControlPlane.inventory.runs', numeric: true},
  {key: 'incidentRate', titleKey: 'agenticControlPlane.inventory.incidentRate', numeric: true},
  {key: 'durationP50Ms', titleKey: 'agenticControlPlane.inventory.durationP50', numeric: true},
  {key: 'limitHitRate', titleKey: 'agenticControlPlane.inventory.limitHitRate', numeric: true},
  {key: 'avgTokens', titleKey: 'agenticControlPlane.inventory.avgTokens', numeric: true},
  {key: 'lastRunDate', titleKey: 'agenticControlPlane.inventory.lastRun', numeric: true},
];

function sortValue(entry: AgentInventoryEntry, key: SortKey): string | number | null {
  if (key === 'process') {
    return entry.processDefinitionName ?? entry.processDefinitionKey;
  }
  if (key === 'name') {
    return entry.name;
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

interface AgentInventoryProps {
  endedFrom: Date;
  onSelectAgent: (processDefinitionKey: string, agentId: string) => void;
}

export function AgentInventory({endedFrom, onSelectAgent}: AgentInventoryProps) {
  const [entries, setEntries] = useState<AgentInventoryEntry[] | null>(null);
  const [sortKey, setSortKey] = useState<SortKey>('runs');
  const [direction, setDirection] = useState<'ASC' | 'DESC'>('DESC');
  const endedFromIso = endedFrom.toISOString();

  useEffect(() => {
    setEntries(null);
    loadAgentInventory(new Date(endedFromIso)).then(setEntries);
  }, [endedFromIso]);

  const toggleSort = (key: SortKey, numeric?: boolean) => {
    if (key === sortKey) {
      setDirection(direction === 'ASC' ? 'DESC' : 'ASC');
    } else {
      setSortKey(key);
      // Metrics are most useful largest first; names alphabetically.
      setDirection(numeric ? 'DESC' : 'ASC');
    }
  };

  if (!entries) {
    return <Loading />;
  }

  if (entries.length === 0) {
    return <p className="AgentInventory empty">{t('agenticControlPlane.inventory.empty')}</p>;
  }

  return (
    <div className="AgentInventory">
      <Table size="md" useZebraStyles={false}>
        <TableHead>
          <TableRow>
            {COLUMNS.map(({key, titleKey, numeric}) => (
              <TableHeader
                key={key}
                isSortable
                isSortHeader={key === sortKey}
                sortDirection={key === sortKey ? direction : 'NONE'}
                onClick={() => toggleSort(key, numeric)}
                className={numeric ? 'numeric' : undefined}
              >
                {t(titleKey)}
              </TableHeader>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {sortInventory(entries, sortKey, direction).map((entry) => {
            const canDrill = entry.processDefinitionKey !== null;
            const drill = () =>
              canDrill && onSelectAgent(entry.processDefinitionKey!, entry.agentId);
            return (
              <TableRow
                key={entry.agentId}
                className={canDrill ? 'drillable' : undefined}
                onClick={drill}
              >
                <TableCell>
                  <button
                    type="button"
                    className="agentName"
                    disabled={!canDrill}
                    onClick={(event) => {
                      event.stopPropagation();
                      drill();
                    }}
                  >
                    {entry.name}
                  </button>
                  <div className="agentDetails">
                    {entry.models.map((model) => (
                      <Tag key={model} size="sm" type="cool-gray">
                        {model}
                      </Tag>
                    ))}
                    {entry.maxModelCalls != null && entry.maxModelCalls > 0 && (
                      <span className="limit">
                        {t('agenticControlPlane.inventory.maxModelCalls', {
                          count: entry.maxModelCalls,
                        })}
                      </span>
                    )}
                  </div>
                </TableCell>
                <TableCell>{entry.processDefinitionName ?? entry.processDefinitionKey}</TableCell>
                <TableCell className="numeric">{formatters.frequency(entry.runs)}</TableCell>
                <TableCell className="numeric">{formatPercentage(entry.incidentRate)}</TableCell>
                <TableCell className="numeric">
                  {entry.durationP50Ms == null ? '–' : formatters.duration(entry.durationP50Ms, 2)}
                </TableCell>
                <TableCell className="numeric">{formatPercentage(entry.limitHitRate)}</TableCell>
                <TableCell className="numeric">
                  {entry.avgTokens == null ? '–' : formatters.compact(entry.avgTokens)}
                </TableCell>
                <TableCell className="numeric">
                  {entry.lastRunDate ? format(parseISO(entry.lastRunDate), 'PP') : '–'}
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}

function formatPercentage(value: number | null) {
  return value == null ? '–' : formatters.percentage(value);
}
