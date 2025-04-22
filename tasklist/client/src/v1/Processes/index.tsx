/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type InlineLoadingProps} from '@carbon/react';
import debounce from 'lodash/debounce';
import {
  useLocation,
  useNavigate,
  useMatch,
  useSearchParams,
} from 'react-router-dom';
import {useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {observer} from 'mobx-react-lite';
import {newProcessInstance} from 'v1/newProcessInstance';
import {FirstTimeModal} from 'common/processes/FirstTimeModal';
import {notificationsStore} from 'common/notifications/notifications.store';
import {logger} from 'common/utils/logger';
import {NewProcessInstanceTasksPolling} from './NewProcessInstanceTasksPolling';
import {tracking} from 'common/tracking';
import {useProcesses} from 'v1/api/useProcesses.query';
import {useCurrentUser} from 'common/api/useCurrentUser.query';
import {getStateLocally, storeStateLocally} from 'common/local-storage';
import {pages} from 'common/routing';
import styles from './styles.module.scss';
import cn from 'classnames';
import {getClientConfig} from 'common/config/getClientConfig';
import {FormModal} from 'common/processes/FormModal';
import {useUploadDocuments} from 'common/api/useUploadDocuments.mutation';
import {useForm} from 'v1/api/useForm.query';
import {getProcessDisplayName} from 'v1/utils/getProcessDisplayName';
import {useStartProcess} from 'v1/api/useStartProcess.mutation';
import type {Process} from 'v1/api/types';
import {ProcessesList} from 'common/processes/ProcessesList';
import {START_FORM_FILTER_OPTIONS} from 'common/processes/ProcessesList/constants';

type InlineLoadingStatus = NonNullable<InlineLoadingProps['status']>;

type LoadingStatus = InlineLoadingStatus | 'active-tasks';

function getIsStartedByForm(searchParamValue: string | undefined) {
  if (searchParamValue === undefined) {
    return undefined;
  }
  return searchParamValue === 'yes';
}

const Processes: React.FC = observer(() => {
  const [startProcessStatus, setStartProcessStatus] =
    useState<LoadingStatus>('inactive');
  const {t} = useTranslation();
  const {instance} = newProcessInstance;
  const {data: currentUser} = useCurrentUser();

  const hasMultipleTenants = (currentUser?.tenants.length ?? 0) > 1;
  const defaultTenant = currentUser?.tenants[0];
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const updateSearchParams = (
    current: URLSearchParams,
    params: {
      name: 'search' | 'tenantId' | 'hasStartForm';
      value: string;
    },
  ) => {
    const {name, value} = params;
    if (value) {
      current.set(name, value);
    } else {
      current.delete(name);
    }
    setSearchParams(current);
  };
  const selectedTenantId = hasMultipleTenants
    ? (searchParams.get('tenantId') ?? defaultTenant?.tenantId)
    : defaultTenant?.tenantId;
  const startFormFilterSearchParam =
    searchParams.get('hasStartForm') ?? undefined;
  const startFormFilter =
    (startFormFilterSearchParam
      ? START_FORM_FILTER_OPTIONS.find(
          ({searchParamValue}) =>
            searchParamValue === startFormFilterSearchParam,
        )
      : undefined) ?? START_FORM_FILTER_OPTIONS[0];
  const {data, error, isLoading} = useProcesses(
    {
      query: searchParams.get('search') ?? undefined,
      tenantId: selectedTenantId,
      isStartedByForm: getIsStartedByForm(startFormFilter.searchParamValue),
    },
    {
      refetchInterval: 5000,
      placeholderData: (previousData) => previousData,
    },
  );
  const debouncedNavigate = useRef(debounce(updateSearchParams, 500)).current;
  const initialTenantId = useRef(
    defaultTenant?.tenantId ?? getStateLocally('tenantId'),
  ).current;
  const [searchValue, setSearchValue] = useState(
    searchParams.get('search') ?? '',
  );
  const isFiltered = data?.query !== undefined && data.query !== '';
  const match = useMatch(pages.internalStartProcessFromForm());
  const [selectedProcess, setSelectedProcess] = useState<Process | null>(null);
  const {mutateAsync: uploadDocuments} = useUploadDocuments();
  const formQueryResult = useForm(
    {
      id: selectedProcess?.startEventFormId ?? '',
      processDefinitionKey: selectedProcess?.id ?? '',
      version: 'latest',
    },
    {
      enabled: match !== null && selectedProcess !== null,
      refetchOnReconnect: false,
      refetchOnWindowFocus: false,
    },
  );
  const {mutateAsync: startProcess} = useStartProcess({
    onSuccess(data) {
      tracking.track({
        eventName: 'process-started',
      });
      setStartProcessStatus('active-tasks');

      newProcessInstance.setInstance({
        ...data,
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

  useEffect(() => {
    if (match !== null) {
      setSelectedProcess((currentProcess) => {
        return (
          data?.processes.find(
            (process) => process.bpmnProcessId === match.params.bpmnProcessId,
          ) ?? currentProcess
        );
      });
    }
  }, [match, data]);

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

  useEffect(() => {
    if (match === null || isLoading) {
      return;
    }

    const {bpmnProcessId = null} = match.params;

    if (
      data?.processes.find(
        (process) => process.bpmnProcessId === bpmnProcessId,
      ) === undefined
    ) {
      notificationsStore.displayNotification({
        isDismissable: false,
        kind: 'error',
        title:
          bpmnProcessId === null
            ? t('processesStartFormNotFound')
            : t('processesProcessNoFormOrNotExistError', {bpmnProcessId}),
      });
      navigate({
        ...location,
        pathname: `/${pages.processes()}`,
      });
    }
  }, [match, data, isLoading, navigate, location, t]);

  useEffect(() => {
    if (
      searchParams.get('tenantId') === null &&
      initialTenantId !== null &&
      getClientConfig().isMultiTenancyEnabled
    ) {
      searchParams.set('tenantId', initialTenantId);
      setSearchParams(searchParams, {replace: true});
    }
  }, [initialTenantId, searchParams, setSearchParams]);

  const [previousSearchParams, setPreviousSearchParams] =
    useState(searchParams);

  if (searchParams !== previousSearchParams) {
    setPreviousSearchParams(searchParams);
    const newValue = searchParams.get('search') ?? '';
    if (newValue !== searchValue) {
      setSearchValue(newValue);
    }
  }

  return (
    <main className={cn('cds--content', styles.splitPane)}>
      <NewProcessInstanceTasksPolling />

      <ProcessesList
        processes={data?.processes ?? []}
        searchValue={searchValue}
        isLoading={isLoading}
        isFiltered={isFiltered}
        onSearch={(event) => {
          setSearchValue(event.target.value);
          debouncedNavigate(searchParams, {
            name: 'search',
            value: event.target.value,
          });
        }}
        initialTenant={currentUser?.tenants.find(({tenantId}) =>
          [
            searchParams.get('tenantId') ?? undefined,
            getStateLocally('tenantId'),
          ]
            .filter((tenantId) => tenantId !== undefined)
            .includes(tenantId),
        )}
        onTenantChange={(tenant) => {
          updateSearchParams(searchParams, {
            name: 'tenantId',
            value: tenant,
          });
          storeStateLocally('tenantId', tenant);
        }}
        startFormFilterValue={startFormFilter}
        onStartFormFilterChange={(value) =>
          debouncedNavigate(searchParams, {
            name: 'hasStartForm',
            value: value.searchParamValue ?? '',
          })
        }
        isStartButtonDisabled={instance !== null}
        getUniqueId={(process) => process.id}
        onStartProcess={(process: Process) => async () => {
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
              pathname: pages.internalStartProcessFromForm(bpmnProcessId),
            });
          }
        }}
        onStartProcessError={(process: Process) => () => {
          setSelectedProcess(null);
          const displayName = getProcessDisplayName(process);
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
        onStartProcessSuccess={() => {
          setSelectedProcess(null);
          setStartProcessStatus('inactive');
        }}
        selectedTenantId={selectedTenantId}
        getStartProcessStatus={(process: Process) =>
          selectedProcess?.bpmnProcessId === process.bpmnProcessId
            ? startProcessStatus
            : 'inactive'
        }
      />

      <FormModal
        processDisplayName={
          selectedProcess === null ? '' : getProcessDisplayName(selectedProcess)
        }
        schema={formQueryResult.data?.schema ?? null}
        fetchStatus={formQueryResult.fetchStatus}
        status={formQueryResult.status}
        isOpen={match !== null}
        onClose={() => {
          navigate({
            ...location,
            pathname: pages.processes(),
          });
        }}
        onSubmit={async (variables) => {
          if (selectedProcess === null) {
            return;
          }

          const {bpmnProcessId} = selectedProcess;

          await startProcess({
            bpmnProcessId,
            variables,
            tenantId: selectedTenantId,
          });
          navigate({
            ...location,
            pathname: pages.processes(),
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
        tenantId={selectedTenantId}
      />

      <FirstTimeModal />
    </main>
  );
});

Processes.displayName = 'Processes';

export {Processes as Component};
