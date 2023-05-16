/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react-lite';
import {pages} from 'modules/routing';
import {newProcessInstance} from 'modules/stores/newProcessInstance';
import {Task} from 'modules/types';
import {useLocation, useNavigate} from 'react-router-dom';
import {tracking} from 'modules/tracking';
import {useQuery} from '@tanstack/react-query';
import {request, RequestError} from 'modules/request';
import {api} from 'modules/api';
import {useEffect} from 'react';

type NewTasksResponse = Task[];

const NewProcessInstanceTasksPolling: React.FC = observer(() => {
  const {instance} = newProcessInstance;
  const navigate = useNavigate();
  const location = useLocation();

  useQuery<NewTasksResponse, RequestError | Error>({
    queryKey: ['newTasks'],
    enabled: instance !== null,
    refetchInterval: 1000,
    queryFn: async () => {
      if (instance?.id === undefined) {
        throw new Error('Process instance id is undefined');
      }

      const {response, error} = await request(
        api.searchTasks({
          pageSize: 10,
          processInstanceKey: instance.id,
          state: 'CREATED',
        }),
      );

      if (response !== null) {
        return await response.json();
      }

      if (error !== null) {
        throw error;
      }

      throw new Error('No tasks found');
    },
    cacheTime: 0,
    refetchOnWindowFocus: false,
    onSuccess(data) {
      if (data.length === 0) {
        return;
      }

      newProcessInstance.removeInstance(data);

      if (data.length === 1 && location.pathname === `/${pages.processes}`) {
        const [{id}] = data;

        tracking.track({
          eventName: 'process-tasks-polling-ended',
          outcome: 'single-task-found',
        });

        navigate({pathname: pages.taskDetails(id)});

        return;
      }
    },
  });

  useEffect(() => {
    return () => {
      if (newProcessInstance.instance !== null) {
        newProcessInstance.removeInstance(null);
      }
    };
  }, []);

  return null;
});

export {NewProcessInstanceTasksPolling};
