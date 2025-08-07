/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  Column,
  Dropdown,
  Grid,
  Layer,
  Link,
  Search,
  SkeletonPlaceholder,
  Stack,
  type InlineLoadingProps,
} from '@carbon/react';
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
import {newProcessInstance} from 'common/processes/newProcessInstance';
import {FirstTimeModal} from 'common/processes/FirstTimeModal';
import {notificationsStore} from 'common/notifications/notifications.store';
import {logger} from 'common/utils/logger';
import {NewProcessInstanceTasksPolling} from './NewProcessInstanceTasksPolling';
import {tracking} from 'common/tracking';
import {useProcessDefinitions} from 'v2/api/useProcessDefinitions.query';
import {useCurrentUser} from 'common/api/useCurrentUser.query';
import styles from './styles.module.scss';
import cn from 'classnames';
import {getClientConfig} from 'common/config/getClientConfig';
import {useCreateProcessInstance} from 'v2/api/useCreateProcessInstance.mutation';
import {C3EmptyState} from '@camunda/camunda-composite-components';
import EmptyMessageImage from 'common/processes/empty-message-image.svg';
import {ProcessTile} from 'common/processes/ProcessTile';
import {getMultiModeProcessDisplayName} from 'common/processes/getMultiModeProcessDisplayName';
import type {ProcessDefinition} from '@vzeta/camunda-api-zod-schemas/8.8';
import {Pagination} from './Pagination';
import {
  START_FORM_FILTER_OPTIONS,
  type FilterOption,
} from 'common/processes/constants';
import {MultitenancyDropdown} from 'common/multitenancy/MultitenancyDropdown';
import {getStateLocally, storeStateLocally} from 'common/local-storage';
import {pages} from 'common/routing';
import {FormModal} from 'common/processes/FormModal';
import {useUploadDocuments} from 'common/api/useUploadDocuments.mutation';
import {useProcessStartForm} from 'v2/api/useProcessStartForm.query';
import {useIsMultitenancyEnabled} from 'common/multitenancy/useIsMultitenancyEnabled';
import {tryParseJSON} from 'v2/features/tasks/details/tryParseJSON';

const PAGE_SIZE = 12;

const FilterDropdown: React.FC<{
  items: FilterOption[];
  selected?: FilterOption;
  onChange?: (option: FilterOption) => void;
}> = ({items, selected, onChange}) => {
  const {t} = useTranslation();

  return (
    <Dropdown
      id="process-filters"
      data-testid="process-filters"
      className={styles.dropdown}
      hideLabel
      selectedItem={selected}
      titleText={t('processesFilterDropdownLabel')}
      label={t('processesFilterDropdownLabel')}
      items={items}
      itemToString={(item) => (item ? t(item.textKey) : '')}
      onChange={(data) => {
        if (data.selectedItem && onChange) {
          onChange(data.selectedItem);
        }
      }}
    />
  );
};

type InlineLoadingStatus = NonNullable<InlineLoadingProps['status']>;

type LoadingStatus = InlineLoadingStatus | 'active-tasks';

function getIsStartedByForm(searchParamValue: string | undefined) {
  if (searchParamValue === undefined) {
    return undefined;
  }
  return searchParamValue === 'yes';
}

