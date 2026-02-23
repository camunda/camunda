/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {pages} from 'common/routing';
import {useLocation, useNavigate} from 'react-router-dom';
import {tracking} from 'common/tracking';
import {useQuery} from '@tanstack/react-query';
import {request} from 'common/api/request';
import {api} from 'v2/api';
import type {QueryUserTasksResponseBody} from '@camunda/camunda-api-zod-schemas/8.9';
import type {NewProcessInstance} from 'common/processes/newProcessInstance';
import {observer} from 'mobx-react-lite';

type Props = {
  newInstance: NewProcessInstance;
};

const NewProcessInstanceTasksPolling: React.FC<Props> = observer(
  ({newInstance}) => {
    const {instance} = newInstance;
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
          api.queryTasks({
            filter: {
              processInstanceKey: id,
              state: 'CREATED',
            },
            page: {
              limit: 10,
            },
          }),
        );

        if (response !== null) {
          const data = (await response.json()) as QueryUserTasksResponseBody;
          const {items} = data;

          if (items.length === 0) {
            return null;
          }

          newInstance.removeInstance();

          if (
            items.length === 1 &&
            location.pathname === `/${pages.processes()}`
          ) {
            const [{userTaskKey}] = items;

            tracking.track({
              eventName: 'process-tasks-polling-ended',
              outcome: 'single-task-found',
            });

            navigate({pathname: pages.taskDetails(userTaskKey)});

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
  },
);

export {NewProcessInstanceTasksPolling};
