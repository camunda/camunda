/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {InlineLoadingStatus, Stack} from '@carbon/react';
import {ArrowRight} from '@carbon/react/icons';
import {AsyncActionButton} from 'modules/components/AsyncActionButton';
import {notificationsStore} from 'modules/stores/notifications';
import {newProcessInstance} from 'modules/stores/newProcessInstance';
import {useState} from 'react';
import {
  Container,
  Content,
  TitleWrapper,
  Title,
  Subtitle,
  ButtonRow,
} from './styled';
import {useNavigate, useMatch, useLocation} from 'react-router-dom';
import {pages} from 'modules/routing';
import {logger} from 'modules/utils/logger';
import {tracking} from 'modules/tracking';
import {useStartProcess} from 'modules/mutations/useStartProcess';
import {Process, Task} from 'modules/types';
import {FormModal} from './FormModal';
import {getProcessDisplayName} from 'modules/utils/getProcessDisplayName';
import {ProcessTag} from './ProcessTag';

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

function getTags(process: Process): ProcessTagVariant[] {
  const tags: ProcessTagVariant[] = [];

  if (process.startEventFormId !== null) {
    tags.push('start-form');
  }

  return tags;
}

const isMultiTenancyEnabled =
  window.clientConfig?.isMultiTenancyEnabled ?? false;

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
  ...props
}) => {
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
        title: 'Process has started',
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
    <Container {...props}>
      <Stack as={Content} data-testid="process-tile-content">
        <Stack as={TitleWrapper}>
          <Title>{displayName}</Title>
          <Subtitle>
            {displayName === bpmnProcessId ? '' : bpmnProcessId}
          </Subtitle>
        </Stack>
        <ButtonRow>
          <ul title="Process Attributes" aria-hidden={tags.length === 0}>
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
              renderIcon: startEventFormId === null ? null : ArrowRight,
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
              if (isMultiTenancyEnabled && tenantId === undefined) {
                notificationsStore.displayNotification({
                  isDismissable: false,
                  kind: 'error',
                  title: 'Process start failed. Please select a tenant.',
                  subtitle: displayName,
                });
              } else {
                notificationsStore.displayNotification({
                  isDismissable: false,
                  kind: 'error',
                  title: 'Process start failed',
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
            Start process
          </AsyncActionButton>
        </ButtonRow>
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
          isMultiTenancyEnabled={isMultiTenancyEnabled}
          tenantId={tenantId}
        />
      )}
    </Container>
  );
};

export {ProcessTile};
