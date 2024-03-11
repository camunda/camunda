/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  Button,
  ComposedModal,
  DatePicker,
  DatePickerInput,
  FormGroup,
  ModalBody,
  ModalHeader,
  RadioButton,
  RadioButtonGroup,
  TextInput,
} from '@carbon/react';
import {
  ModalFooter,
  TwoColumnGrid,
  VariablesGrid,
  Toggle,
  VariableFormGroup,
} from './styled';
import {Fragment, useState} from 'react';
import {Field, Form} from 'react-final-form';
import {useMultiTenancyDropdown} from 'modules/components/useMultiTenancyDropdown';
import {z} from 'zod';
import {FieldArray} from 'react-final-form-arrays';
import {Close, Add} from '@carbon/react/icons';
import arrayMutators from 'final-form-arrays';
import {ProcessesSelect} from './ProcessesSelect';
import {MultiTenancySelect} from 'modules/components/useMultiTenancyDropdown/MultiTenancySelect';

const formSchema = z.object({
  assignee: z
    .enum(['all', 'unassigned', 'me', 'user-and-group'])
    .default('all'),
  assignedTo: z.string().optional(),
  candidateGroup: z.string().optional(),
  status: z.enum(['all', 'open', 'completed']).default('all'),
  bpmnProcess: z.string().optional(),
  tenant: z.string().optional(),
  dueDate: z.array(z.date()).optional(),
  followUpDate: z.array(z.date()).optional(),
  taskId: z.string().optional(),
  variables: z
    .array(
      z.object({
        name: z.string(),
        value: z.string(),
      }),
    )
    .optional(),
});

type FormValues = z.infer<typeof formSchema>;

const DEFAULT_FORM_VALUES: FormValues = {
  assignee: 'all',
  status: 'all',
};

type Props = {
  isOpen: boolean;
  onClose: () => void;
  onApply: () => void;
};

