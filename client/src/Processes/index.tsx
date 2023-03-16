/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useQuery, NetworkStatus} from '@apollo/client';
import {Search, Stack, Link} from '@carbon/react';
import {
  GetProcesses,
  GetProcessesVariables,
  GET_PROCESSES,
} from 'modules/queries/get-processes';
import {ProcessTile} from './ProcessTile';
import {
  Container,
  SearchContainer,
  ProcessesContainer,
  TileSkeleton,
  ActionableNotification,
  NotificationContainer,
} from './styled';
import debounce from 'lodash/debounce';
import {useLocation, useNavigate} from 'react-router-dom';
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

const Processes: React.FC = observer(() => {
  const {instance} = newProcessInstance;
  const location = useLocation();
  const navigate = useNavigate();
  const searchParam =
    new URLSearchParams(location.search).get('search') ?? undefined;
  const {data, previousData, networkStatus, variables, error} = useQuery<
    GetProcesses,
    GetProcessesVariables
  >(GET_PROCESSES, {
    variables: {
      search: searchParam,
    },
    pollInterval: 5000,
    onCompleted: () => {
      tracking.track({
        eventName: 'processes-loaded',
        filter: variables?.search ?? '',
        count: data?.processes.length ?? 0,
      });
    },
  });
  const isExecutingFirstFetch = networkStatus === NetworkStatus.loading;
  const debouncedNavigate = useRef(
    debounce(function navigateSearchParams(
      search: string,
      currentQueryString: string,
    ) {
      const searchParams = new URLSearchParams(currentQueryString);

      if (search) {
        searchParams.set('search', search);
      } else {
        searchParams.delete('search');
      }

      navigate({
        search: searchParams.toString(),
      });
    },
    500),
  ).current;
  const [searchValue, setSearchValue] = useState(searchParam ?? '');
  const processes = (data ?? previousData)?.processes ?? [];
  const isFiltered = Boolean(variables?.search);

  useEffect(() => {
    if (error !== undefined) {
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

  return (
    <>
      <NewProcessInstanceTasksPolling />
      <Stack as={Container} gap={6} className="cds--content">
        <NotificationContainer>
          <ActionableNotification
            actionButtonLabel="Give us your feedback"
            title="This feature is only available for alpha releases"
            subtitle="try our new feature and give us your feedback!"
            hideCloseButton
            kind="info"
            role="alert"
            lowContrast
            inline
            onActionButtonClick={() => {
              tracking.track({
                eventName: 'early-access-feedback-link-clicked',
                feature: 'start-process',
              });
              window.open(
                'https://forum.camunda.io/',
                '_blank',
                'noopener noreferrer',
              );
            }}
          />
        </NotificationContainer>

        <SearchContainer>
          <Search
            size="md"
            placeholder="Search processes"
            labelText="Search processes"
            closeButtonLabelText="Clear search processes"
            value={searchValue}
            onChange={(event) => {
              setSearchValue(event.target.value);
              debouncedNavigate(event.target.value, location.search);
            }}
            disabled={isExecutingFirstFetch}
          />
        </SearchContainer>
        {!isExecutingFirstFetch && processes.length === 0 ? (
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
            {isExecutingFirstFetch
              ? Array.from({length: 5}).map((_, index) => (
                  <TileSkeleton key={index} data-testid="process-skeleton" />
                ))
              : processes.map(({name, processDefinitionId}, idx) => (
                  <ProcessTile
                    name={name}
                    processDefinitionId={processDefinitionId}
                    key={processDefinitionId}
                    isFirst={idx === 0}
                    isStartButtonDisabled={
                      instance !== null && instance.id !== processDefinitionId
                    }
                    data-testid="process-tile"
                  />
                ))}
          </ProcessesContainer>
        )}
      </Stack>
      <FirstTimeModal />
    </>
  );
});

export {Processes};
