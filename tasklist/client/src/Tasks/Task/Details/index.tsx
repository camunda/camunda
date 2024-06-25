/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Section} from '@carbon/react';
import {Outlet, useMatch, useNavigate} from 'react-router-dom';
import {CurrentUser, Process, Task} from 'modules/types';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {useTask} from 'modules/queries/useTask';
import {useProcessDefinition} from 'modules/queries/useProcessDefinition';
import {useTaskDetailsParams, pages} from 'modules/routing';
import {DetailsSkeleton} from './DetailsSkeleton';
import {TabListNav} from './TabListNav';
import {TurnOnNotificationPermission} from './TurnOnNotificationPermission';
import {Aside} from './Aside';
import {Header} from './Header';
import styles from './styles.module.scss';
import {useEffect} from 'react';
import {notificationsStore} from 'modules/stores/notifications';

type OutletContext = {
  task: Task;
  currentUser: CurrentUser;
  refetch: () => void;
  process: Process | undefined;
};

const Details: React.FC = () => {
  const {id} = useTaskDetailsParams();
  const {data: task, refetch} = useTask(id, {
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    refetchInterval: 5000,
  });
  const taskState = task?.taskState;
  const isTaskCompleted = taskState === 'COMPLETED';
  const {data: process, isLoading: processLoading} = useProcessDefinition(
    task?.processDefinitionKey ?? '',
    {
      enabled: task !== undefined && !isTaskCompleted,
    },
  );
  const {data: currentUser} = useCurrentUser();
  const onAssignmentError = () => refetch();

  const navigate = useNavigate();
  useEffect(() => {
    if (taskState === 'CANCELED') {
      notificationsStore.displayNotification({
        kind: 'info',
        title: 'Process instance cancelled',
        subtitle: `${task?.processName} (${task?.processInstanceKey})`,
        isDismissable: true,
      });
      navigate(pages.initial);
    }
  }, [navigate, task?.processInstanceKey, task?.processName, taskState]);

  const tabs = [
    {
      key: 'task',
      title: 'Task',
      label: 'Show task',
      selected: useMatch(pages.taskDetails()) !== null,
      to: {
        pathname: pages.taskDetails(id),
      },
    },
    {
      key: 'process',
      title: 'Process',
      label: 'Show associated BPMN process',
      selected: useMatch(pages.taskDetailsProcess()) !== null,
      to: {
        pathname: pages.taskDetailsProcess(id),
      },
      visible:
        !isTaskCompleted && process !== undefined && process.bpmnXml !== null,
    },
  ];

  if (task === undefined || currentUser === undefined || processLoading) {
    return <DetailsSkeleton data-testid="details-skeleton" />;
  }

  return (
    <div className={styles.container} data-testid="details-info">
      <Section className={styles.content} level={4}>
        <TurnOnNotificationPermission />
        <Header
          task={task}
          user={currentUser}
          onAssignmentError={onAssignmentError}
        />
        <TabListNav label="Task Details Navigation" items={tabs} />
        <Outlet
          context={
            {task, currentUser, refetch, process} satisfies OutletContext
          }
        />
      </Section>
      <Aside task={task} user={currentUser} />
    </div>
  );
};

export {Details as Component};
export type {OutletContext};
