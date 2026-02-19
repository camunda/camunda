/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Form} from 'react-final-form';
import {
  PROCESS_INSTANCE_AUDIT_LOG_FILTER_FIELDS,
  type ProcessInstanceOperationsLogFilterField,
  type ProcessInstanceOperationsLogFilters,
} from '../operationsLogFilters.ts';
import {updateFiltersSearchString} from 'modules/utils/filter';
import {useLocation, useNavigate} from 'react-router-dom';
import {AutoSubmit} from 'modules/components/AutoSubmit.tsx';
import {Stack} from '@carbon/react';
import {FilterMultiselect} from 'modules/components/FilterMultiSelect';
import {
  auditLogEntityTypeSchema,
  auditLogOperationTypeSchema,
} from '@camunda/camunda-api-zod-schemas/8.9';
import {getFilters} from 'modules/utils/filter/getProcessInstanceFilters.ts';
import {FiltersForm} from './styled.tsx';

const Filters: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const filterValues = getFilters<
    ProcessInstanceOperationsLogFilterField,
    ProcessInstanceOperationsLogFilters
  >(location.search, PROCESS_INSTANCE_AUDIT_LOG_FILTER_FIELDS, []);

  const setFilters = (filters: ProcessInstanceOperationsLogFilters) => {
    navigate({
      search: updateFiltersSearchString<ProcessInstanceOperationsLogFilters>(
        new URLSearchParams(location.search),
        filters,
        PROCESS_INSTANCE_AUDIT_LOG_FILTER_FIELDS,
        [],
      ),
    });
  };

  return (
    <Form<ProcessInstanceOperationsLogFilters>
      onSubmit={setFilters}
      initialValues={filterValues}
    >
      {({handleSubmit}) => (
        <FiltersForm onSubmit={handleSubmit}>
          <AutoSubmit fieldsToSkipTimeout={['operationType', 'entityType']} />
          <Stack orientation="horizontal" gap={5}>
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
          </Stack>
        </FiltersForm>
      )}
    </Form>
  );
};

export {Filters};
