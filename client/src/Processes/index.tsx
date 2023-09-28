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
import {
  IS_MULTI_TENANCY_ENABLED,
  IS_PROCESS_INSTANCES_ENABLED,
} from 'modules/featureFlags';
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
  const location = useLocation();
  const navigate = useNavigate();
  const searchParams = new URLSearchParams(location.search);
  const {data, error, isInitialLoading} = useProcesses({
    query: getParam('search', searchParams),
    tenantId: getParam('tenantId', searchParams),
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
  const initialTenantId = useRef(getStateLocally('tenantId')).current;
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
        {IS_MULTI_TENANCY_ENABLED &&
        window.clientConfig?.isMultiTenancyEnabled ? (
          <MultiTenancyContainer>
            <Dropdown<CurrentUser['tenants'][0]>
              key={`tenant-dropdown-${currentUser?.tenants.length ?? 0}`}
              id="tenantId"
              items={currentUser?.tenants ?? []}
              itemToString={(item) => (item ? item.name : '')}
              label="Tenant"
              titleText="Tenant"
              initialSelectedItem={currentUser?.tenants.find(({id}) =>
                [
                  getParam('tenantId', searchParams),
                  getStateLocally('tenantId'),
                ].includes(id),
              )}
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
              disabled={isInitialLoading || currentUser === undefined}
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
                  href="https://docs.camunda.io/"
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
                    key={process.bpmnProcessId}
                    isFirst={idx === 0}
                    isStartButtonDisabled={
                      (instance !== null &&
                        instance.id !== process.bpmnProcessId) ||
                      !hasPermission
                    }
                    data-testid="process-tile"
                    tenantId={getParam('tenantId', searchParams)}
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
