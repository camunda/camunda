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
import {match, Pattern} from 'ts-pattern';
import {AssigneeTag} from 'common/components/AssigneeTag';
import type {CurrentUser} from '@vzeta/camunda-api-zod-schemas/8.8';
import styles from './styles.module.scss';
import taskDetailsLayoutCommon from 'common/tasks/details/taskDetailsLayoutCommon.module.scss';
import {ActiveTransitionLoadingText} from 'common/tasks/details/ActiveTransitionLoadingText';

type Props = {
  taskName: string;
  processName: string;
  assignee: string | null;
  taskState:
    | 'CREATED'
    | 'COMPLETED'
    | 'CANCELED'
    | 'FAILED'
    | 'ASSIGNING'
    | 'UPDATING'
    | 'COMPLETING'
    | 'CANCELING';
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
        {match({taskState, assignee})
          .with({taskState: 'COMPLETED'}, ({assignee}) => (
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
                    <span
                      className={styles.taskAssignee}
                      data-testid="assignee"
                    >
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
          ))
          .with(
            {taskState: Pattern.union('CREATED', 'CANCELED', 'FAILED')},
            ({assignee}) => (
              <>
                <span className={styles.taskAssignee} data-testid="assignee">
                  <AssigneeTag
                    currentUser={user}
                    assignee={assignee}
                    isShortFormat={false}
                  />
                </span>
                <span className={styles.assignButtonContainer}>
                  {assignButton}
                </span>
              </>
            ),
          )
          .with(
            {
              taskState: Pattern.union('UPDATING', 'CANCELING'),
              assignee: null,
            },
            ({taskState}) => (
              <ActiveTransitionLoadingText taskState={taskState} />
            ),
          )
          .with(
            {taskState: Pattern.union('UPDATING', 'CANCELING', 'COMPLETING')},
            ({assignee}) => (
              <span className={styles.taskAssignee} data-testid="assignee">
                <AssigneeTag
                  currentUser={user}
                  assignee={assignee}
                  isShortFormat={false}
                />
              </span>
            ),
          )
          .with({taskState: 'ASSIGNING'}, () => (
            <span className={styles.assignButtonContainer}>{assignButton}</span>
          ))
          .exhaustive()}
      </div>
    </header>
  );
};

export {TaskDetailsHeader};
