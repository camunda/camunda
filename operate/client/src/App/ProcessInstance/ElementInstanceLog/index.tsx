/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useEffect, useRef} from 'react';
import {Form, Field, useForm} from 'react-final-form';
import {useSearchParams, useNavigate} from 'react-router-dom';
import {Container, PanelHeader, ErrorMessage, PanelBody} from './styled';
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
import {AutoSubmit} from 'modules/components/AutoSubmit';

const SEARCH_PARAM_KEY = 'elementSearch';

type SearchFormValues = {search: string};

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

/**
 * Resets the enclosing RFF form to empty when modification mode is enabled.
 * Must be rendered as a child of the `<Form>` component so it can access the
 * form API via `useForm()`.
 */
const FormResetter: React.FC<{shouldReset: boolean}> = ({shouldReset}) => {
  const form = useForm<SearchFormValues>();
  const prevShouldReset = useRef(false);
  useEffect(() => {
    if (shouldReset && !prevShouldReset.current) {
      form.reset({search: ''});
    }
    prevShouldReset.current = shouldReset;
  }, [shouldReset, form]);
  return null;
};

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

    const [searchParams] = useSearchParams();
    const navigate = useNavigate();

    const isModificationModeEnabled =
      modificationsStore.isModificationModeEnabled;

    // The submitted search value lives in the URL so the filtered view is
    // shareable. Reads the param directly so a fresh page load with
    // ?elementSearch=foo shows the filtered list immediately.
    const submittedSearch = searchParams.get(SEARCH_PARAM_KEY) ?? '';
    const isFiltered = submittedSearch.trim().length > 0;

    const handleSearchSubmit = (values: SearchFormValues) => {
      const next = new URLSearchParams(searchParams);
      if (values.search.trim()) {
        next.set(SEARCH_PARAM_KEY, values.search);
      } else {
        next.delete(SEARCH_PARAM_KEY);
      }
      navigate({search: next.toString()}, {replace: true});
    };

    const handleClearSearch = () => {
      const next = new URLSearchParams(searchParams);
      next.delete(SEARCH_PARAM_KEY);
      navigate({search: next.toString()}, {replace: true});
    };

    const searchInputElement = (
      <Form<SearchFormValues>
        onSubmit={handleSearchSubmit}
        initialValues={{search: submittedSearch}}
      >
        {() => (
          <>
            <AutoSubmit />
            <FormResetter shouldReset={isModificationModeEnabled} />
            <Field<string> name="search">
              {({input}) => (
                <SearchInput
                  value={input.value}
                  onChange={(value) => input.onChange(value)}
                  onClear={() => {
                    input.onChange('');
                    handleClearSearch();
                  }}
                />
              )}
            </Field>
          </>
        )}
      </Form>
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
          {isFiltered ? (
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
        </PanelBody>
      </Layout>
    );
  },
);

export {ElementInstanceLog};
