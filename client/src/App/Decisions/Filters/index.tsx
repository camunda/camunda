/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import {Locations} from 'modules/routes';
import {
  DecisionInstanceFilters,
  getDecisionInstanceFilters,
  updateDecisionsFiltersSearchString,
} from 'modules/utils/filter';
import {Field, Form} from 'react-final-form';
import {useLocation, useNavigate, Location} from 'react-router-dom';
import {CollapsablePanel} from './CollapsablePanel';
import {
  FormElement,
  Dropdown,
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
import {Button} from 'modules/components/Button';
import {isEqual} from 'lodash';
import {AutoSubmit} from 'modules/components/AutoSubmit';
import {DecisionsFormGroup} from './DecisionsFormGroup';
import {
  validateDateCharacters,
  validateDateComplete,
  validateDecisionIdsCharacters,
  validateDecisionIdsLength,
  validateParentInstanceIdCharacters,
  validateParentInstanceIdComplete,
  validateParentInstanceIdNotTooLong,
  validatesDecisionIdsComplete,
} from 'modules/validators';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {FieldValidator} from 'final-form';

type OptionalFilter = keyof Pick<
  DecisionInstanceFilters,
  'decisionInstanceIds' | 'processInstanceId' | 'evaluationDate'
>;

const optionalFilters: Array<OptionalFilter> = [
  'decisionInstanceIds',
  'processInstanceId',
  'evaluationDate',
];

const OPTIONAL_FILTER_FIELDS: Record<
  OptionalFilter,
  {
    label: string;
    placeholder?: string;
    type: 'multiline' | 'text';
    rows?: number;
    validate?: FieldValidator<string | undefined>;
  }
> = {
  decisionInstanceIds: {
    label: 'Decision Instance Key(s)',
    type: 'multiline',
    placeholder: 'separated by space or comma',
    rows: 1,
    validate: mergeValidators(
      validateDecisionIdsCharacters,
      validateDecisionIdsLength,
      validatesDecisionIdsComplete
    ),
  },
  processInstanceId: {
    label: 'Process Instance Key',
    type: 'text',
    validate: mergeValidators(
      validateParentInstanceIdComplete,
      validateParentInstanceIdNotTooLong,
      validateParentInstanceIdCharacters
    ),
  },
  evaluationDate: {
    label: 'Evaluation Date',
    placeholder: 'YYYY-MM-DD hh:mm:ss',
    type: 'text',
    validate: mergeValidators(validateDateCharacters, validateDateComplete),
  },
};

const initialValues: DecisionInstanceFilters = {
  evaluated: true,
  failed: true,
};

type LocationType = Omit<Location, 'state'> & {
  state: {hideOptionalFilters?: boolean};
};

const Filters: React.FC = observer(() => {
  const location = useLocation() as LocationType;
  const navigate = useNavigate();

  const [visibleFilters, setVisibleFilters] = useState<OptionalFilter[]>([]);

  const unselectedOptionalFilters = optionalFilters.filter(
    (filter) => !visibleFilters.includes(filter)
  );

  useEffect(() => {
    if (location.state?.hideOptionalFilters) {
      setVisibleFilters([]);
    }
  }, [location.state]);

  useEffect(() => {
    const params = Array.from(
      new URLSearchParams(location.search).keys()
    ).filter((param) =>
      (optionalFilters as string[]).includes(param)
    ) as OptionalFilter[];

    setVisibleFilters((currentVisibleFilters) => {
      return Array.from(new Set([...currentVisibleFilters, ...params]));
    });
  }, [location.search]);

  return (
    <CollapsablePanel label="Filters">
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
              <DecisionsFormGroup />
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
                          setVisibleFilters(
                            Array.from(
                              new Set([...visibleFilters, ...[filter]])
                            )
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
                        setVisibleFilters(
                          visibleFilters.filter(
                            (visibleFilter) => visibleFilter !== filter
                          )
                        );

                        form.change(filter, undefined);
                        form.submit();
                      }}
                    />
                    <Field
                      name={filter}
                      validate={OPTIONAL_FILTER_FIELDS[filter].validate}
                    >
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
                  navigate(Locations.decisions(initialValues));
                  setVisibleFilters([]);
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
