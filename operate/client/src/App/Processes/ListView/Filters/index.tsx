/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
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
import {C3AdvancedSearchFilters} from 'modules/components/C3AdvancedSearchFilters';
import {processesStore} from 'modules/stores/processes/processes.list';
import {batchModificationStore} from 'modules/stores/batchModification';

const initialValues: ProcessInstanceFilters = {
  active: true,
  incidents: true,
};

const Filters: React.FC = observer(() => {
  const filters = useFilters();
  const [visibleFilters, setVisibleFilters] = useState<OptionalFilter[]>([]);
  const [isAdvancedFiltersOpen, setIsAdvancedFiltersOpen] = useState(false);
  const filtersFromUrl = filters.getFilters();
  const isBatchModificationEnabled = batchModificationStore.state.isEnabled;

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
          onApply={() => {
            // Hook up real apply behavior when the concrete component is integrated
            setIsAdvancedFiltersOpen(false);
          }}
          onReset={() => {
            // Hook up real reset behavior when the concrete component is integrated
          }}
        />
      </ModalBody>
      <ModalFooter
        primaryButtonText="Apply"
        secondaryButtonText="Cancel"
        onRequestClose={() => setIsAdvancedFiltersOpen(false)}
        onRequestSubmit={() => setIsAdvancedFiltersOpen(false)}
      />
  </ComposedModal>
  </>
  );
});

export {Filters};
