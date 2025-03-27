/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useRef, useState} from 'react';
import {useTasks as useV1Tasks} from 'v1/api/useTasks.query';
import {useTasks as useV2Tasks} from 'v2/api/useTasks.query';
import {encodeTaskEmptyPageRef} from 'common/tracking/reftags';
import {
  type NavigateFunction,
  useBeforeUnload,
  useNavigate,
} from 'react-router-dom';
import {tracking} from 'common/tracking';
import {pages} from 'common/routing';
import type {TaskFilters as V1TaskFilters} from 'v1/features/tasks/filters/useTaskFilters';
import type {TaskFilters as V2TaskFilters} from 'v2/features/tasks/filters/useTaskFilters';
import {useIsPageBlurred} from 'common/os-notifications/useIsPageBlurred';
import {useIsOnline} from 'common/os-notifications/useIsOnline';
import {useInterval} from 'common/os-notifications/useInterval';
import {t} from 'i18next';
import {getClientConfig} from 'common/config/getClientConfig';

const FIFTEEN_MINUTES_IN_MS = 15 * 60 * 1000;
const FILTER = 'assigned-to-me';
const SORT_BY = 'creation';

type State = 'stopped' | 'focused' | 'blurred' | 'offline';

function taskListWithRefUrl(
  params: Pick<V1TaskFilters | V2TaskFilters, 'filter' | 'sortBy'>,
) {
  return {
    pathname: pages.initial,
    search: new URLSearchParams({
      ...params,
      ref: encodeTaskEmptyPageRef({by: 'os-notification'}),
    }).toString(),
  };
}

function createNotification(numTasks: number, navigate: NavigateFunction) {
  const notificationMessage =
    numTasks === 1
      ? t('nativeNotificationOneNewTaskAssigned')
      : numTasks >= 50
        ? t('nativeNotificationFiftyOrMoreTasksAssigned')
        : t('nativeNotificationMultipleTasksAssigned', {count: numTasks});

  const notification = new Notification(notificationMessage, {
    icon: '/favicon.ico',
    body: t('nativeNotificationNewTasksMessageBody'),
    tag: `${FILTER}-notification`,
  });
  notification.onclick = () => {
    window.focus();
    navigate(taskListWithRefUrl({filter: FILTER, sortBy: SORT_BY}));
  };
  return notification;
}

function useStateMachine(enable: boolean) {
  const blurred = useIsPageBlurred();
  const online = useIsOnline();
  const [state, setState] = useState<State>('stopped');

  useEffect(() => {
    if (Notification.permission !== 'granted') {
      tracking.track({
        eventName: 'os-notification-opted-out',
      });
      setState('stopped');
      return;
    }

    switch (state) {
      case 'stopped':
        if (!enable) {
          setState('stopped');
        } else if (!online) {
          setState('offline');
        } else if (!blurred) {
          setState('focused');
        } else {
          setState('blurred');
        }
        break;
      case 'focused':
        if (!enable) {
          setState('stopped');
        } else if (!online) {
          setState('offline');
        } else if (blurred) {
          setState('blurred');
        }
        break;
      case 'blurred':
        if (!enable) {
          setState('stopped');
        } else if (!blurred) {
          setState('focused');
        } else if (!online) {
          setState('offline');
        }
        break;
      case 'offline':
        if (!enable) {
          setState('stopped');
        } else if (online) {
          if (blurred) {
            setState('blurred');
          } else {
            setState('focused');
          }
        }
        break;
    }
  }, [blurred, online, enable, state]);

  return state;
}

const useTasks =
  getClientConfig().clientMode === 'v2' ? useV2Tasks : useV1Tasks;

const OSNotifications: React.FC =
  'Notification' in window
    ? () => {
        const state = useStateMachine(Notification.permission === 'granted');
        const tasks = useTasks(
          {
            filter: FILTER,
            state: 'CREATED',
            sortBy: SORT_BY,
            sortOrder: 'asc',
          },
          {
            refetchInterval: false,
          },
        );
        const navigate = useNavigate();
        const notification = useRef<Notification | undefined>(undefined);
        useBeforeUnload(
          useCallback(() => {
            if (notification.current) {
              notification.current.close();
              notification.current = undefined;
            }
          }, []),
        );

        const tick = useCallback(async () => {
          const result = await tasks.refetch();
          const data = result.data ?? {pages: []};
          const numTasks = data.pages.flat().length;
          if (numTasks > 0) {
            notification.current = createNotification(numTasks, navigate);
          }
        }, [navigate, tasks]);

        const [start, stop] = useInterval(tick, FIFTEEN_MINUTES_IN_MS);

        useEffect(() => {
          switch (state) {
            case 'stopped':
              if (notification.current) {
                notification.current.close();
                notification.current = undefined;
              }
              stop();
              break;
            case 'focused':
              if (notification.current) {
                notification.current.close();
                notification.current = undefined;
              }
              stop();
              break;
            case 'offline':
              stop();
              break;
            case 'blurred':
              start();
              break;
          }
        }, [state, start, stop]);

        return null;
      }
    : () => null;

export {OSNotifications};