const ProcessesTab: React.FC = observer(() => {
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
  const [paginationParams, setPaginationParams] = useState<{
    currentPage: number;
    after?: string;
    before?: string;
  }>({currentPage: 1});

  const searchParam = searchParams.get('search');
  const {data, error, isLoading} = useProcessDefinitions(
    {
      filter: {
        tenantId: selectedTenantId,
        processDefinitionId:
          searchParam !== null
            ? {
                $like: `*${searchParam}*`,
              }
            : undefined,
        hasStartForm: getIsStartedByForm(startFormFilter.searchParamValue),
      },
      page: {
        limit: PAGE_SIZE,
        after: paginationParams.after,
        before: paginationParams.before,
      },
    },
    {
      refetchInterval: 5000,
    },
  );
  const debouncedNavigate = useRef(debounce(updateSearchParams, 500)).current;
  const initialTenantId = useRef(
    defaultTenant?.tenantId ?? getStateLocally('tenantId'),
  ).current;
  const [searchValue, setSearchValue] = useState(
    searchParams.get('search') ?? '',
  );
  const isFiltered =
    searchParams.get('search') !== null && searchParams.get('search') !== '';
  const match = useMatch(
    pages.internalStartProcessFromForm(':processDefinitionKey'),
  );
  const [selectedProcess, setSelectedProcess] =
    useState<ProcessDefinition | null>(null);
  const {mutateAsync: uploadDocuments} = useUploadDocuments();
  const formQueryResult = useProcessStartForm(
    {
      processDefinitionKey: selectedProcess?.processDefinitionKey ?? '',
    },
    {
      enabled: match !== null && selectedProcess !== null,
      refetchOnReconnect: false,
      refetchOnWindowFocus: false,
    },
  );
  const {mutateAsync: createProcessInstance} = useCreateProcessInstance({
    onSuccess(data) {
      tracking.track({
        eventName: 'process-started',
      });
      setStartProcessStatus('active-tasks');

      newProcessInstance.setInstance({
        id: data.processInstanceKey,
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
  const processSearchProps: React.ComponentProps<typeof Search> = {
    size: 'md',
    placeholder: t('processesFilterFieldLabel'),
    labelText: t('processesFilterFieldLabel'),
    closeButtonLabelText: t('processesClearFilterFieldButtonLabel'),
    value: searchValue,
    onChange: (event) => {
      setSearchValue(event.target.value);
      debouncedNavigate(searchParams, {
        name: 'search',
        value: event.target.value,
      });
    },
    disabled: isLoading,
  } as const;
  const startFormFilterDropdownProps: React.ComponentProps<
    typeof FilterDropdown
  > = {
    items: START_FORM_FILTER_OPTIONS,
    selected: startFormFilter,
    onChange: (value) =>
      debouncedNavigate(searchParams, {
        name: 'hasStartForm',
        value: value.searchParamValue ?? '',
      }),
  } as const;
  const initialTenant = currentUser?.tenants.find(({tenantId}) =>
    [searchParams.get('tenantId') ?? undefined, getStateLocally('tenantId')]
      .filter((tenantId) => tenantId !== undefined)
      .includes(tenantId),
  );
  const processes = data?.items ?? [];
  const totalItems = data?.page.totalItems ?? 0;
  const {isMultitenancyEnabled} = useIsMultitenancyEnabled();

  useEffect(() => {
    if (match !== null) {
      setSelectedProcess((currentProcess) => {
        return (
          data?.items.find(
            (process) =>
              process.processDefinitionKey ===
              match.params.processDefinitionKey,
          ) ?? currentProcess
        );
      });
    }
  }, [match, data]);

  useEffect(() => {
    if (match === null || isLoading) {
      return;
    }

    const {processDefinitionKey = null} = match.params;

    if (
      data?.items.find(
        (process) => process.processDefinitionKey === processDefinitionKey,
      ) === undefined
    ) {
      notificationsStore.displayNotification({
        isDismissable: false,
        kind: 'error',
        title:
          processDefinitionKey === null
            ? t('processesStartFormNotFound')
            : t('processesProcessNoFormOrNotExistError', {
                bpmnProcessId: processDefinitionKey,
              }),
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

  const handleNextPage = () => {
    if (data?.page.endCursor === undefined) {
      return;
    }
    const {endCursor} = data.page;

    setPaginationParams(({currentPage}) => ({
      currentPage: currentPage + 1,
      after: endCursor,
    }));
  };

  const handlePreviousPage = () => {
    if (data?.page.startCursor === undefined) {
      return;
    }
    const {startCursor} = data.page;
    setPaginationParams(({currentPage}) => ({
      currentPage: currentPage - 1,
      before: startCursor,
    }));
  };

  return (
    <main className={cn('cds--content', styles.splitPane)}>
      <NewProcessInstanceTasksPolling newInstance={newProcessInstance} />

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
              {isMultitenancyEnabled ? (
                <Grid narrow>
                  <Column
                    className={styles.searchFieldWrapper}
                    sm={4}
                    md={8}
                    lg={10}
                  >
                    <Search {...processSearchProps} />
                  </Column>
                  <Column
                    className={styles.searchFieldWrapper}
                    sm={2}
                    md={4}
                    lg={3}
                  >
                    <FilterDropdown {...startFormFilterDropdownProps} />
                  </Column>
                  <Column
                    className={styles.searchFieldWrapper}
                    sm={2}
                    md={4}
                    lg={2}
                  >
                    <MultitenancyDropdown
                      initialSelectedItem={initialTenant}
                      onChange={(tenant) => {
                        updateSearchParams(searchParams, {
                          name: 'tenantId',
                          value: tenant,
                        });
                        storeStateLocally('tenantId', tenant);
                      }}
                    />
                  </Column>
                </Grid>
              ) : (
                <Grid narrow>
                  <Column
                    className={styles.searchFieldWrapper}
                    sm={4}
                    md={5}
                    lg={10}
                  >
                    <Search {...processSearchProps} />
                  </Column>
                  <Column
                    className={styles.searchFieldWrapper}
                    sm={4}
                    md={3}
                    lg={5}
                  >
                    <FilterDropdown {...startFormFilterDropdownProps} />
                  </Column>
                </Grid>
              )}
            </Stack>
          </div>

          <div className={styles.processTilesContainer}>
            <div className={styles.processTilesContainerInner}>
              {!isLoading && processes.length === 0 ? (
                <Layer>
                  <C3EmptyState
                    icon={
                      isFiltered
                        ? undefined
                        : {path: EmptyMessageImage, altText: ''}
                    }
                    heading={
                      isFiltered
                        ? t('processesProcessNotFoundError')
                        : t('processesProcessNotPublishedError')
                    }
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
                          key={process.processDefinitionKey}
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
                              const {processDefinitionKey} = process;
                              if (!process.hasStartForm) {
                                setStartProcessStatus('active');
                                tracking.track({
                                  eventName: 'process-start-clicked',
                                });
                                try {
                                  await createProcessInstance({
                                    processDefinitionKey,
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
                                      processDefinitionKey,
                                    ),
                                });
                              }
                            }}
                            onStartProcessError={() => {
                              setSelectedProcess(null);
                              const displayName =
                                getMultiModeProcessDisplayName(process);
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
                              selectedProcess?.processDefinitionKey ===
                              process.processDefinitionKey
                                ? startProcessStatus
                                : 'inactive'
                            }
                          />
                        </Column>
                      ))}
                </Grid>
              )}
              {isLoading || totalItems <= PAGE_SIZE ? null : (
                <Pagination
                  totalPages={Math.ceil(totalItems / PAGE_SIZE)}
                  currentPage={paginationParams.currentPage}
                  onNextPage={handleNextPage}
                  onPreviousPage={handlePreviousPage}
                  className={styles.pagination}
                />
              )}
            </div>
          </div>
        </Stack>
      </div>

      <FormModal
        processDisplayName={
          selectedProcess === null
            ? ''
            : getMultiModeProcessDisplayName(selectedProcess)
        }
        schema={formQueryResult.data?.schema ?? null}
        fetchStatus={formQueryResult.fetchStatus}
        status={formQueryResult.status}
        isOpen={match !== null}
        onClose={() => {
          navigate({
            ...location,
            pathname: `/${pages.processes()}`,
          });
        }}
        onSubmit={async (variables) => {
          if (selectedProcess === null) {
            return;
          }

          const {processDefinitionKey} = selectedProcess;

          await createProcessInstance({
            processDefinitionKey,
            variables: variables.reduce(
              (acc, {name, value}) => ({
                ...acc,
                [name]: tryParseJSON(value),
              }),
              {},
            ),
            tenantId: selectedTenantId,
          });
          navigate({
            ...location,
            pathname: `/${pages.processes()}`,
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

ProcessesTab.displayName = 'Processes';

export {ProcessesTab as Component};
