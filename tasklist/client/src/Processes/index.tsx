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
import styles from './styles.module.scss';
import cn from 'classnames';

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
      className={styles.dropdown}
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

  const Filter = () => {
    return (
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
    );
  };

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
                    <h1>Processes</h1>
                    <p>
                      Browse and run processes published by your organization.
                    </p>
                  </Stack>
                </Column>
              </Grid>
              {isMultiTenancyVisible ? (
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
                    <Filter />
                  </Column>
                  <Column
                    className={styles.searchFieldWrapper}
                    sm={2}
                    md={4}
                    lg={2}
                  >
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
                    <Filter />
                  </Column>
                </Grid>
              )}
            </Stack>
          </div>

          <div className={styles.processTilesContainer}>
            <div className={styles.processTilesContainerInner}>
              {!isInitialLoading && processes.length === 0 ? (
                <Layer>
                  <C3EmptyState
                    icon={
                      isFiltered
                        ? undefined
                        : {path: EmptyMessageImage, altText: ''}
                    }
                    heading={
                      isFiltered
                        ? 'We could not find any process with that name'
                        : 'No published processes yet'
                    }
                    description={
                      <span data-testid="empty-message">
                        Contact your process administrator to publish processes
                        or learn how to publish processes{' '}
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
                </Layer>
              ) : (
                <Grid narrow as={Layer}>
                  {isInitialLoading
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
                              (instance !== null &&
                                instance.id !== process.bpmnProcessId) ||
                              !hasPermission
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

      {IS_PROCESS_INSTANCES_ENABLED ? (
        <aside className={styles.historyAside}>
          <History />
        </aside>
      ) : null}

      <FirstTimeModal />
    </main>
  );
});

Processes.displayName = 'Processes';

export {Processes as Component};
