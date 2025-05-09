/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {pages} from 'common/routing';
import type {Task} from 'v1/api/types';
import {useLocation, useNavigate} from 'react-router-dom';
import {tracking} from 'common/tracking';
import {useQuery} from '@tanstack/react-query';
import {request, type RequestError} from 'common/api/request';
import {api} from 'v1/api';

type NewTasksResponse = Task[];

type Props = {
  newInstance: {
    id: string;
    removeCallback: () => void;
  } | null;
};

const NewProcessInstanceTasksPolling: React.FC<Props> = ({newInstance}) => {
  const navigate = useNavigate();
  const location = useLocation();

  useQuery<NewTasksResponse, RequestError | Error>({
    queryKey: ['newTasks', newInstance?.id],
    enabled: newInstance !== null,
    refetchInterval: 1000,
    queryFn: async () => {
      const id = newInstance?.id;
      if (id === undefined) {
        throw new Error('Process instance id is undefined');
      }

      const {response, error} = await request(
        api.searchTasks({
          pageSize: 10,
          processInstanceKey: id,
          state: 'CREATED',
        }),
      );

      if (response !== null) {
        const data = await response.json();

        if (data.length === 0) {
          return null;
        }

        newInstance!.removeCallback();

        if (
          data.length === 1 &&
          location.pathname === `/${pages.processes()}`
        ) {
          const [{id}] = data;

          tracking.track({
            eventName: 'process-tasks-polling-ended',
            outcome: 'single-task-found',
          });

          navigate({pathname: pages.taskDetails(id)});

          return null;
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
};

export {NewProcessInstanceTasksPolling};
