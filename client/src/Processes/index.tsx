/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Search, Stack, Link} from '@carbon/react';
import {ProcessTile} from './ProcessTile';
import {
  Container,
  Content,
  SearchContainer,
  ProcessesContainer,
  TileSkeleton,
  Aside,
  MultiTenancyContainer,
  Dropdown,
} from './styled';
import debounce from 'lodash/debounce';
import {
  useLocation,
  useNavigate,
  useMatch,
  useSearchParams,
} from 'react-router-dom';
import {useEffect, useRef, useState} from 'react';
import {C3EmptyState} from '@camunda/camunda-composite-components';
import EmptyMessageImage from './empty-message-image.svg';
import {observer} from 'mobx-react-lite';
import {newProcessInstance} from 'modules/stores/newProcessInstance';
import {FirstTimeModal} from './FirstTimeModal';
import {notificationsStore} from 'modules/stores/notifications';
import {logger} from 'modules/utils/logger';
import {NewProcessInstanceTasksPolling} from './NewProcessInstanceTasksPolling';
import {tracking} from 'modules/tracking';
import {useProcesses} from 'modules/queries/useProcesses';
import {usePermissions} from 'modules/hooks/usePermissions';
import {History} from './History';
import {IS_PROCESS_INSTANCES_ENABLED} from 'modules/featureFlags';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {pages} from 'modules/routing';
import {useMultiTenancyDropdown} from 'modules/components/useMultiTenancyDropdown';
import {MultiTenancyDropdown} from 'modules/components/useMultiTenancyDropdown/MultiTenancyDropdown';

type UseProcessesFilterParams = Omit<
  Parameters<typeof useProcesses>[0],
  'query' | 'tenantId'
>;

type FilterOption = {
  id: string;
  text: string;
  searchParamValue: 'yes' | 'no' | undefined;
  params: UseProcessesFilterParams;
};

