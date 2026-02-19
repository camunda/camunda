/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLocation, useNavigate} from 'react-router-dom';
import {Dropdown, Stack} from '@carbon/react';
import {updateFiltersSearchString} from 'modules/utils/filter';
import {Container} from './styled';
import {Field, Form} from 'react-final-form';
import {TextInputField} from 'modules/components/TextInputField';
import {Title} from 'modules/components/FiltersPanel/styled';
import {FiltersPanel} from 'modules/components/FiltersPanel/index';
import {ProcessField} from 'App/Processes/ListView/Filters/ProcessField';
import {ProcessVersionField} from 'App/Processes/ListView/Filters/ProcessVersionField';
import {AutoSubmit} from 'modules/components/AutoSubmit';
import {
  getDefinitionIdentifier,
  splitDefinitionIdentifier,
} from 'modules/hooks/processDefinitions';
import isEqual from 'lodash/isEqual';
import {observer} from 'mobx-react';
import {TenantField} from 'modules/components/TenantField';
import {getFilters} from 'modules/utils/filter/getProcessInstanceFilters';
import {
  AUDIT_LOG_FILTER_FIELDS,
  type OperationsLogFilterField,
  type OperationsLogFilters,
} from '../auditLogFilters';
import {
  auditLogEntityTypeSchema,
  auditLogOperationTypeSchema,
  auditLogResultSchema,
} from '@camunda/camunda-api-zod-schemas/8.9';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';
import {DateRangeField} from 'modules/components/DateRangeField';
import {useState} from 'react';
import {FilterMultiselect} from 'modules/components/FilterMultiSelect';

const initialValues: OperationsLogFilters = {};

const Filters: React.FC = observer(() => {
  const navigate = useNavigate();
  const location = useLocation();
  const [isDateRangeModalOpen, setIsDateRangeModalOpen] =
    useState<boolean>(false);

  const filterValues = getFilters<
    OperationsLogFilterField,
    OperationsLogFilters
  >(location.search, AUDIT_LOG_FILTER_FIELDS, []);
  if (filterValues.process && filterValues.tenant !== 'all') {
    filterValues.process = getDefinitionIdentifier(
      filterValues.process,
      filterValues.tenant,
    );
  }
  if (filterValues.tenant === 'all') {
    delete filterValues.process;
    delete filterValues.version;
  }

  const setFilters = (filters: OperationsLogFilters) => {
    navigate({
      search: updateFiltersSearchString<OperationsLogFilters>(
        new URLSearchParams(location.search),
        filters,
        AUDIT_LOG_FILTER_FIELDS,
        [],
      ),
    });
  };

  return (
    <>
      <Form<OperationsLogFilters>
        onSubmit={(values: OperationsLogFilters) => {
          setFilters({
            ...values,
            process: splitDefinitionIdentifier(values.process).definitionId,
          });
        }}
        initialValues={filterValues}
      >
        {({handleSubmit, form, values}) => (
          <form onSubmit={handleSubmit}>
            <FiltersPanel
              localStorageKey="isAuditLogsFiltersCollapsed"
              isResetButtonDisabled={isEqual(initialValues, values)}
              onResetClick={() => {
                form.reset();
                setFilters(initialValues);
              }}
            >
              <Container>
                <AutoSubmit
                  fieldsToSkipTimeout={[
                    'process',
                    'version',
                    'operationType',
                    'entityType',
                    'result',
                  ]}
                />
                <Stack gap={5}>
                  {window.clientConfig?.multiTenancyEnabled && (
                    <div>
                      <Title>Tenant</Title>
                      <Stack gap={5}>
                        <TenantField
                          onChange={() => {
                            form.change('process', undefined);
                            form.change('version', undefined);
                          }}
                        />
                      </Stack>
                    </div>
                  )}
                  <div>
                    <Title>Process</Title>
                    <Stack gap={5}>
                      <ProcessField />
                      <ProcessVersionField />
                      <Field name="processInstanceKey">
                        {({input}) => (
                          <TextInputField
                            {...input}
                            id="process-instance-key"
                            size="sm"
                            labelText="Process instance key"
                            type="text"
                            placeholder="Process instance key"
                          />
                        )}
                      </Field>
                    </Stack>
                  </div>
                  <div>
                    <Title>Operation</Title>
                    <Stack gap={5}>
                      <FilterMultiselect
                        name="operationType"
                        titleText="Operation type"
                        items={auditLogOperationTypeSchema.options}
                      />
                      <FilterMultiselect
                        name="entityType"
                        titleText="Entity type"
                        items={auditLogEntityTypeSchema.options}
                      />
                      <Field name="result">
                        {({input}) => (
                          <Dropdown
                            label="Choose option"
                            aria-label="Choose option"
                            titleText="Operations status"
                            id="result-field"
                            onChange={({selectedItem}) =>
                              input.onChange(
                                selectedItem === 'all'
                                  ? undefined
                                  : selectedItem,
                              )
                            }
                            items={['all', ...auditLogResultSchema.options]}
                            itemToString={(item) =>
                              item === 'all' ? 'All' : spaceAndCapitalize(item)
                            }
                            selectedItem={input.value}
                            size="sm"
                          />
                        )}
                      </Field>
                      <Field name="actorId">
                        {({input}) => (
                          <TextInputField
                            {...input}
                            id="actorId"
                            size="sm"
                            labelText="Actor"
                            type="text"
                            placeholder="Username or client ID"
                          />
                        )}
                      </Field>
                      <DateRangeField
                        isModalOpen={isDateRangeModalOpen}
                        onModalClose={() => setIsDateRangeModalOpen(false)}
                        onClick={() => setIsDateRangeModalOpen(true)}
                        filterName="timestamp"
                        popoverTitle="Filter by timestamp date range"
                        label="Timestamp date range"
                        fromDateTimeKey="timestampAfter"
                        toDateTimeKey="timestampBefore"
                      />
                    </Stack>
                  </div>
                </Stack>
              </Container>
            </FiltersPanel>
          </form>
        )}
      </Form>
    </>
  );
});

export {Filters};
