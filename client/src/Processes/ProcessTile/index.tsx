/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useMutation} from '@apollo/client';
import {InlineLoadingStatus, Stack} from '@carbon/react';
import {AsyncActionButton} from 'modules/components/AsyncActionButton';
import {
  StartProcess,
  StartProcessVariables,
  START_PROCESS,
} from 'modules/mutations/start-process';
import {notificationsStore} from 'modules/stores/notifications';
import {newProcessInstance} from 'modules/stores/newProcessInstance';
import {useState} from 'react';
import {Container, Title, Subtitle} from './styled';
import {useNavigate} from 'react-router-dom';
import {Pages} from 'modules/constants/pages';
import {logger} from 'modules/utils/logger';
import {tracking} from 'modules/tracking';

type LoadingStatus = InlineLoadingStatus | 'active-tasks';

function convertStatus(status: LoadingStatus): InlineLoadingStatus {
  if (status === 'active-tasks') {
    return 'active';
  }

  return status;
}

function getAsyncButtonDescription(status: LoadingStatus) {
  if (status === 'active') {
    return 'Starting process...';
  }

  if (status === 'active-tasks') {
    return 'Waiting for tasks...';
  }

  if (status === 'finished') {
    return 'Process started';
  }

  if (status === 'error') {
    return 'Process start failed';
  }

  return '';
}

type Props = {
  name: string | null;
  processDefinitionId: string;
  className?: string;
  isStartButtonDisabled: boolean;
  'data-testid'?: string;
};

const ProcessTile: React.FC<Props> = ({
  name,
  processDefinitionId,
  isStartButtonDisabled,
  ...props
}) => {
  const [status, setStatus] = useState<LoadingStatus>('inactive');
  const displayName = name ?? processDefinitionId;
  const navigate = useNavigate();

  const [startProcess] = useMutation<StartProcess, StartProcessVariables>(
    START_PROCESS,
    {
      variables: {
        processDefinitionId,
      },
    },
  );

  return (
    <Container {...props}>
      <Stack>
        <Title>{displayName}</Title>
        <Subtitle>
          {displayName !== processDefinitionId ? processDefinitionId : ''}
        </Subtitle>
        <AsyncActionButton
          status={convertStatus(status)}
          buttonProps={{
            type: 'button',
            kind: 'tertiary',
            size: 'sm',
            disabled: isStartButtonDisabled,
            onClick: async () => {
              setStatus('active');
              try {
                tracking.track({
                  eventName: 'process-start-clicked',
                });
                const result = await startProcess();
                const instance = result.data?.startProcess;
                tracking.track({
                  eventName: 'process-started',
                });
                setStatus('active-tasks');

                if (instance !== undefined) {
                  newProcessInstance.setInstance({
                    ...instance,
                    removeCallback: (tasks = []) => {
                      setStatus('finished');
                      if (tasks === null) {
                        tracking.track({
                          eventName: 'process-tasks-polling-ended',
                          outcome: 'navigated-away',
                        });
                        notificationsStore.displayNotification({
                          isDismissable: true,
                          kind: 'info',
                          title:
                            'The task will appear in the list once it is created.',
                        });

                        return;
                      }

                      if (tasks.length === 0) {
                        tracking.track({
                          eventName: 'process-tasks-polling-ended',
                          outcome: 'no-tasks-found',
                        });
                        notificationsStore.displayNotification({
                          isDismissable: true,
                          kind: 'info',
                          title:
                            "We couldn't find a task for the started process.",
                          subtitle:
                            'Your process might have not reached a user task yet or the process might have an incident.',
                        });

                        return;
                      }

                      if (tasks.length > 1) {
                        tracking.track({
                          eventName: 'process-tasks-polling-ended',
                          outcome: 'multiple-tasks-found',
                        });
                        tasks.forEach(({name, id, processName}) => {
                          notificationsStore.displayNotification({
                            isDismissable: false,
                            kind: 'success',
                            title: `Process "${processName}" reached task "${name}"`,
                            isActionable: true,
                            actionButtonLabel: 'Open task',
                            onActionButtonClick: () => {
                              tracking.track({
                                eventName: 'process-task-toast-clicked',
                              });
                              navigate({pathname: Pages.TaskDetails(id)});
                            },
                          });
                        });

                        return;
                      }
                    },
                  });
                  notificationsStore.displayNotification({
                    isDismissable: true,
                    kind: 'success',
                    title: 'Process has started',
                    subtitle:
                      'We will redirect you to the task once it is created',
                  });
                }
              } catch (error) {
                logger.error(error);
                setStatus('error');
              }
            },
          }}
          onError={() => {
            tracking.track({
              eventName: 'process-start-failed',
            });
            setStatus('inactive');
            notificationsStore.displayNotification({
              isDismissable: false,
              kind: 'error',
              title: 'Process start failed',
              subtitle: displayName,
            });
          }}
          inlineLoadingProps={{
            description: getAsyncButtonDescription(status),
            'aria-live': ['error', 'finished'].includes(status)
              ? 'assertive'
              : 'polite',
            onSuccess: () => {
              setStatus('inactive');
            },
          }}
        >
          Start process
        </AsyncActionButton>
      </Stack>
    </Container>
  );
};

export {ProcessTile};
