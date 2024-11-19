/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react-lite';
import {pages} from 'modules/routing';
import {newProcessInstance} from 'modules/stores/newProcessInstance';
import {useLocation, useNavigate} from 'react-router-dom';
import {tracking} from 'modules/tracking';
import {useQuery} from '@tanstack/react-query';
import {request} from 'modules/request';
import {api} from 'modules/api';
import type {QueryUserTasksResponseBody} from '@vzeta/camunda-api-zod-schemas/tasklist';

const NewProcessInstanceTasksPolling: React.FC = observer(() => {
  const {instance} = newProcessInstance;
  const navigate = useNavigate();
  const location = useLocation();

  useQuery({
    queryKey: ['newTasks', instance?.id],
    enabled: instance !== null,
    refetchInterval: 1000,
    queryFn: async () => {
      const id = instance?.id;
      if (id === undefined) {
        throw new Error('Process instance id is undefined');
      }

      const {response, error} = await request(
        api.v2.queryTasks({
          filter: {
            processInstanceKey: parseInt(id, 10),
            state: 'CREATED',
          },
          page: {
            limit: 10,
          },
        }),
      );

      if (response !== null) {
        const data = (await response.json()) as QueryUserTasksResponseBody;

        if (data.items.length === 0) {
          return;
        }

        newProcessInstance.removeInstance();

        if (
          data.items.length === 1 &&
          location.pathname === `/${pages.processes()}`
        ) {
          const [{userTaskKey}] = data.items;

          tracking.track({
            eventName: 'process-tasks-polling-ended',
            outcome: 'single-task-found',
          });

          navigate({pathname: pages.taskDetails(userTaskKey)});

          return;
        }

        return data;
      }

      if (error !== null) {
        throw error;
      }

      throw new Error('No tasks found');
    },
    gcTime: 0,
    refetchOnWindowFocus: false,
  });

  return null;
});

export {NewProcessInstanceTasksPolling};
