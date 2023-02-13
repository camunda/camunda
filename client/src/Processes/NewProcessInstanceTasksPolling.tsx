/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useQuery} from '@apollo/client';
import {observer} from 'mobx-react-lite';
import {Pages} from 'modules/constants/pages';
import {newProcessInstance} from 'modules/stores/newProcessInstance';
import {ProcessInstance} from 'modules/types';
import {useEffect} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {TaskStates} from 'modules/constants/taskStates';
import {
  GetNewTasks,
  GetNewTasksVariables,
  GET_NEW_TASKS,
} from 'modules/queries/get-new-tasks';
import {tracking} from 'modules/tracking';

const NewProcessInstanceTasksPolling: React.FC = observer(() => {
  if (newProcessInstance.instance === null) {
    return null;
  }

  const {id} = newProcessInstance.instance;

  return <PollForTasks processInstanceId={id} />;
});

type Props = {
  processInstanceId: ProcessInstance['id'];
};

const PollForTasks: React.FC<Props> = observer(({processInstanceId}) => {
  const navigate = useNavigate();
  const location = useLocation();
  const {data} = useQuery<GetNewTasks, GetNewTasksVariables>(GET_NEW_TASKS, {
    fetchPolicy: 'network-only',
    pollInterval: 1000,
    variables: {
      pageSize: 10,
      processInstanceId,
      state: TaskStates.Created,
    },
  });

  useEffect(() => {
    if (data === undefined || data.tasks.length <= 0) {
      return;
    }

    const {tasks} = data;

    newProcessInstance.removeInstance(tasks);

    if (tasks.length === 1 && location.pathname === `/${Pages.Processes}`) {
      const [{id}] = tasks;

      tracking.track({
        eventName: 'process-tasks-polling-ended',
        outcome: 'single-task-found',
      });

      navigate({pathname: Pages.TaskDetails(id)});

      return;
    }
  }, [data, processInstanceId, navigate, location]);

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
