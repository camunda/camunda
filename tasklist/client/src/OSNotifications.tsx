/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {useCallback, useEffect, useRef, useState} from 'react';
import {useTasks} from 'modules/queries/useTasks';
import {encodeTaskEmptyPageRef} from 'modules/utils/reftags';
import {NavigateFunction, useBeforeUnload, useNavigate} from 'react-router-dom';
import {tracking} from 'modules/tracking';
import {pages} from 'modules/routing';
import {TaskFilters} from 'modules/hooks/useTaskFilters';
import {useIsPageBlurred} from 'modules/hooks/useIsPageBlurred';
import {useIsOnline} from 'modules/hooks/useIsOnline';
import {useInterval} from 'modules/hooks/useInterval';

const FIFTEEN_MINUTES_IN_MS = 15 * 60 * 1000;
const FILTER = 'assigned-to-me';
const SORT_BY = 'creation';

type State = 'stopped' | 'focused' | 'blurred' | 'offline';

function taskListWithRefUrl(params: Pick<TaskFilters, 'filter' | 'sortBy'>) {
  return {
    pathname: pages.initial,
    search: new URLSearchParams({
      ...params,
      ref: encodeTaskEmptyPageRef({by: 'os-notification'}),
    }).toString(),
  };
}

function createNotification(numTasks: number, navigate: NavigateFunction) {
  const notification = new Notification(
    numTasks === 1
      ? '1 task is assigned to you.'
      : numTasks === 50
        ? '50+ tasks are assigned to you.'
        : `${numTasks} tasks are assigned to you.`,
    {
      icon: '/favicon.ico',
      body: 'Click here to see the details.',
      tag: `${FILTER}-notification`,
    },
  );
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

const OSNotifications: React.FC = () => {
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
  const notification = useRef<Notification | undefined>();
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
};

export {OSNotifications};
