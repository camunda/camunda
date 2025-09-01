/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef, useState} from 'react';
import {observer} from 'mobx-react';
import {Button, ComposedModal, ModalBody, ModalFooter, ModalHeader, Stack} from '@carbon/react';
import {Error} from '@carbon/react/icons';
import {Form} from 'react-final-form';
import isEqual from 'lodash/isEqual';
import {type ProcessInstanceFilters} from 'modules/utils/filter/shared';
import {AutoSubmit} from 'modules/components/AutoSubmit';
import {useFilters} from 'modules/hooks/useFilters';
import {ProcessField} from './ProcessField';
import {ProcessVersionField} from './ProcessVersionField';
import {FlowNodeField} from './FlowNodeField';
import {
  Container,
  Title,
  Form as StyledForm,
} from 'modules/components/FiltersPanel/styled';
import {FiltersPanel} from 'modules/components/FiltersPanel';
import {
  CheckmarkOutline,
  RadioButtonChecked,
  WarningFilled,
} from 'modules/components/StateIcon/styled';
import {CheckboxGroup} from './CheckboxGroup';
import {
  type OptionalFilter,
  OptionalFiltersFormGroup,
} from './OptionalFiltersFormGroup';
import {TenantField} from 'modules/components/TenantField';
import {
  C3AdvancedSearchFilters,
  type FieldSpecMap,
  type FilterPayload,
  type AdvancedFilterOperator,
} from 'modules/components/C3AdvancedSearchFilters';
import {processesStore} from 'modules/stores/processes/processes.list';
import {batchModificationStore} from 'modules/stores/batchModification';
import {searchProcessInstances} from 'modules/api/v2/processInstances/searchProcessInstances';
import type {QueryProcessInstancesRequestBody} from '@vzeta/camunda-api-zod-schemas/8.8';

const initialValues: ProcessInstanceFilters = {
  active: true,
  incidents: true,
};

