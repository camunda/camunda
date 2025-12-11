/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useNavigate, useSearchParams} from 'react-router-dom';
import {Stack} from '@carbon/react';
import {updateFiltersSearchString} from 'modules/utils/filter';
import {Container} from './styled';
import {Field, Form} from 'react-final-form';
import {TextInputField} from 'modules/components/TextInputField';
import {Title} from 'modules/components/FiltersPanel/styled';
import {FiltersPanel} from 'modules/components/FiltersPanel/index';
import {ProcessField} from '../../Processes/ListView/Filters/ProcessField';
import {ProcessVersionField} from '../../Processes/ListView/Filters/ProcessVersionField';
import {AutoSubmit} from 'modules/components/AutoSubmit';
import {updateProcessInstancesFilterSearchString} from 'modules/utils/filter/v2/processInstancesSearch';

export type OperationsFilterValues = {
  process?: string;
  version?: string;
  processInstanceKey?: string;
  operationType?: string;
  entityType?: string;
  actorId?: string;
};

const initialValues: OperationsFilterValues = {};

const AUDIT_LOG_FILTER_FIELDS: (keyof OperationsFilterValues)[] = [
  'process',
  'version',
  'processInstanceKey',
  'operationType',
  'entityType',
  'actorId',
];

const Filters: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  return (
    <>
      <Form<OperationsFilterValues>
        onSubmit={(values: OperationsFilterValues) => {
          console.log('values', values);
          const nextSearchParams = updateFiltersSearchString(
            searchParams,
            values,
            AUDIT_LOG_FILTER_FIELDS,
            [],
          );

          navigate({
            search: nextSearchParams.toString(),
          });
        }}
        initialValues={initialValues}
      >
        {({handleSubmit, form, values}) => (
          <form onSubmit={handleSubmit} style={{height: '100%'}}>
            <FiltersPanel
              localStorageKey="isAuditLogsFiltersCollapsed"
              isResetButtonDisabled={false}
              onResetClick={() => {
                form.reset();
                navigate({
                  search: updateProcessInstancesFilterSearchString(
                    searchParams,
                    initialValues,
                  ),
                });
              }}
            >
              <Container style={{width: '100%', padding: '1rem'}}>
                <AutoSubmit />
                <Stack gap={5}>
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
                      <Field name="operationType">
                        {({input}) => (
                          <TextInputField
                            {...input}
                            id="operation-type"
                            size="sm"
                            labelText="Operation type"
                            type="text"
                            placeholder="Operation type"
                          />
                        )}
                      </Field>
                      <Field name="entityType">
                        {({input}) => (
                          <TextInputField
                            {...input}
                            id="entity-type"
                            size="sm"
                            labelText="Entity type"
                            type="text"
                            placeholder="Entity type"
                          />
                        )}
                      </Field>
                      <Field name="actorId">
                        {({input}) => (
                          <TextInputField
                            {...input}
                            id="actor-id"
                            size="sm"
                            labelText="Actor"
                            type="text"
                            placeholder="Username or client ID"
                          />
                        )}
                      </Field>
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
};

export {Filters};
