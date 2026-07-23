/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useSearchParams} from 'react-router-dom';
import {ErrorBoundary} from 'react-error-boundary';
import {Container, PanelHeader, ErrorMessage, PanelBody} from './styled';
import {TimeStampPill} from './TimeStampPill';
import {modificationsStore} from 'modules/stores/modifications';
import {Stack} from '@carbon/react';
import {Skeleton} from './Skeleton';
import {ExecutionCountToggle} from './ExecutionCountToggle';
import {ElementInstancesTree} from './ElementInstancesTree';
import {FilteredElementInstancesList} from './FilteredElementInstancesList';
import {SearchForm, SEARCH_PARAM_KEY} from './SearchForm';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {isRequestError} from 'modules/request';
import {HTTP_STATUS_FORBIDDEN} from 'modules/constants/statusCode';
import {getForbiddenPermissionsError} from 'modules/constants/permissions';

type LayoutProps = {
  children: React.ReactNode;
  isPanel: boolean;
  showHeader: boolean;
  searchInput?: React.ReactNode;
};

const Layout: React.FC<LayoutProps> = observer(
  ({children, isPanel, showHeader, searchInput}) => {
    if (!isPanel) {
      return children;
    }

    return (
      <Container data-testid="instance-history">
        {showHeader && (
          <PanelHeader title="Instance History" size="sm">
            {!modificationsStore.isModificationModeEnabled && (
              <Stack orientation="horizontal" gap={5}>
                <TimeStampPill />
                <ExecutionCountToggle />
              </Stack>
            )}
          </PanelHeader>
        )}
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

const ElementInstanceLog: React.FC<{isPanel?: boolean; showHeader?: boolean}> =
  observer(({isPanel = false, showHeader = true}) => {
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

    const [searchParams] = useSearchParams();

    const isModificationModeEnabled =
      modificationsStore.isModificationModeEnabled;

    // The submitted search value lives in the URL so the filtered view is
    // shareable. Reads the param directly so a fresh page load with
    // ?elementSearch=foo shows the filtered list immediately.
    const submittedSearch = searchParams.get(SEARCH_PARAM_KEY) ?? '';
    const isFiltered = submittedSearch.trim().length > 0;

    if ([processInstanceStatus, businessObjectsStatus].includes('pending')) {
      return (
        <Layout isPanel={isPanel} showHeader={showHeader}>
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
        <Layout isPanel={isPanel} showHeader={showHeader}>
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
      <Layout
        isPanel={isPanel}
        showHeader={showHeader}
        searchInput={<SearchForm />}
      >
        <PanelBody>
          <ErrorBoundary
            fallbackRender={({error}) => (
              <ErrorMessage
                message={
                  isRequestError(error) &&
                  error?.response?.status === HTTP_STATUS_FORBIDDEN
                    ? INSTANCE_HISTORY_FORBIDDEN.message
                    : 'Instance History could not be fetched'
                }
                additionalInfo={
                  isRequestError(error) &&
                  error?.response?.status === HTTP_STATUS_FORBIDDEN
                    ? INSTANCE_HISTORY_FORBIDDEN.additionalInfo
                    : 'Refresh the page to try again'
                }
              />
            )}
          >
            {isFiltered && !isModificationModeEnabled ? (
              <FilteredElementInstancesList
                searchText={submittedSearch.trim()}
                processInstanceKey={processInstance!.processInstanceKey}
                businessObjects={businessObjects!}
              />
            ) : (
              <ElementInstancesTree
                processInstance={processInstance!}
                businessObjects={businessObjects!}
                errorMessage={
                  <ErrorMessage message="Instance History could not be fetched" />
                }
              />
            )}
          </ErrorBoundary>
        </PanelBody>
      </Layout>
    );
  });

export {ElementInstanceLog};
