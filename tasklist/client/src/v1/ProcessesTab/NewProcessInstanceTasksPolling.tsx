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
import {request} from 'common/api/request';
import {api} from 'v1/api';
import type {NewProcessInstance} from 'common/processes/newProcessInstance';
import {observer} from 'mobx-react-lite';
import {useEffect} from 'react';
type NewTasksResponse = Task[];

type Props = {
  newInstance: NewProcessInstance;
};

const NewProcessInstanceTasksPolling: React.FC<Props> = observer(
  ({newInstance}) => {
    const {instance} = newInstance;
    const navigate = useNavigate();
    const location = useLocation();

    const {data} = useQuery({
      queryKey: ['newTasks', newInstance, instance?.id],
      enabled: instance !== null,
      refetchInterval: 1000,
      queryFn: async () => {
        const id = instance?.id;
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

          return data as NewTasksResponse;
        }

        if (error !== null) {
          throw error;
        }

        throw new Error('No tasks found');
      },
      gcTime: 0,
      refetchOnWindowFocus: false,
    });

    useEffect(() => {
      if (data === undefined) {
        return;
      }
      if (data.length === 0) {
        return;
      }

      newInstance.removeInstance();

      if (data.length === 1 && location.pathname === `/${pages.processes()}`) {
        const [{id}] = data;

        tracking.track({
          eventName: 'process-tasks-polling-ended',
          outcome: 'single-task-found',
        });

        navigate({pathname: pages.taskDetails(id)});

        return;
      }
    }, [data, location.pathname, navigate, newInstance]);

    return null;
  },
);

export {NewProcessInstanceTasksPolling};
