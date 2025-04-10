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
import {tracking} from 'common/tracking';
import {
  useMultiModeTaskFilters,
  type MultiModeTaskFilters,
} from 'common/tasks/filters/useMultiModeTaskFilters';
import {t as _t} from 'i18next';
import {useTranslation} from 'react-i18next';
import styles from './styles.module.scss';
import sharedStyles from 'common/tasks/details/panelHeader.module.scss';
import {useSearchParams} from 'react-router-dom';
import {getStateLocally} from 'common/local-storage';

type Props = {
  disabled: boolean;
};

const SORTING_OPTIONS_ORDER: MultiModeTaskFilters['sortBy'][] = [
  'creation',
  'due',
  'follow-up',
  'priority',
];

const COMPLETED_SORTING_OPTIONS_ORDER: MultiModeTaskFilters['sortBy'][] = [
  'creation',
  'due',
  'follow-up',
  'priority',
  'completion',
];

const getFilterLabels = () =>
  ({
    'all-open': _t('taskFiltersAllOpenTasks'),
    'assigned-to-me': _t('taskFiltersAssignedToMe'),
    unassigned: _t('taskFiltersUnassigned'),
    completed: _t('taskFiltersCompleted'),
    custom: _t('taskFiltersCustomFilter'),
  }) as Record<string, string>;

const getSortingOptions = () =>
  ({
    creation: _t('taskFiltersSortCreationDate'),
    'follow-up': _t('taskFiltersSortFollowUpDate'),
    due: _t('taskFiltersSortDueDate'),
    completion: _t('taskFiltersSortCompletionDate'),
    priority: _t('taskFiltersSortPriority'),
  }) as Record<string, string>;

const Filters: React.FC<Props> = memo(({disabled}) => {
  const customFilters = getStateLocally('customFilters');
  const [searchParams, setSearchParams] = useSearchParams();
  const {filter, sortBy} = useMultiModeTaskFilters();
  const sortOptionsOrder = ['completed', 'custom'].includes(filter)
    ? COMPLETED_SORTING_OPTIONS_ORDER
    : SORTING_OPTIONS_ORDER;

  const {t} = useTranslation();

  return (
    <section
      className={sharedStyles.panelHeader}
      aria-label={t('taskFiltersHeaderAria')}
    >
      <h1 className={styles.header}>
        {getFilterLabels()?.[filter] ?? customFilters?.[filter]?.name}
      </h1>
      <OverflowMenu
        aria-label={t('taskFiltersSortButton')}
        iconDescription={t('taskFiltersSortButton')}
        renderIcon={SortAscending}
        size="md"
        disabled={disabled}
        align="bottom"
        menuOptionsClass={styles.overflowMenu}
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
                {getSortingOptions()[id]}
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
