/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {memo, useMemo} from 'react';
import {OverflowMenu, OverflowMenuItem} from '@carbon/react';
import {SortAscending, Checkmark} from '@carbon/react/icons';
import {tracking} from 'modules/tracking';
import {useTaskFilters, TaskFilters} from 'modules/hooks/useTaskFilters';
import {useTranslation} from 'react-i18next';
import styles from './styles.module.scss';
import sharedStyles from 'modules/styles/panelHeader.module.scss';
import {useSearchParams} from 'react-router-dom';
import {getStateLocally} from 'modules/utils/localStorage';

// const FILTER_LABELS: Record<string, string> = {
//   'all-open': t('allOpenTasks'),
//   'assigned-to-me': t('assignedToMe'),
//   unassigned: t('unassigned'),
//   completed: t('completed'),
//   custom: t('customFilter'),
// };

type Props = {
  disabled: boolean;
};

// const SORTING_OPTIONS: Record<TaskFilters['sortBy'], string> = {
//   creation: t('creationDate'),
//   'follow-up': t('followUpDate'),
//   due: t('dueDate'),
//   completion: t('completionDate'),
// };
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
  
  const {t} = useTranslation();

  const filterLabels: Record<string, string> = useMemo(() => {
    return {
      'all-open': t('allOpenTasks'),
      'assigned-to-me': t('assignedToMe'),
      unassigned: t('unassigned'),
      completed: t('completed'),
      custom: t('customFilter'),
    };
  }, [t]);

  const sortingOptions: Record<TaskFilters['sortBy'], string> = useMemo(() => {
    return {
      creation: t('creationDate'),
      'follow-up': t('followUpDate'),
      due: t('dueDate'),
      completion: t('completionDate'),
    };
  }, [t]);

  return (
    <section className={sharedStyles.panelHeader} aria-label={t('filtersAriaLabel')}>
      <h1 className={styles.header}>
        {filterLabels?.[filter] ?? customFilters?.[filter]?.name}
      </h1>
      <OverflowMenu
        aria-label={t('sortTasksAriaLabel')}
        iconDescription={t('sortTasksIconDescription')}
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
                <span className={styles.menuItem}>{sortingOptions[id]}</span>
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
