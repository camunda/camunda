/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Section} from '@carbon/react';
import {Outlet, useMatch, useNavigate} from 'react-router-dom';
import type {Process, Task} from 'v1/api/types';
import type {CurrentUser} from '@vzeta/camunda-api-zod-schemas/identity';
import {useCurrentUser} from 'common/api/useCurrentUser.query';
import {useTask} from 'v1/api/useTask.query';
import {useProcessDefinition} from 'v1/api/useProcessDefinition.query';
import {useTaskDetailsParams, pages} from 'common/routing';
import {DetailsSkeleton} from './DetailsSkeleton';
import {TabListNav} from './TabListNav';
import {TurnOnNotificationPermission} from './TurnOnNotificationPermission';
import {Aside} from './Aside';
import {Header} from './Header';
import styles from './styles.module.scss';
import {useEffect} from 'react';
import {useTranslation} from 'react-i18next';
import {notificationsStore} from 'common/notifications/notifications.store';

type OutletContext = {
  task: Task;
  currentUser: CurrentUser;
  refetch: () => void;
  process: Process | undefined;
};

const Details: React.FC = () => {
  const {id} = useTaskDetailsParams();
  const {t} = useTranslation();
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
        title: t('processInstanceCancelledNotification'),
        subtitle: `${task?.processName} (${task?.processInstanceKey})`,
        isDismissable: true,
      });
      navigate(pages.initial);
    }
  }, [navigate, task?.processInstanceKey, task?.processName, taskState, t]);

  const tabs = [
    {
      key: 'task',
      title: t('taskDetailsTaskTabLabel'),
      label: t('taskDetailsShowTaskLabel'),
      selected: useMatch(pages.taskDetails()) !== null,
      to: {
        pathname: pages.taskDetails(id),
      },
    },
    {
      key: 'process',
      title: t('taskDetailsProcessTabLabel'),
      label: t('taskDetailsShowBpmnProcessLabel'),
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
        <TabListNav label={t('taskDetailsNavLabel')} items={tabs} />
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