const START_FORM_FILTER_OPTIONS: FilterOption[] = [
  {
    id: 'ignore',
    text: 'All Processes',
    searchParamValue: undefined,
    params: {
      isStartedByForm: undefined,
    },
  },
  {
    id: 'yes',
    text: 'Requires form input to start',
    searchParamValue: 'yes',
    params: {
      isStartedByForm: true,
    },
  },
  {
    id: 'no',
    text: 'Does not require form input to start',
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
  return (
    <Dropdown
      id="process-filters"
      data-testid="process-filters"
      hideLabel
      selectedItem={selected}
      titleText="Filter processes"
      label="Filter processes"
      items={items}
      itemToString={(item) => (item ? item.text : '')}
      onChange={(data) => {
        if (data.selectedItem && onChange) {
          onChange(data.selectedItem);
        }
      }}
    />
  );
};

const Processes: React.FC = observer(() => {
  const {instance} = newProcessInstance;
  const {hasPermission} = usePermissions(['write']);
  const {data: currentUser} = useCurrentUser();
  const {isMultiTenancyVisible} = useMultiTenancyDropdown();
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
    ? searchParams.get('tenantId') ?? defaultTenant?.id
    : defaultTenant?.id;
  const startFormFilterSearchParam =
    searchParams.get('hasStartForm') ?? undefined;
  const startFormFilter =
    (startFormFilterSearchParam
      ? START_FORM_FILTER_OPTIONS.find(
          (opt) => opt.searchParamValue === startFormFilterSearchParam,
        )
      : undefined) ?? START_FORM_FILTER_OPTIONS[0];
  const {data, error, isInitialLoading} = useProcesses(
    {
      query: searchParams.get('search') ?? undefined,
      tenantId: selectedTenantId,
      ...(startFormFilter?.params ?? {}),
    },
    {
      refetchInterval: 5000,
      keepPreviousData: true,
    },
  );
  const debouncedNavigate = useRef(debounce(updateSearchParams, 500)).current;
  const initialTenantId = useRef(
    defaultTenant?.id ?? getStateLocally('tenantId'),
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
        title: 'Processes could not be fetched',
      });
      logger.error(error);
    }
  }, [error]);

  useEffect(() => {
    if (match === null || isInitialLoading) {
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
            ? 'Process does not exist or has no start form'
            : `Process ${bpmnProcessId} does not exist or has no start form`,
      });
      navigate({
        ...location,
        pathname: `/${pages.processes()}`,
      });
    }
  }, [match, data, isInitialLoading, navigate, location]);

  useEffect(() => {
    if (searchParams.get('tenantId') === null && initialTenantId !== null) {
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
    placeholder: 'Search processes',
    labelText: 'Search processes',
    closeButtonLabelText: 'Clear search processes',
    value: searchValue,
    onChange: (event) => {
      setSearchValue(event.target.value);
      debouncedNavigate(searchParams, {
        name: 'search',
        value: event.target.value,
      });
    },
    disabled: isInitialLoading,
  } as const;

  return (
    <Container
      className="cds--content"
      $isSingleColumn={!IS_PROCESS_INSTANCES_ENABLED}
    >
      <NewProcessInstanceTasksPolling />
      <Stack as={Content} gap={6}>
        {isMultiTenancyVisible ? (
          <MultiTenancyContainer>
            <Search {...processSearchProps} />
            <FilterDropdown
              items={START_FORM_FILTER_OPTIONS}
              selected={startFormFilter}
              onChange={(value) =>
                updateSearchParams(searchParams, {
                  name: 'hasStartForm',
                  value: value.searchParamValue ?? '',
                })
              }
            />
            <MultiTenancyDropdown
              initialSelectedItem={currentUser?.tenants.find(({id}) =>
                [
                  searchParams.get('tenantId') ?? undefined,
                  getStateLocally('tenantId'),
                ]
                  .filter((tenantId) => tenantId !== undefined)
                  .includes(id),
              )}
              onChange={(tenant) => {
                updateSearchParams(searchParams, {
                  name: 'tenantId',
                  value: tenant,
                });
                storeStateLocally('tenantId', tenant);
              }}
            />
          </MultiTenancyContainer>
        ) : (
          <SearchContainer>
            <Search {...processSearchProps} />
            <FilterDropdown
              items={START_FORM_FILTER_OPTIONS}
              selected={startFormFilter}
              onChange={(value) =>
                updateSearchParams(searchParams, {
                  name: 'hasStartForm',
                  value: value.searchParamValue ?? '',
                })
              }
            />
          </SearchContainer>
        )}
        {!isInitialLoading && processes.length === 0 ? (
          <C3EmptyState
            icon={
              isFiltered ? undefined : {path: EmptyMessageImage, altText: ''}
            }
            heading={
              isFiltered
                ? 'We could not find any process with that name'
                : 'No published processes yet'
            }
            description={
              <span data-testid="empty-message">
                Contact your process administrator to publish processes or learn
                how to publish processes{' '}
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
                  here
                </Link>
              </span>
            }
          />
        ) : (
          <ProcessesContainer>
            {isInitialLoading
              ? Array.from({length: 5}).map((_, index) => (
                  <TileSkeleton key={index} data-testid="process-skeleton" />
                ))
              : processes.map((process, idx) => (
                  <ProcessTile
                    process={process}
                    key={process.id}
                    isFirst={idx === 0}
                    isStartButtonDisabled={
                      (instance !== null &&
                        instance.id !== process.bpmnProcessId) ||
                      !hasPermission
                    }
                    data-testid="process-tile"
                    tenantId={selectedTenantId}
                  />
                ))}
          </ProcessesContainer>
        )}
      </Stack>

      {IS_PROCESS_INSTANCES_ENABLED ? (
        <Aside>
          <History />
        </Aside>
      ) : null}
      <FirstTimeModal />
    </Container>
  );
});

Processes.displayName = 'Processes';

export {Processes as Component};
