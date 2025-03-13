/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type InlineLoadingProps, Stack} from '@carbon/react';
import {ArrowRight} from '@carbon/react/icons';
import {AsyncActionButton} from 'common/components/AsyncActionButton';
import {notificationsStore} from 'common/notifications/notifications.store';
import {newProcessInstance} from 'v1/newProcessInstance';
import {useState} from 'react';
import {useNavigate, useMatch, useLocation} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {t} from 'i18next';
import {pages} from 'common/routing';
import {logger} from 'common/utils/logger';
import {tracking} from 'common/tracking';
import {useStartProcess} from 'v1/api/useStartProcess.mutation';
import type {Process, Task} from 'v1/api/types';
import {FormModal} from './FormModal';
import {getProcessDisplayName} from 'v1/utils/getProcessDisplayName';
import {ProcessTag} from './ProcessTag';
import styles from './styles.module.scss';
import cn from 'classnames';
import {useUploadDocuments} from 'common/api/useUploadDocuments.mutation';
import {getClientConfig} from 'common/config/getClientConfig';

type InlineLoadingStatus = NonNullable<InlineLoadingProps['status']>;

type LoadingStatus = InlineLoadingStatus | 'active-tasks';

type ProcessTagVariant = React.ComponentProps<typeof ProcessTag>['variant'];

function convertStatus(status: LoadingStatus): InlineLoadingStatus {
  if (status === 'active-tasks') {
    return 'active';
  }

  return status;
}

function getAsyncButtonDescription(status: LoadingStatus) {
  if (status === 'active') {
    return t('processesStartProcessPendingStatusText');
  }

  if (status === 'active-tasks') {
    return t('processesStartProcessWaitForTasksText');
  }

  if (status === 'finished') {
    return t('processesStartProcessSuccess');
  }

  if (status === 'error') {
    return t('processesStartProcessFailed');
  }

  return '';
}

function getTags(process: Process): ProcessTagVariant[] {
  const tags: ProcessTagVariant[] = [];

  if (process.startEventFormId !== null) {
    tags.push('start-form');
  }

  return tags;
}

type Props = {
  process: Process;
  className?: string;
  isFirst: boolean;
  isStartButtonDisabled: boolean;
  'data-testid'?: string;
  tenantId?: Task['tenantId'];
};

const ProcessTile: React.FC<Props> = ({
  process,
  isFirst,
  isStartButtonDisabled,
  tenantId,
  className,
  ...props
}) => {
  const {t} = useTranslation();
  const {mutateAsync: uploadDocuments} = useUploadDocuments();
  const {mutateAsync: startProcess} = useStartProcess({
    onSuccess(data) {
      tracking.track({
        eventName: 'process-started',
      });
      setStatus('active-tasks');

      newProcessInstance.setInstance({
        ...data,
        removeCallback: () => {
          setStatus('finished');
        },
      });
      notificationsStore.displayNotification({
        isDismissable: true,
        kind: 'success',
        title: t('processesStartProcessNotificationSuccess'),
      });
    },
  });
  const [status, setStatus] = useState<LoadingStatus>('inactive');
  const {bpmnProcessId, startEventFormId} = process;
  const displayName = getProcessDisplayName(process);
  const location = useLocation();
  const navigate = useNavigate();
  const startFormModalRoute = pages.internalStartProcessFromForm(bpmnProcessId);
  const match = useMatch(startFormModalRoute);
  const isFormModalOpen = match !== null;
  const tags = getTags(process);

  return (
    <div className={cn(className, styles.container)} {...props}>
      <Stack className={styles.content} data-testid="process-tile-content">
        <Stack className={styles.titleWrapper}>
          <h4 className={styles.title}>{displayName}</h4>
          <span className={styles.subtitle}>
            {displayName === bpmnProcessId ? '' : bpmnProcessId}
          </span>
        </Stack>
        <div className={styles.buttonRow}>
          <ul
            title={t('processesProcessTileAttributes')}
            aria-hidden={tags.length === 0}
          >
            {tags.map((type) => (
              <li key={type}>
                <ProcessTag variant={type} />
              </li>
            ))}
          </ul>
          <AsyncActionButton
            status={convertStatus(status)}
            buttonProps={{
              type: 'button',
              kind: 'tertiary',
              size: 'sm',
              className: 'startButton',
              renderIcon: startEventFormId === null ? undefined : ArrowRight,
              id: isFirst ? 'main-content' : '',
              autoFocus: isFirst,
              disabled: isStartButtonDisabled,
              onClick: async () => {
                if (startEventFormId === null) {
                  setStatus('active');
                  tracking.track({
                    eventName: 'process-start-clicked',
                  });
                  try {
                    await startProcess({
                      bpmnProcessId,
                      tenantId,
                    });
                  } catch (error) {
                    logger.error(error);
                    setStatus('error');
                  }
                } else {
                  navigate({
                    ...location,
                    pathname: startFormModalRoute,
                  });
                }
              },
            }}
            onError={() => {
              tracking.track({
                eventName: 'process-start-failed',
              });
              setStatus('inactive');
              if (
                getClientConfig().isMultiTenancyEnabled &&
                tenantId === undefined
              ) {
                notificationsStore.displayNotification({
                  isDismissable: false,
                  kind: 'error',
                  title: t('processesStartProcessFailedMissingTenant'),
                  subtitle: displayName,
                });
              } else {
                notificationsStore.displayNotification({
                  isDismissable: false,
                  kind: 'error',
                  title: t('processesStartProcessFailed'),
                  subtitle: displayName,
                });
              }
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
            {t('processesTileStartProcessButtonLabel')}
          </AsyncActionButton>
        </div>
      </Stack>

      {startEventFormId === null ? null : (
        <FormModal
          key={process.bpmnProcessId}
          process={process}
          isOpen={isFormModalOpen}
          onClose={() => {
            navigate({
              ...location,
              pathname: '/processes',
            });
          }}
          onSubmit={async (variables) => {
            await startProcess({
              bpmnProcessId,
              variables,
              tenantId,
            });
            navigate({
              ...location,
              pathname: '/processes',
            });
          }}
          onFileUpload={async (files: Map<string, File[]>) => {
            if (files.size === 0) {
              return new Map();
            }

            return uploadDocuments({
              files,
            });
          }}
          isMultiTenancyEnabled={getClientConfig().isMultiTenancyEnabled}
          tenantId={tenantId}
        />
      )}
    </div>
  );
};

export {ProcessTile};
