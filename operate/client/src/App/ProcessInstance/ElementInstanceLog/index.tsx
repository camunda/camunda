/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useEffect, useState} from 'react';
import {
  Container,
  PanelHeader,
  ErrorMessage,
  PanelBody,
  HiddenTreeWrapper,
} from './styled';
import {TimeStampPill} from './TimeStampPill';
import {modificationsStore} from 'modules/stores/modifications';
import {Stack} from '@carbon/react';
import {Skeleton} from './Skeleton';
import {ExecutionCountToggle} from './ExecutionCountToggle';
import {ElementInstancesTree} from './ElementInstancesTree';
import {SearchInput} from './SearchInput';
import {FilteredElementInstancesList} from './FilteredElementInstancesList';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {isRequestError} from 'modules/request';
import {HTTP_STATUS_FORBIDDEN} from 'modules/constants/statusCode';
import {getForbiddenPermissionsError} from 'modules/constants/permissions';

type LayoutProps = {
  children: React.ReactNode;
  isPanel: boolean;
  searchInput?: React.ReactNode;
};

const Layout: React.FC<LayoutProps> = observer(
  ({children, isPanel, searchInput}) => {
    if (!isPanel) {
      return children;
    }

    return (
      <Container data-testid="instance-history">
        <PanelHeader title="Instance History" size="sm">
          {!modificationsStore.isModificationModeEnabled && (
            <Stack orientation="horizontal" gap={5}>
              <TimeStampPill />
              <ExecutionCountToggle />
            </Stack>
          )}
        </PanelHeader>
        {!modificationsStore.isModificationModeEnabled && searchInput}
        {children}
      </Container>
    );
  },
);

const INSTANCE_HISTORY_FORBIDDEN = getForbiddenPermissionsError(
  'Instance History',
  'this instance history',
);

const ElementInstanceLog: React.FC<{isPanel?: boolean}> = observer(
  ({isPanel = false}) => {
    const {
      data: processInstance,
      status: processInstanceStatus,
      error: processInstanceError,
    } = useProcessInstance();
    const {
      data: businessObjects,
      status: businessObjectsStatus,
      error: businessObjectsError,
    } = useBusinessObjects();

    const [searchText, setSearchText] = useState('');
    const [debouncedSearchText, setDebouncedSearchText] = useState('');

    const isModificationModeEnabled =
      modificationsStore.isModificationModeEnabled;

    // Clear search when modification mode is enabled (modification mode is
    // incompatible with search).
    useEffect(() => {
      if (isModificationModeEnabled) {
        setSearchText('');
      }
    }, [isModificationModeEnabled]);

    // Debounce the search text by 300ms before triggering the API query.
    useEffect(() => {
      const timer = setTimeout(() => {
        setDebouncedSearchText(searchText);
      }, 300);
      return () => clearTimeout(timer);
    }, [searchText]);

    const isFiltered = debouncedSearchText.length > 0;

    const searchInputElement = (
      <SearchInput
        value={searchText}
        onChange={setSearchText}
        onClear={() => setSearchText('')}
      />
    );

    if ([processInstanceStatus, businessObjectsStatus].includes('pending')) {
      return (
        <Layout isPanel={isPanel} searchInput={searchInputElement}>
          <Skeleton />
        </Layout>
      );
    }

    if ([processInstanceStatus, businessObjectsStatus].includes('error')) {
      const isForbidden =
        (isRequestError(processInstanceError) &&
          processInstanceError?.response?.status === HTTP_STATUS_FORBIDDEN) ||
        (isRequestError(businessObjectsError) &&
          businessObjectsError?.response?.status === HTTP_STATUS_FORBIDDEN);

      return (
        <Layout isPanel={isPanel} searchInput={searchInputElement}>
          <ErrorMessage
            message={
              isForbidden
                ? INSTANCE_HISTORY_FORBIDDEN.message
                : 'Instance History could not be fetched'
            }
            additionalInfo={
              isForbidden
                ? INSTANCE_HISTORY_FORBIDDEN.additionalInfo
                : 'Refresh the page to try again'
            }
          />
        </Layout>
      );
    }

    return (
      <Layout isPanel={isPanel} searchInput={searchInputElement}>
        <PanelBody>
          <HiddenTreeWrapper $hidden={isFiltered}>
            <ElementInstancesTree
              processInstance={processInstance!}
              businessObjects={businessObjects!}
              errorMessage={
                <ErrorMessage message="Instance History could not be fetched" />
              }
            />
          </HiddenTreeWrapper>
          {isFiltered && (
            <FilteredElementInstancesList
              searchText={debouncedSearchText}
              processInstanceKey={processInstance!.processInstanceKey}
              businessObjects={businessObjects!}
            />
          )}
        </PanelBody>
      </Layout>
    );
  },
);

export {ElementInstanceLog};
