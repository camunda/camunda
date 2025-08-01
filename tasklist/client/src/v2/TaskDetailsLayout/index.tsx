/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CurrentUser, UserTask} from '@vzeta/camunda-api-zod-schemas/8.8';
import taskDetailsLayoutCommon from 'common/tasks/details/taskDetailsLayoutCommon.module.scss';
import {Section} from '@carbon/react';
import {TurnOnNotificationPermission} from 'common/tasks/details/TurnOnNotificationPermission';
import {TabListNav} from 'common/tasks/details/TabListNav';
import {Aside} from 'common/tasks/details/Aside';
import {pages, useTaskDetailsParams} from 'common/routing';
import {useTranslation} from 'react-i18next';
import {useCurrentUser} from 'common/api/useCurrentUser.query';
import {Outlet, useMatch, useNavigate, useSearchParams} from 'react-router-dom';
import {DetailsSkeleton} from 'common/tasks/details/DetailsSkeleton';
import {useProcessDefinitionXml} from 'v2/api/useProcessDefinitionXml.query';
import {useTask} from 'v2/api/useTask.query';
import {useEffect} from 'react';
import {notificationsStore} from 'common/notifications/notifications.store';
import {TaskDetailsHeader} from 'common/tasks/details/TaskDetailsHeader';
import {tracking} from 'common/tracking';
import {decodeTaskOpenedRef} from 'common/tracking/reftags';
import {AssignButton} from './AssignButton';

type OutletContext = {
  task: UserTask;
  currentUser: CurrentUser;
  refetch: () => void;
  processXml: string | undefined;
};

const TaskDetailsLayout: React.FC = () => {
  const {id} = useTaskDetailsParams();
  const {t} = useTranslation();
  const {data: currentUser} = useCurrentUser();
  const {data: task, refetch} = useTask(id, {
    refetchInterval(query) {
      const {data} = query.state;
      if (data?.state === 'COMPLETING' || data?.state === 'ASSIGNING') {
        return 1000;
      }

      return false;
    },
  });
  const isTaskCompleted = task?.state === 'COMPLETED';
  const {data: processXml, isLoading: processLoading} = useProcessDefinitionXml(
    task?.processDefinitionKey ?? '',
    {
      enabled: task !== undefined && !isTaskCompleted,
    },
  );
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  useEffect(() => {
    if (task?.state === 'CANCELED') {
      notificationsStore.displayNotification({
        kind: 'info',
        title: t('processInstanceCancelledNotification'),
        subtitle: `${task?.processName ?? task?.processDefinitionId} (${task?.processInstanceKey})`,
        isDismissable: true,
      });
      navigate(pages.initial);
    }
  }, [
    navigate,
    task?.processInstanceKey,
    task?.processName,
    task?.state,
    task?.processDefinitionId,
    t,
  ]);

  useEffect(() => {
    const search = new URLSearchParams(searchParams);
    const ref = search.get('ref');
    if (search.has('ref')) {
      search.delete('ref');
      setSearchParams(search, {replace: true});
    }

    const taskOpenedRef = decodeTaskOpenedRef(ref);
    tracking.track({
      eventName: 'task-opened',
      ...(taskOpenedRef ?? {}),
    });
  }, [searchParams, setSearchParams, id]);

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
      visible: !isTaskCompleted && processXml !== undefined,
    },
  ];

  if (task === undefined || currentUser === undefined || processLoading) {
    return <DetailsSkeleton data-testid="details-skeleton" />;
  }

  return (
    <div
      className={taskDetailsLayoutCommon.container}
      data-testid="details-info"
    >
      <Section className={taskDetailsLayoutCommon.content} level={4}>
        <TurnOnNotificationPermission />
        <TaskDetailsHeader
          taskName={task.name ?? task.elementId}
          processName={task.processName ?? task.processDefinitionId}
          assignee={task.assignee ?? null}
          taskState={task.state}
          user={currentUser}
          assignButton={
            <AssignButton
              id={task.userTaskKey}
              taskState={task.state}
              assignee={task.assignee}
              currentUser={currentUser.username}
            />
          }
        />
        <TabListNav label={t('taskDetailsNavLabel')} items={tabs} />
        <Outlet
          context={
            {task, currentUser, refetch, processXml} satisfies OutletContext
          }
        />
      </Section>
      <Aside
        creationDate={task.creationDate}
        completionDate={task.completionDate}
        dueDate={task.dueDate}
        followUpDate={task.followUpDate}
        priority={task.priority}
        candidateUsers={task.candidateUsers}
        candidateGroups={task.candidateGroups}
        tenantId={task.tenantId}
        user={currentUser}
      />
    </div>
  );
};

export {TaskDetailsLayout as Component};
export type {OutletContext};