const CustomFiltersModal: React.FC<Props> = ({isOpen, onClose, onApply}) => {
  const [areAdvancedFiltersEnabled, setAreAdvancedFiltersEnabled] =
    useState(false);
  const label = 'Advanced filters';
  const {isMultiTenancyVisible} = useMultiTenancyDropdown();

  return (
    <Form<FormValues>
      onSubmit={() => {
        onApply();
      }}
      initialValues={DEFAULT_FORM_VALUES}
      mutators={{...arrayMutators}}
      keepDirtyOnReinitialize={false}
    >
      {({handleSubmit, form, values}) => (
        <ComposedModal
          open={isOpen}
          preventCloseOnClickOutside
          size="md"
          onClose={onClose}
        >
          <>
            <ModalHeader title="Apply filters" buttonOnClick={onClose} />
            <ModalBody hasForm>
              <TwoColumnGrid as="form" onSubmit={handleSubmit}>
                <Field name="assignee">
                  {({input}) => (
                    <RadioButtonGroup
                      legendText="Assignee"
                      name={input.name}
                      onChange={input.onChange}
                      valueSelected={input.value}
                      defaultSelected="all"
                      orientation="vertical"
                    >
                      <RadioButton
                        labelText="All"
                        value="all"
                        data-modal-primary-focus
                      />
                      <RadioButton labelText="Unassigned" value="unassigned" />
                      <RadioButton labelText="Me" value="me" />
                      <RadioButton
                        labelText="User and group"
                        value="user-and-group"
                      />
                    </RadioButtonGroup>
                  )}
                </Field>

                <Field name="status">
                  {({input}) => (
                    <RadioButtonGroup
                      legendText="Status"
                      name={input.name}
                      onChange={input.onChange}
                      valueSelected={input.value}
                      defaultSelected="all"
                      orientation="vertical"
                      className="second-column"
                    >
                      <RadioButton labelText="All" value="all" />
                      <RadioButton labelText="Open" value="open" />
                      <RadioButton labelText="Completed" value="completed" />
                    </RadioButtonGroup>
                  )}
                </Field>

                {values.assignee === 'user-and-group' ? (
                  <>
                    <Field name="assignedTo">
                      {({input}) => (
                        <TextInput
                          {...input}
                          id={input.name}
                          labelText="Assigned to user"
                          placeholder="Enter user email or username"
                        />
                      )}
                    </Field>
                    <Field name="candidateGroup">
                      {({input}) => (
                        <TextInput
                          {...input}
                          id={input.name}
                          labelText="In a group"
                          className="second-column"
                          placeholder="Enter group name"
                        />
                      )}
                    </Field>
                  </>
                ) : null}
                <Field name="bpmnProcess">
                  {({input}) => (
                    <ProcessesSelect
                      {...input}
                      id={input.name}
                      tenantId={values.tenant}
                      disabled={!isOpen}
                      labelText="Process"
                    />
                  )}
                </Field>
                {isMultiTenancyVisible ? (
                  <Field name="tenant">
                    {({input}) => (
                      <MultiTenancySelect
                        {...input}
                        id={input.name}
                        className="second-column"
                      />
                    )}
                  </Field>
                ) : null}

                <Toggle
                  id="toggle-advanced-filters"
                  size="sm"
                  labelText={label}
                  aria-label={label}
                  hideLabel
                  labelA="Hidden"
                  labelB="Visible"
                  toggled={areAdvancedFiltersEnabled}
                  onToggle={setAreAdvancedFiltersEnabled}
                />

                {areAdvancedFiltersEnabled ? (
                  <>
                    <Field name="dueDate">
                      {({input}) => (
                        <FormGroup legendText="Due date">
                          <DatePicker
                            {...input}
                            datePickerType="range"
                            dateFormat="d/m/y"
                          >
                            <DatePickerInput
                              id="due-date-from"
                              placeholder="dd/mm/yyyy"
                              labelText="From"
                              size="md"
                            />
                            <DatePickerInput
                              id="due-date-to"
                              placeholder="dd/mm/yyyy"
                              labelText="To"
                              size="md"
                            />
                          </DatePicker>
                        </FormGroup>
                      )}
                    </Field>

                    <Field name="followUpDate">
                      {({input}) => (
                        <FormGroup
                          legendText="Follow up date"
                          className="second-column"
                        >
                          <DatePicker
                            {...input}
                            datePickerType="range"
                            dateFormat="d/m/y"
                          >
                            <DatePickerInput
                              id="follow-up-date-from"
                              placeholder="dd/mm/yyyy"
                              labelText="From"
                              size="md"
                            />
                            <DatePickerInput
                              id="follow-up-date-to"
                              placeholder="dd/mm/yyyy"
                              labelText="To"
                              size="md"
                            />
                          </DatePicker>
                        </FormGroup>
                      )}
                    </Field>

                    <Field name="taskId">
                      {({input}) => (
                        <TextInput
                          {...input}
                          id={input.name}
                          labelText="Task ID"
                        />
                      )}
                    </Field>

                    <FieldArray name="variables">
                      {({fields}) => (
                        <>
                          <VariableFormGroup legendText="Task variables">
                            <VariablesGrid>
                              {fields.map((name, index) => (
                                <Fragment key={name}>
                                  <Field name={`${name}.name`}>
                                    {({input}) => (
                                      <TextInput
                                        {...input}
                                        id={input.name}
                                        labelText="Name"
                                        autoFocus={
                                          index === (fields.length ?? 1) - 1
                                        }
                                      />
                                    )}
                                  </Field>

                                  <Field name={`${name}.value`}>
                                    {({input}) => (
                                      <TextInput
                                        {...input}
                                        id={input.name}
                                        labelText="Value"
                                      />
                                    )}
                                  </Field>

                                  <Button
                                    type="button"
                                    hasIconOnly
                                    iconDescription="Remove variable"
                                    renderIcon={Close}
                                    kind="ghost"
                                    size="md"
                                    onClick={() => fields.remove(index)}
                                    tooltipPosition="left"
                                  />
                                </Fragment>
                              ))}
                            </VariablesGrid>

                            <Button
                              type="button"
                              iconDescription="Remove variable"
                              renderIcon={Add}
                              kind="tertiary"
                              size="md"
                              onClick={() => fields.push({name: '', value: ''})}
                            >
                              Add variable
                            </Button>
                          </VariableFormGroup>
                        </>
                      )}
                    </FieldArray>
                  </>
                ) : null}
              </TwoColumnGrid>
            </ModalBody>
            <ModalFooter>
              <Button
                kind="ghost"
                onClick={() => {
                  setAreAdvancedFiltersEnabled(false);
                  form.reset();
                }}
                type="button"
              >
                Reset
              </Button>
              <Button kind="secondary" onClick={onClose} type="button">
                Cancel
              </Button>
              <Button kind="primary" type="submit" onClick={form.submit}>
                Apply
              </Button>
            </ModalFooter>
          </>
        </ComposedModal>
      )}
    </Form>
  );
};

export {CustomFiltersModal};
