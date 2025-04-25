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
import {useTranslation} from 'react-i18next';
import {t} from 'i18next';
import {ProcessTag} from './ProcessTag';
import styles from './styles.module.scss';
import cn from 'classnames';
import type {MultiModeProcess} from 'common/processes';
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

function getTags(process: MultiModeProcess): ProcessTagVariant[] {
  const tags: ProcessTagVariant[] = [];

  if (
    'startEventFormId' in process &&
    typeof process.startEventFormId === 'string'
  ) {
    tags.push('start-form');
  }

  return tags;
}

function getNormalizedProcess(process: MultiModeProcess): {
  bpmnProcessId: string;
  hasStartForm: boolean;
} {
  if ('bpmnProcessId' in process) {
    return {
      bpmnProcessId: process.bpmnProcessId,
      hasStartForm: process.startEventFormId !== null,
    };
  }

  return {
    bpmnProcessId: process.processDefinitionId,
    hasStartForm: false,
  };
}

type Props = {
  process: MultiModeProcess;
  className?: string;
  isFirst: boolean;
  isStartButtonDisabled: boolean;
  'data-testid'?: string;
  tenantId?: string;
  onStartProcess: () => void;
  onStartProcessError: () => void;
  onStartProcessSuccess: () => void;
  status: LoadingStatus;
  displayName: string;
};

const ProcessTile: React.FC<Props> = ({
  process,
  isFirst,
  isStartButtonDisabled,
  tenantId,
  className,
  onStartProcess,
  onStartProcessError,
  onStartProcessSuccess,
  status,
  displayName,
  ...props
}) => {
  const {t} = useTranslation();
  const {bpmnProcessId, hasStartForm} = getNormalizedProcess(process);
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
              renderIcon: hasStartForm ? ArrowRight : undefined,
              id: isFirst ? 'main-content' : '',
              autoFocus: isFirst,
              disabled: isStartButtonDisabled,
              onClick: onStartProcess,
            }}
            onError={onStartProcessError}
            inlineLoadingProps={{
              description: getAsyncButtonDescription(status),
              'aria-live': ['error', 'finished'].includes(status)
                ? 'assertive'
                : 'polite',
              onSuccess: onStartProcessSuccess,
            }}
          >
            {t('processesTileStartProcessButtonLabel')}
          </AsyncActionButton>
        </div>
      </Stack>
    </div>
  );
};

export {ProcessTile};
