/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Stack} from '@carbon/react';
import {CheckmarkFilled} from '@carbon/react/icons';
import {AssigneeTag} from 'common/components/AssigneeTag';
import type {CurrentUser, UserTask} from '@vzeta/camunda-api-zod-schemas/8.8';
import styles from './styles.module.scss';
import taskDetailsLayoutCommon from 'common/tasks/details/taskDetailsLayoutCommon.module.scss';
import {TaskStateLoadingText} from 'common/tasks/details/TaskStateLoadingText';

type Props = {
  taskName: string;
  processName: string;
  assignee: string | null;
  taskState: UserTask['state'];
  assignButton: React.ReactNode;
  user: CurrentUser;
};

const TaskDetailsHeader: React.FC<Props> = ({
  taskName,
  processName,
  assignee,
  taskState,
  user,
  assignButton,
}) => {
  const {t} = useTranslation();

  return (
    <header
      className={taskDetailsLayoutCommon.header}
      title={t('taskDetailsHeader')}
    >
      <div className={taskDetailsLayoutCommon.headerLeftContainer}>
        <span className={styles.taskName}>{taskName}</span>
        <span className={styles.processName}>{processName}</span>
      </div>
      <div className={taskDetailsLayoutCommon.headerRightContainer}>
        {taskState === 'COMPLETED' && (
          <span
            className={styles.taskStatus}
            data-testid="completion-label"
            title={t('taskDetailsTaskCompletedBy')}
          >
            <Stack
              className={styles.alignItemsCenter}
              orientation="horizontal"
              gap={2}
            >
              <CheckmarkFilled size={16} color="green" />
              {assignee ? (
                <>
                  {t('taskDetailsTaskCompletedBy') + ' '}
                  <span className={styles.taskAssignee} data-testid="assignee">
                    <AssigneeTag
                      currentUser={user}
                      assignee={assignee}
                      isShortFormat
                    />
                  </span>
                </>
              ) : (
                t('taskAssignmentStatusCompleted')
              )}
            </Stack>
          </span>
        )}

        {['CREATED', 'CANCELED', 'FAILED'].includes(taskState) && (
          <>
            <span className={styles.taskAssignee} data-testid="assignee">
              <AssigneeTag
                currentUser={user}
                assignee={assignee}
                isShortFormat={false}
              />
            </span>
            <span className={styles.assignButtonContainer}>{assignButton}</span>
          </>
        )}

        {['UPDATING', 'CANCELING'].includes(taskState) &&
          (assignee ? (
            <span className={styles.taskAssignee} data-testid="assignee">
              <AssigneeTag
                currentUser={user}
                assignee={assignee}
                isShortFormat={false}
              />
            </span>
          ) : (
            <TaskStateLoadingText taskState={taskState} />
          ))}

        {taskState === 'ASSIGNING' && (
          <TaskStateLoadingText taskState={taskState} />
        )}

        {taskState === 'COMPLETING' && (
          <span className={styles.taskAssignee} data-testid="assignee">
            <AssigneeTag
              currentUser={user}
              assignee={assignee}
              isShortFormat={false}
            />
          </span>
        )}
      </div>
    </header>
  );
};

export {TaskDetailsHeader};
