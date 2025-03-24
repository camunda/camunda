/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  Search,
  Grid,
  Column,
  Stack,
  Link,
  Layer,
  Dropdown,
  SkeletonPlaceholder,
} from '@carbon/react';
import {ProcessTile} from './ProcessTile';
import debounce from 'lodash/debounce';
import {
  useLocation,
  useNavigate,
  useMatch,
  useSearchParams,
} from 'react-router-dom';
import {useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {C3EmptyState} from '@camunda/camunda-composite-components';
import EmptyMessageImage from './empty-message-image.svg';
import {observer} from 'mobx-react-lite';
import {newProcessInstance} from 'v1/newProcessInstance';
import {FirstTimeModal} from './FirstTimeModal';
import {notificationsStore} from 'common/notifications/notifications.store';
import {logger} from 'common/utils/logger';
import {NewProcessInstanceTasksPolling} from './NewProcessInstanceTasksPolling';
import {tracking} from 'common/tracking';
import {useProcesses} from 'v1/api/useProcesses.query';
import {useCurrentUser} from 'common/api/useCurrentUser.query';
import {getStateLocally, storeStateLocally} from 'common/local-storage';
import {pages} from 'common/routing';
import {useIsMultitenancyEnabled} from 'common/multitenancy/useIsMultitenancyEnabled';
import {MultitenancyDropdown} from 'common/multitenancy/MultitenancyDropdown';
import styles from './styles.module.scss';
import cn from 'classnames';
import {getClientConfig} from 'common/config/getClientConfig';

type UseProcessesFilterParams = Omit<
  Parameters<typeof useProcesses>[0],
  'query' | 'tenantId'
>;

type FilterOption = {
  id: string;
  textKey: string;
  searchParamValue: 'yes' | 'no' | undefined;
  params: UseProcessesFilterParams;
};

const START_FORM_FILTER_OPTIONS: FilterOption[] = [
  {
    id: 'ignore',
    textKey: 'processFiltersAllProcesses',
    searchParamValue: undefined,
    params: {
      isStartedByForm: undefined,
    },
  },
  {
    id: 'yes',
    textKey: 'processesFormFilterRequiresForm',
    searchParamValue: 'yes',
    params: {
      isStartedByForm: true,
    },
  },
  {
    id: 'no',
    textKey: 'processesFormFilterRequiresNoForm',
    searchParamValue: 'no',
    params: {
      isStartedByForm: false,
    },
  },
];

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

const Processes: React.FC = observer(() => {
  const {t} = useTranslation();
  const {instance} = newProcessInstance;
  const {data: currentUser} = useCurrentUser();
  const {isMultitenancyEnabled} = useIsMultitenancyEnabled();
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
          (opt) => opt.searchParamValue === startFormFilterSearchParam,
        )
      : undefined) ?? START_FORM_FILTER_OPTIONS[0];
  const {data, error, isLoading} = useProcesses(
    {
      query: searchParams.get('search') ?? undefined,
      tenantId: selectedTenantId,
      ...(startFormFilter?.params ?? {}),
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
  const processes = data?.processes ?? [];
  const match = useMatch(pages.internalStartProcessFromForm());

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
  const filterDropdownProps: React.ComponentProps<typeof FilterDropdown> = {
    items: START_FORM_FILTER_OPTIONS,
    selected: startFormFilter,
    onChange: (value) =>
      debouncedNavigate(searchParams, {
        name: 'hasStartForm',
        value: value.searchParamValue ?? '',
      }),
  } as const;

  return (
    <main className={cn('cds--content', styles.splitPane)}>
      <div className={styles.container}>
        <NewProcessInstanceTasksPolling />
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
                    <FilterDropdown {...filterDropdownProps} />
                  </Column>
                  <Column
                    className={styles.searchFieldWrapper}
                    sm={2}
                    md={4}
                    lg={2}
                  >
                    <MultitenancyDropdown
                      initialSelectedItem={currentUser?.tenants.find(
                        ({tenantId}) =>
                          [
                            searchParams.get('tenantId') ?? undefined,
                            getStateLocally('tenantId'),
                          ]
                            .filter((tenantId) => tenantId !== undefined)
                            .includes(tenantId),
                      )}
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
                    <FilterDropdown {...filterDropdownProps} />
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
                          key={process.id}
                        >
                          <ProcessTile
                            process={process}
                            isFirst={idx === 0}
                            isStartButtonDisabled={
                              instance !== null &&
                              instance.id !== process.bpmnProcessId
                            }
                            data-testid="process-tile"
                            tenantId={selectedTenantId}
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

Processes.displayName = 'Processes';

export {Processes as Component};
