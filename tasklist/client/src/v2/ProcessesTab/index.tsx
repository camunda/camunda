/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  Column,
  Grid,
  Layer,
  Link,
  SkeletonPlaceholder,
  Stack,
  type InlineLoadingProps,
} from '@carbon/react';
import {useLocation, useNavigate} from 'react-router-dom';
import {useEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {observer} from 'mobx-react-lite';
import {newProcessInstance} from 'common/processes/newProcessInstance';
import {FirstTimeModal} from 'common/processes/FirstTimeModal';
import {notificationsStore} from 'common/notifications/notifications.store';
import {logger} from 'common/utils/logger';
import {NewProcessInstanceTasksPolling} from 'v1/ProcessesTab/NewProcessInstanceTasksPolling';
import {tracking} from 'common/tracking';
import {useProcesses} from 'v1/api/useProcesses.query';
import {useCurrentUser} from 'common/api/useCurrentUser.query';
import {pages} from 'common/routing';
import styles from './styles.module.scss';
import cn from 'classnames';
import {getClientConfig} from 'common/config/getClientConfig';
import {getProcessDisplayName} from 'v1/utils/getProcessDisplayName';
import {useStartProcess} from 'v1/api/useStartProcess.mutation';
import type {Process} from 'v1/api/types';
import {C3EmptyState} from '@camunda/camunda-composite-components';
import EmptyMessageImage from 'common/processes/empty-message-image.svg';
import {ProcessTile} from 'common/processes/ProcessTile';
import {getMultiModeProcessDisplayName} from 'common/processes/getMultiModeProcessDisplayName';

type InlineLoadingStatus = NonNullable<InlineLoadingProps['status']>;

type LoadingStatus = InlineLoadingStatus | 'active-tasks';

const ProcessesTab: React.FC = observer(() => {
  const [startProcessStatus, setStartProcessStatus] =
    useState<LoadingStatus>('inactive');
  const {t} = useTranslation();
  const {instance} = newProcessInstance;
  const {data: currentUser} = useCurrentUser();
  const defaultTenant = currentUser?.tenants[0];
  const location = useLocation();
  const navigate = useNavigate();
  const selectedTenantId = defaultTenant?.tenantId;
  const {data, error, isLoading} = useProcesses(
    {
      tenantId: selectedTenantId,
    },
    {
      refetchInterval: 5000,
      placeholderData: (previousData) => previousData,
    },
  );
  const [selectedProcess, setSelectedProcess] = useState<Process | null>(null);
  const {mutateAsync: startProcess} = useStartProcess({
    onSuccess(data) {
      tracking.track({
        eventName: 'process-started',
      });
      setStartProcessStatus('active-tasks');

      newProcessInstance.setInstance({
        id: data.id,
        removeCallback: () => {
          setStartProcessStatus('finished');
        },
      });
      notificationsStore.displayNotification({
        isDismissable: true,
        kind: 'success',
        title: t('processesStartProcessNotificationSuccess'),
      });
    },
  });
  const processes = data?.processes ?? [];

  useEffect(() => {
    if (error !== null) {
      tracking.track({
        eventName: 'processes-fetch-failed',
      });
      notificationsStore.displayNotification({
        isDismissable: false,
        kind: 'error',
        title: t('processesFetchFailed'),
      });
      logger.error(error);
    }
  }, [error, t]);

  return (
    <main className={cn('cds--content', styles.splitPane)}>
      <NewProcessInstanceTasksPolling newInstance={instance} />

      <div className={styles.container}>
        <Stack className={styles.content} gap={2}>
          <div className={styles.searchContainer}>
            <Stack className={styles.searchContainerInner} gap={6}>
              <Grid narrow>
                <Column sm={4} md={8} lg={16}>
                  <Stack gap={4}>
                    <h1>{t('headerNavItemProcesses')}</h1>
                    <p>{t('processesSubtitle')}</p>
                  </Stack>
                </Column>
              </Grid>
            </Stack>
          </div>

          <div className={styles.processTilesContainer}>
            <div className={styles.processTilesContainerInner}>
              {!isLoading && processes.length === 0 ? (
                <Layer>
                  <C3EmptyState
                    icon={{path: EmptyMessageImage, altText: ''}}
                    heading={t('processesProcessNotPublishedError')}
                    description={
                      <span data-testid="empty-message">
                        {t('processesErrorBody')}
                        <Link
                          href="https://docs.camunda.io/docs/components/modeler/web-modeler/run-or-publish-your-process/#publishing-a-process"
                          target="_blank"
                          rel="noopener noreferrer"
                          inline
                          onClick={() => {
                            tracking.track({
                              eventName: 'processes-empty-message-link-clicked',
                            });
                          }}
                        >
                          {t('processesErrorBodyLinkLabel')}
                        </Link>
                      </span>
                    }
                  />
                </Layer>
              ) : (
                <Grid narrow as={Layer}>
                  {isLoading
                    ? Array.from({length: 5}).map((_, index) => (
                        <Column
                          className={styles.processTileWrapper}
                          sm={4}
                          md={4}
                          lg={5}
                          key={index}
                        >
                          <SkeletonPlaceholder
                            className={styles.tileSkeleton}
                            data-testid="process-skeleton"
                          />
                        </Column>
                      ))
                    : processes.map((process, idx) => (
                        <Column
                          className={styles.processTileWrapper}
                          sm={4}
                          md={4}
                          lg={5}
                          key={process.id}
                        >
                          <ProcessTile
                            process={process}
                            displayName={getMultiModeProcessDisplayName(
                              process,
                            )}
                            isFirst={idx === 0}
                            isStartButtonDisabled={instance !== null}
                            data-testid="process-tile"
                            tenantId={selectedTenantId}
                            onStartProcess={async () => {
                              setSelectedProcess(process);
                              const {bpmnProcessId} = process;
                              if (process.startEventFormId === null) {
                                setStartProcessStatus('active');
                                tracking.track({
                                  eventName: 'process-start-clicked',
                                });
                                try {
                                  await startProcess({
                                    bpmnProcessId,
                                    tenantId: selectedTenantId,
                                  });
                                } catch (error) {
                                  logger.error(error);
                                  setStartProcessStatus('error');
                                }
                              } else {
                                navigate({
                                  ...location,
                                  pathname:
                                    pages.internalStartProcessFromForm(
                                      bpmnProcessId,
                                    ),
                                });
                              }
                            }}
                            onStartProcessError={() => {
                              setSelectedProcess(null);
                              const displayName =
                                getProcessDisplayName(process);
                              tracking.track({
                                eventName: 'process-start-failed',
                              });
                              setStartProcessStatus('inactive');
                              if (
                                getClientConfig().isMultiTenancyEnabled &&
                                selectedTenantId === undefined
                              ) {
                                notificationsStore.displayNotification({
                                  isDismissable: false,
                                  kind: 'error',
                                  title: t(
                                    'processesStartProcessFailedMissingTenant',
                                  ),
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
                            onStartProcessSuccess={() => {
                              setSelectedProcess(null);
                              setStartProcessStatus('inactive');
                            }}
                            status={
                              selectedProcess?.bpmnProcessId ===
                              process.bpmnProcessId
                                ? startProcessStatus
                                : 'inactive'
                            }
                          />
                        </Column>
                      ))}
                </Grid>
              )}
            </div>
          </div>
        </Stack>
      </div>

      <FirstTimeModal />
    </main>
  );
});

ProcessesTab.displayName = 'Processes';

export {ProcessesTab as Component};
