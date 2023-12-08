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
import {useLocation, useNavigate, Navigate} from 'react-router-dom';
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
import {CurrentUser} from 'modules/types';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';

function getParam(param: 'search' | 'tenantId', params: URLSearchParams) {
  return params.get(param) ?? undefined;
}

const Processes: React.FC = observer(() => {
  const {instance} = newProcessInstance;
  const {hasPermission} = usePermissions(['write']);
  const {data: currentUser} = useCurrentUser();
  const hasMultipleTenants = (currentUser?.tenants.length ?? 0) > 1;
  const defaultTenant = currentUser?.tenants[0];
  const location = useLocation();
  const navigate = useNavigate();
  const searchParams = new URLSearchParams(location.search);
  const selectedTenantId = hasMultipleTenants
    ? getParam('tenantId', searchParams) ?? defaultTenant?.id
    : defaultTenant?.id;
  const {data, error, isInitialLoading} = useProcesses({
    query: getParam('search', searchParams),
    tenantId: selectedTenantId,
  });
  function navigateSearchParams(params: {
    name: 'search' | 'tenantId';
    value: string;
    currentQueryString: string;
  }) {
    const {name, value, currentQueryString} = params;
    const searchParams = new URLSearchParams(currentQueryString);

    if (value) {
      searchParams.set(name, value);
    } else {
      searchParams.delete(name);
    }

    navigate({
      search: searchParams.toString(),
    });
  }
  const debouncedNavigate = useRef(debounce(navigateSearchParams, 500)).current;
  const initialTenantId = useRef(
    defaultTenant?.id ?? getStateLocally('tenantId'),
  ).current;
  const [searchValue, setSearchValue] = useState(
    getParam('search', searchParams) ?? '',
  );
  const isFiltered = data?.query !== undefined && data.query !== '';
  const processes = data?.processes ?? [];

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

  if (
    getParam('tenantId', searchParams) === undefined &&
    initialTenantId !== null
  ) {
    const newSearchParams = new URLSearchParams(location.search);

    newSearchParams.set('tenantId', initialTenantId);

    return (
      <Navigate
        to={{
          search: newSearchParams.toString(),
        }}
        replace
      />
    );
  }

  const processSearchProps: React.ComponentProps<typeof Search> = {
    size: 'md',
    placeholder: 'Search processes',
    labelText: 'Search processes',
    closeButtonLabelText: 'Clear search processes',
    value: searchValue,
    onChange: (event) => {
      setSearchValue(event.target.value);
      debouncedNavigate({
        name: 'search',
        value: event.target.value,
        currentQueryString: location.search,
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
        {window.clientConfig?.isMultiTenancyEnabled &&
        currentUser !== undefined &&
        hasMultipleTenants ? (
          <MultiTenancyContainer>
            <Dropdown<CurrentUser['tenants'][0]>
              key={`tenant-dropdown-${currentUser?.tenants.length ?? 0}`}
              id="tenantId"
              items={currentUser?.tenants ?? []}
              itemToString={(item) => (item ? `${item.name} - ${item.id}` : '')}
              label="Tenant"
              titleText="Tenant"
              initialSelectedItem={
                currentUser?.tenants.find(({id}) =>
                  [
                    getParam('tenantId', searchParams),
                    getStateLocally('tenantId'),
                  ]
                    .filter((tenantId) => tenantId !== undefined)
                    .includes(id),
                ) ?? defaultTenant
              }
              onChange={(event) => {
                const id = event.selectedItem?.id;

                if (!id) {
                  return;
                }

                navigateSearchParams({
                  name: 'tenantId',
                  value: id,
                  currentQueryString: location.search,
                });
                storeStateLocally('tenantId', id);
              }}
            />
            <Search {...processSearchProps} />
          </MultiTenancyContainer>
        ) : (
          <SearchContainer>
            <Search {...processSearchProps} />
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

export {Processes};
