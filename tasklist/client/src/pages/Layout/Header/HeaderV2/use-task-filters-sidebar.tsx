/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {IconButton} from '@carbon/react';
import {
  Add,
  Edit,
  TaskAdd,
  TaskComplete,
  TaskRemove,
  TaskView,
  TrashCan,
  UserFollow,
} from '@carbon/react/icons';
import type {SidebarNodeDescriptor} from '@camunda/camunda-composite-components';
import {pages} from 'modules/routing';
import {tracking} from 'modules/tracking';
import type {CustomFiltersDialog} from './use-custom-filters-dialog';
import styles from '../styles.module.scss';

const stopRowNavigation = (e: React.SyntheticEvent) => {
  e.stopPropagation();
  e.preventDefault();
};

export function useTaskFiltersSidebar(
  dialog: CustomFiltersDialog,
): SidebarNodeDescriptor[] {
  const {t} = useTranslation();

  return [
    {
      type: 'item' as const,
      key: 'tasks:all-open',
      label: t('taskFiltersAllOpenTasks'),
      icon: TaskView,
      linkProps: {to: `${pages.initial}?filter=all-open`},
      onClick: () => {
        tracking.track({
          eventName: 'navigation',
          link: 'header-tasks-all-open',
        });
      },
    },
    {
      type: 'item' as const,
      key: 'tasks:assigned-to-me',
      label: t('taskFiltersAssignedToMe'),
      icon: UserFollow,
      linkProps: {to: `${pages.initial}?filter=assigned-to-me`},
      onClick: () => {
        tracking.track({
          eventName: 'navigation',
          link: 'header-tasks-assigned-to-me',
        });
      },
    },
    {
      type: 'item' as const,
      key: 'tasks:unassigned',
      label: t('taskFiltersUnassigned'),
      icon: TaskRemove,
      linkProps: {to: `${pages.initial}?filter=unassigned`},
      onClick: () => {
        tracking.track({
          eventName: 'navigation',
          link: 'header-tasks-unassigned',
        });
      },
    },
    {
      type: 'item' as const,
      key: 'tasks:completed',
      label: t('taskFiltersCompleted'),
      icon: TaskComplete,
      linkProps: {to: `${pages.initial}?filter=completed`},
      onClick: () => {
        tracking.track({
          eventName: 'navigation',
          link: 'header-tasks-completed',
        });
      },
    },
    ...dialog.customFilterEntries.map(([key, value]) => ({
      type: 'item' as const,
      key: `tasks:${key}`,
      label: value?.name ?? key,
      icon: TaskAdd,
      linkProps: {to: `${pages.initial}?filter=${key}`},
      onClick: () => {
        tracking.track({
          eventName: 'navigation',
          link: `header-tasks-custom-${key}`,
        });
      },
      trailingElement: (
        <span className={styles.hoverActions} onClick={stopRowNavigation}>
          <IconButton
            kind="ghost"
            size="sm"
            label={t('taskFilterPanelEdit')}
            align="bottom-left"
            autoAlign
            onClick={() => {
              dialog.openEditFilterModal(key);
            }}
          >
            <Edit />
          </IconButton>
          <IconButton
            kind="ghost"
            size="sm"
            label={t('taskFilterPanelDelete')}
            align="bottom-left"
            autoAlign
            onClick={() => {
              dialog.openDeleteFilterModal(key);
            }}
          >
            <TrashCan />
          </IconButton>
        </span>
      ),
    })),
    {
      type: 'item' as const,
      key: 'tasks:new-filter',
      label: t('taskFilterPanelNewFilter'),
      icon: Add,
      onClick: dialog.openNewFilterModal,
    },
  ];
}
