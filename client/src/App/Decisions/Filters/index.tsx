/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';
import {Locations} from 'modules/routes';
import {
  DecisionInstanceFilters,
  getDecisionInstanceFilters,
  updateDecisionsFiltersSearchString,
} from 'modules/utils/filter';
import {Field, Form} from 'react-final-form';
import {useLocation, useNavigate} from 'react-router-dom';
import {CollapsablePanel} from './CollapsablePanel';
import {
  FormElement,
  Dropdown,
  Select,
  SectionTitle,
  Checkbox,
  TextField,
  ResetButtonContainer,
  Fields,
  FormGroup,
  OptionalFilters,
  DeleteIcon,
} from './styled';
import {observer} from 'mobx-react';
import {
  decisionInstancesVisibleFiltersStore,
  OptionalFilter,
} from 'modules/stores/decisionInstancesVisibleFilters';
import {Button} from 'modules/components/Button';
import {isEqual} from 'lodash';
import {AutoSubmit} from 'modules/components/AutoSubmit';

const OPTIONAL_FILTER_FIELDS: Record<
  OptionalFilter,
  {
    label: string;
    placeholder?: string;
    type: 'multiline' | 'text';
    rows?: number;
  }
> = {
  decisionInstanceIds: {
    label: 'Decision Instance Id(s)',
    type: 'multiline',
    placeholder: 'separated by space or comma',
    rows: 1,
  },
  processInstanceId: {
    label: 'Process Instance Id',
    type: 'text',
  },
  evaluationDate: {
    label: 'Evaluation Date',
    placeholder: 'YYYY-MM-DD hh:mm:ss',
    type: 'text',
  },
};

const Filters: React.FC = observer(() => {
  const location = useLocation();
  const navigate = useNavigate();
  const {
    possibleOptionalFilters,
    state: {visibleFilters},
  } = decisionInstancesVisibleFiltersStore;
  const unselectedOptionalFilters = possibleOptionalFilters.filter(
    (filter) => !visibleFilters.includes(filter)
  );
  const initialValues: DecisionInstanceFilters = {
    evaluated: true,
    failed: true,
  };

  useEffect(() => {
    const {possibleOptionalFilters} = decisionInstancesVisibleFiltersStore;

    const params = Array.from(
      new URLSearchParams(location.search).keys()
    ).filter((param) =>
      (possibleOptionalFilters as string[]).includes(param)
    ) as OptionalFilter[];

    decisionInstancesVisibleFiltersStore.addVisibleFilters(params);
  }, [location.search]);

  return (
    <CollapsablePanel header="Filters">
      <Form<DecisionInstanceFilters>
        onSubmit={(values) => {
          navigate({
            search: updateDecisionsFiltersSearchString(location.search, values),
          });
        }}
        initialValues={getDecisionInstanceFilters(location.search)}
      >
        {({handleSubmit, form, values}) => (
          <FormElement onSubmit={handleSubmit}>
            <AutoSubmit
              fieldsToSkipTimeout={['name', 'version', 'evaluated', 'failed']}
            />
            <Fields>
              <FormGroup>
                <SectionTitle appearance="emphasis">Decision</SectionTitle>
                <Field name="name">
                  {({input}) => (
                    <Select
                      label="Name"
                      selectedOptions={[input.value]}
                      onCmInput={(event) => {
                        input.onChange(event.detail.selectedOptions[0]);
                      }}
                      options={[
                        {
                          options: [
                            {
                              label: 'All',
                              value: '',
                            },
                            {
                              label: 'Decision 1',
                              value: '1',
                            },
                            {
                              label: 'Decision 2',
                              value: '2',
                            },
                            {
                              label: 'Decision 3',
                              value: '3',
                            },
                          ],
                        },
                      ]}
                    />
                  )}
                </Field>
                <Field name="version">
                  {({input}) => (
                    <Select
                      label="Version"
                      selectedOptions={[input.value]}
                      onCmInput={(event) => {
                        input.onChange(event.detail.selectedOptions[0]);
                      }}
                      options={[
                        {
                          options: [
                            {
                              label: 'All',
                              value: '',
                            },
                            {
                              label: 'Version 1',
                              value: '1',
                            },
                            {
                              label: 'Version 2',
                              value: '2',
                            },
                            {
                              label: 'Version 3',
                              value: '3',
                            },
                          ],
                        },
                      ]}
                    />
                  )}
                </Field>
              </FormGroup>
              <FormGroup>
                <SectionTitle appearance="emphasis">
                  Instance States
                </SectionTitle>
                <Field name="evaluated" component="input" type="checkbox">
                  {({input}) => (
                    <Checkbox
                      {...input}
                      label="Evaluated"
                      id={input.name}
                      checked={input.checked}
                      onCmInput={input.onChange}
                      icon={{icon: 'state:completed', color: 'medLight'}}
                    />
                  )}
                </Field>
                <Field name="failed" component="input" type="checkbox">
                  {({input}) => (
                    <Checkbox
                      {...input}
                      label="Failed"
                      id={input.name}
                      checked={input.checked}
                      onCmInput={input.onChange}
                      icon={{icon: 'state:incident', color: 'danger'}}
                    />
                  )}
                </Field>
              </FormGroup>
              {unselectedOptionalFilters.length > 0 && (
                <Dropdown
                  trigger={{type: 'label', label: 'More Filters'}}
                  options={[
                    {
                      options: unselectedOptionalFilters.map((filter) => ({
                        label: OPTIONAL_FILTER_FIELDS[filter].label,
                        handler: () => {
                          decisionInstancesVisibleFiltersStore.addVisibleFilters(
                            [filter]
                          );
                        },
                      })),
                    },
                  ]}
                />
              )}
              <OptionalFilters>
                {visibleFilters.map((filter) => (
                  <FormGroup key={filter}>
                    <DeleteIcon
                      icon="delete"
                      data-testid={`delete-${filter}`}
                      onClick={() => {
                        decisionInstancesVisibleFiltersStore.hideFilter(filter);
                        form.change(filter, undefined);
                        form.submit();
                      }}
                    />
                    <Field name={filter}>
                      {({input}) => (
                        <TextField
                          {...input}
                          label={OPTIONAL_FILTER_FIELDS[filter].label}
                          type={OPTIONAL_FILTER_FIELDS[filter].type}
                          rows={OPTIONAL_FILTER_FIELDS[filter].rows}
                          placeholder={
                            OPTIONAL_FILTER_FIELDS[filter].placeholder
                          }
                          shouldDebounceError={false}
                          autoFocus
                        />
                      )}
                    </Field>
                  </FormGroup>
                ))}
              </OptionalFilters>
            </Fields>
            <ResetButtonContainer>
              <Button
                title="Reset Filters"
                size="small"
                disabled={
                  isEqual(initialValues, values) && visibleFilters.length === 0
                }
                type="reset"
                onClick={() => {
                  form.reset();
                  navigate(Locations.decisions(location));
                  decisionInstancesVisibleFiltersStore.reset();
                }}
              >
                Reset Filters
              </Button>
            </ResetButtonContainer>
          </FormElement>
        )}
      </Form>
    </CollapsablePanel>
  );
});

export {Filters};
