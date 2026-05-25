/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useEffect} from 'react';
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
import {elementInstanceHistorySearchStore} from 'modules/stores/elementInstanceHistorySearch';

const Layout: React.FC<{children: React.ReactNode; isPanel: boolean}> =
  observer(({children, isPanel}) => {
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
        {!modificationsStore.isModificationModeEnabled && <SearchInput />}
        {children}
      </Container>
    );
  });

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

    // Keep the search store's processInstanceKey in sync with the URL, and
    // drop any active search when modification mode is enabled (modification
    // mode is incompatible with search).
    const processInstanceKey = processInstance?.processInstanceKey ?? null;
    const isModificationModeEnabled =
      modificationsStore.isModificationModeEnabled;
    useEffect(() => {
      if (isModificationModeEnabled) {
        elementInstanceHistorySearchStore.reset();
      }
      elementInstanceHistorySearchStore.setProcessInstanceKey(
        processInstanceKey,
      );
      return () => {
        elementInstanceHistorySearchStore.reset();
      };
    }, [processInstanceKey, isModificationModeEnabled]);

    if ([processInstanceStatus, businessObjectsStatus].includes('pending')) {
      return (
        <Layout isPanel={isPanel}>
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
        <Layout isPanel={isPanel}>
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

    const isFiltered = elementInstanceHistorySearchStore.hasActiveSearch;

    return (
      <Layout isPanel={isPanel}>
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
            <FilteredElementInstancesList businessObjects={businessObjects!} />
          )}
        </PanelBody>
      </Layout>
    );
  },
);

export {ElementInstanceLog};
