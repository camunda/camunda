/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {memo} from 'react';
import {OverflowMenu, OverflowMenuItem} from '@carbon/react';
import {SortAscending, Checkmark} from '@carbon/react/icons';
import {tracking} from 'modules/tracking';
import {useTaskFilters, TaskFilters} from 'modules/hooks/useTaskFilters';
import styles from './styles.module.scss';
import sharedStyles from 'modules/styles/panelHeader.module.scss';
import {useSearchParams} from 'react-router-dom';
import {getStateLocally} from 'modules/utils/localStorage';

const FILTER_LABELS: Record<string, string> = {
  'all-open': 'All open tasks',
  'assigned-to-me': 'Assigned to me',
  unassigned: 'Unassigned',
  completed: 'Completed',
  custom: 'Custom filter',
};

type Props = {
  disabled: boolean;
};

const SORTING_OPTIONS: Record<TaskFilters['sortBy'], string> = {
  creation: 'Creation date',
  'follow-up': 'Follow-up date',
  due: 'Due date',
  completion: 'Completion date',
};
const SORTING_OPTIONS_ORDER: TaskFilters['sortBy'][] = [
  'creation',
  'due',
  'follow-up',
];
const COMPLETED_SORTING_OPTIONS_ORDER: TaskFilters['sortBy'][] = [
  'creation',
  'due',
  'follow-up',
  'completion',
];

const Filters: React.FC<Props> = memo(({disabled}) => {
  const customFilters = getStateLocally('customFilters');
  const [searchParams, setSearchParams] = useSearchParams();
  const {filter, sortBy} = useTaskFilters();
  const sortOptionsOrder = ['completed', 'custom'].includes(filter)
    ? COMPLETED_SORTING_OPTIONS_ORDER
    : SORTING_OPTIONS_ORDER;

  return (
    <section className={sharedStyles.panelHeader} aria-label="Filters">
      <h1 className={styles.header}>
        {FILTER_LABELS?.[filter] ?? customFilters?.[filter]?.name}
      </h1>
      <OverflowMenu
        aria-label="Sort tasks"
        iconDescription="Sort tasks"
        renderIcon={SortAscending}
        size="md"
        disabled={disabled}
        align="bottom"
      >
        {sortOptionsOrder.map((id) => (
          <OverflowMenuItem
            aria-selected={sortBy === id}
            key={id}
            itemText={
              <div className={styles.sortItem}>
                <Checkmark
                  aria-label=""
                  size={20}
                  style={{
                    visibility: sortBy === id ? undefined : 'hidden',
                  }}
                />
                <span className={styles.menuItem}>{SORTING_OPTIONS[id]}</span>
              </div>
            }
            onClick={() => {
              const customFilters = getStateLocally('customFilters')?.[filter];

              searchParams.set('sortBy', id);
              setSearchParams(searchParams);

              tracking.track({
                eventName: 'tasks-filtered',
                filter: filter,
                sorting: sortBy,
                customFilters: Object.keys(customFilters ?? {}),
                customFilterVariableCount:
                  customFilters?.variables?.length ?? 0,
              });
            }}
          />
        ))}
      </OverflowMenu>
    </section>
  );
});

export {Filters};