const Filters: React.FC = observer(() => {
  const filters = useFilters();
  const [visibleFilters, setVisibleFilters] = useState<OptionalFilter[]>([]);
  const [isAdvancedFiltersOpen, setIsAdvancedFiltersOpen] = useState(false);
  const advancedPayloadRef = useRef<FilterPayload | undefined>(undefined);
  const filtersFromUrl = filters.getFilters();
  const isBatchModificationEnabled = batchModificationStore.state.isEnabled;

  // Operators used for date-time fields in Operate advanced search
  const OperateProcessInstanceOperators: AdvancedFilterOperator[] = [
    '$eq',
    '$neq',
    '$exists',
    '$gt',
    '$gte',
    '$lt',
    '$lte',
    '$in',
  ];

  // Advanced Filters field specification based on the provided definition
  const advancedFields: FieldSpecMap = {
    processInstanceKey: {
      label: 'Process Instance Key',
      description: 'The key of this process instance.',
      matchType: 'both',
      type: 'string',
      operators: ['$eq', '$neq', '$exists', '$in', '$notIn'] as AdvancedFilterOperator[],
      optional: true,
    },
    parentProcessInstanceKey: {
      label: 'Parent Process Instance Key',
      description: 'The parent process instance key.',
      matchType: 'both',
      type: 'string',
      operators: ['$eq', '$neq', '$exists', '$in', '$notIn'] as AdvancedFilterOperator[],
      optional: true,
    },
    parentElementInstanceKey: {
      label: 'Parent Element Instance Key',
      description: 'The parent element instance key.',
      matchType: 'both',
      type: 'string',
      operators: ['$eq', '$neq', '$exists', '$in', '$notIn'] as AdvancedFilterOperator[],
      optional: true,
    },
    batchOperationId: {
      label: 'Batch Operation ID',
      description: 'The batch operation ID.',
      matchType: 'both',
      type: 'string',
      operators: ['$eq', '$neq', '$exists', '$in', '$notIn', '$like'] as AdvancedFilterOperator[],
      optional: true,
    },
    errorMessage: {
      label: 'Error Message',
      description: 'The error message related to the process.',
      matchType: 'both',
      type: 'string',
      operators: ['$eq', '$neq', '$exists', '$in', '$notIn', '$like'] as AdvancedFilterOperator[],
      optional: true,
    },
    hasRetriesLeft: {
      label: 'Has Retries Left',
      description: 'Whether the process has failed jobs with retries left.',
      matchType: 'exact-match',
      type: 'boolean',
      optional: true,
    },
    elementInstanceState: {
      label: 'Element Instance State',
      description:
        'The state of the element instances associated with the process instance.',
      matchType: 'both',
      type: 'enum',
      enumValues: ['ACTIVE', 'COMPLETED', 'TERMINATED'],
      operators: ['$eq', '$neq', '$exists', '$in', '$like'] as AdvancedFilterOperator[],
      optional: true,
    },
    elementId: {
      label: 'Element ID',
      description: 'The element ID associated with the process instance.',
      matchType: 'both',
      type: 'string',
      operators: ['$eq', '$neq', '$exists', '$in', '$notIn', '$like'] as AdvancedFilterOperator[],
      optional: true,
    },
    hasElementInstanceIncident: {
      label: 'Has Element Instance Incident',
      description: 'Whether the element instance has an incident or not.',
      matchType: 'exact-match',
      type: 'boolean',
      optional: true,
    },
    incidentErrorHashCode: {
      label: 'Incident Error Hash Code',
      description: 'The incident error hash code, associated with this process.',
      matchType: 'both',
      type: 'number',
      operators: ['$eq', '$neq', '$exists', '$gt', '$gte', '$lt', '$lte', '$in'] as AdvancedFilterOperator[],
      optional: true,
    },
    processDefinitionId: {
      label: 'Process Definition ID',
      description: 'The process definition ID.',
      matchType: 'both',
      type: 'string',
      operators: ['$eq', '$neq', '$exists', '$in', '$notIn', '$like'] as AdvancedFilterOperator[],
      optional: true,
    },
    processDefinitionName: {
      label: 'Process Definition Name',
      description: 'The process definition name.',
      matchType: 'both',
      type: 'string',
      operators: ['$eq', '$neq', '$exists', '$in', '$notIn', '$like'] as AdvancedFilterOperator[],
      optional: true,
    },
    processDefinitionVersion: {
      label: 'Process Definition Version',
      description: 'The process definition version.',
      matchType: 'both',
      type: 'number',
      operators: ['$eq', '$neq', '$exists', '$gt', '$gte', '$lt', '$lte', '$in'] as AdvancedFilterOperator[],
      optional: true,
    },
    processDefinitionVersionTag: {
      label: 'Process Definition Version Tag',
      description: 'The process definition version tag.',
      matchType: 'both',
      type: 'string',
      operators: ['$eq', '$neq', '$exists', '$in', '$notIn', '$like'] as AdvancedFilterOperator[],
      optional: true,
    },
    processDefinitionKey: {
      label: 'Process Definition Key',
      description: 'The process definition key.',
      matchType: 'both',
      type: 'string',
      operators: ['$eq', '$neq', '$exists', '$in', '$notIn'] as AdvancedFilterOperator[],
      optional: true,
    },
    startDate: {
      label: 'Start Date',
      description: 'Process start date',
      matchType: 'both',
      type: 'date-time',
      operators: OperateProcessInstanceOperators,
      optional: true,
    },
    endDate: {
      label: 'End Date',
      description: 'Process end date',
      matchType: 'both',
      type: 'date-time',
      operators: OperateProcessInstanceOperators,
      optional: true,
    },
    state: {
      label: 'State',
      description: 'The process instance state.',
      matchType: 'both',
      type: 'enum',
      enumValues: ['ACTIVE', 'COMPLETED', 'TERMINATED'],
      operators: ['$eq', '$neq', '$exists', '$in', '$like'] as AdvancedFilterOperator[],
      optional: true,
    },
    hasIncident: {
      label: 'Has Incident',
      description: 'Whether this process instance has a related incident or not.',
      matchType: 'exact-match',
      type: 'boolean',
      optional: true,
    },
    tenantId: {
      label: 'Tenant Id',
      description: 'The tenant Id.',
      matchType: 'both',
      type: 'string',
      operators: ['$eq', '$neq', '$exists', '$in', '$notIn', '$like'] as AdvancedFilterOperator[],
      optional: true,
    },
    variables: {
      label: 'Variables',
      description: 'Process variables array',
      matchType: 'array',
      optional: true,
      arrayItems: {
        name: {
          label: 'Variable Name',
          description: 'Name of the variable',
          matchType: 'exact-match',
          type: 'string',
          optional: false,
        },
        value: {
          label: 'Variable Value',
          description: 'Value of the variable',
          matchType: 'both',
          type: 'string',
          operators: ['$eq', '$neq', '$exists', '$in', '$notIn', '$like'] as AdvancedFilterOperator[],
          optional: false,
        },
      },
    },
  };

  return (
    <>
    <Form<ProcessInstanceFilters>
      onSubmit={(values) => {
        filters.setFilters({
          ...values,
          ...(values.process !== undefined
            ? {
                process: processesStore.state.processes.find(
                  ({key}) => key === values.process,
                )?.bpmnProcessId,
              }
            : {}),
        });
      }}
      initialValues={{
        ...filtersFromUrl,
        ...(filtersFromUrl.process !== undefined
          ? {
              process: processesStore.getProcess({
                bpmnProcessId: filtersFromUrl.process,
                tenantId: filtersFromUrl.tenant,
              })?.key,
            }
          : {}),
      }}
    >
      {({handleSubmit, form, values}) => (
        <StyledForm onSubmit={handleSubmit}>
          <FiltersPanel
            localStorageKey="isFiltersCollapsed"
            isResetButtonDisabled={
              (isEqual(initialValues, values) && visibleFilters.length === 0) ||
              isBatchModificationEnabled
            }
            onResetClick={() => {
              form.reset();
              filters.setFilters(initialValues);
              setVisibleFilters([]);
            }}
          >
            <Container>
              <AutoSubmit
                fieldsToSkipTimeout={[
                  'tenant',
                  'process',
                  'version',
                  'flowNodeId',
                  'active',
                  'incidents',
                  'completed',
                  'canceled',
                  'retriesLeft',
                ]}
              />
              <Stack gap={5}>
                {window.clientConfig?.multiTenancyEnabled && (
                  <div>
                    <Title>Tenant</Title>
                    <TenantField
                      onChange={(selectedItem) => {
                        form.change('process', undefined);
                        form.change('version', undefined);

                        processesStore.fetchProcesses(selectedItem);
                      }}
                    />
                  </div>
                )}
                <div>
                  <Title>Process</Title>
                  <Stack gap={5}>
                    <ProcessField />
                    <ProcessVersionField />
                    <FlowNodeField />
                  </Stack>
                </div>
                <div>
                  <Title>Instances States</Title>
                  <Stack gap={3}>
                    <CheckboxGroup
                      groupLabel="Running Instances"
                      dataTestId="filter-running-instances"
                      items={[
                        {
                        label: 'Active',
                          name: 'active',
                          Icon: RadioButtonChecked,
                        },
                        {
                          label: 'Incidents',
                          name: 'incidents',
                          Icon: WarningFilled,
                        },
                      ]}
                    />
                    <CheckboxGroup
                      groupLabel="Finished Instances"
                      dataTestId="filter-finished-instances"
                      items={[
                        {
                          label: 'Completed',
                          name: 'completed',
                          Icon: CheckmarkOutline,
                        },
                        {
                          label: 'Canceled',
                          name: 'canceled',
                          Icon: Error,
                        },
                      ]}
                    />
                  </Stack>
                </div>
                <OptionalFiltersFormGroup
                  visibleFilters={visibleFilters}
                  onVisibleFilterChange={setVisibleFilters}
           onOpenAdvanced={() => setIsAdvancedFiltersOpen(true)}
                />
              </Stack>
            </Container>
          </FiltersPanel>
        </StyledForm>
      )}
  </Form>
  <ComposedModal
      open={isAdvancedFiltersOpen}
      onClose={() => setIsAdvancedFiltersOpen(false)}
      size="lg"
      preventCloseOnClickOutside
    >
      <ModalHeader label="Filters" title="Advanced Filters" closeModal={() => setIsAdvancedFiltersOpen(false)} />
      <ModalBody hasForm>
        <C3AdvancedSearchFilters
          fields={advancedFields}
          onFilterChange={async (payload) => {
            advancedPayloadRef.current = payload;
            // eslint-disable-next-line no-console
            console.log('[Advanced Filters] payload changed:', payload);
            
            // Call the v2 API directly with the filter payload
            try {
              const v2ApiPayload = {
                filter: payload,
                page: { limit: 50 },
                sort: [{ field: 'startDate', order: 'desc' }],
              } as QueryProcessInstancesRequestBody;
              
              // eslint-disable-next-line no-console
              console.log('[Advanced Filters] v2 API payload:', v2ApiPayload);
              
              const {response, error} = await searchProcessInstances(v2ApiPayload);
              if (response) {
                // eslint-disable-next-line no-console
                console.log('[Advanced Filters] v2 API response:', response);
              } else {
                // eslint-disable-next-line no-console
                console.error('[Advanced Filters] v2 API error:', error);
              }
            } catch (err) {
              // eslint-disable-next-line no-console
              console.error('[Advanced Filters] Failed to call v2 API:', err);
            }
          }}
        />
      </ModalBody>
      <ModalFooter
        primaryButtonText="Apply"
        secondaryButtonText="Cancel"
        onRequestClose={() => setIsAdvancedFiltersOpen(false)}
        onRequestSubmit={() => {
          // TODO: map advancedPayloadRef.current into Operate filters and submit
          setIsAdvancedFiltersOpen(false);
        }}
      />
  </ComposedModal>
  </>
  );
});

export {Filters};
