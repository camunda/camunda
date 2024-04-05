/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Fragment} from 'react';
import {
  Button,
  ComposedModal,
  DatePicker,
  DatePickerInput,
  ModalBody,
  ModalFooter,
  ModalHeader,
  RadioButton,
  RadioButtonGroup,
  Select,
  SelectItem,
  TextInput,
  Toggle,
  FormGroup,
} from '@carbon/react';
import {Close, Add} from '@carbon/react/icons';
import {Field, Form} from 'react-final-form';
import {FieldArray} from 'react-final-form-arrays';
import arrayMutators from 'final-form-arrays';
import set from 'lodash/set';
import {MultiTenancySelect} from 'modules/components/useMultiTenancyDropdown/MultiTenancySelect';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {useMultiTenancyDropdown} from 'modules/components/useMultiTenancyDropdown';
import {
  type CustomFilters,
  customFiltersSchema,
} from 'modules/custom-filters/customFiltersSchema';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {ProcessesSelect} from './ProcessesSelect';
import styles from './styles.module.scss';
import cn from 'classnames';

type FormValues = CustomFilters;

const DEFAULT_FORM_VALUES: FormValues = {
  assignee: 'all',
  status: 'all',
};

const ADVANCED_FILTERS: Array<keyof FormValues> = [
  'dueDateFrom',
  'dueDateTo',
  'followUpDateFrom',
  'followUpDateTo',
  'taskId',
  'variables',
];

function getDateValue(date: Date[]): Date | undefined {
  if (date.length !== 1) {
    return undefined;
  }

  return date[0];
}

type Props = {
  isOpen: boolean;
  onClose: () => void;
  onApply: (values: FormValues) => void;
};

const CustomFiltersModal: React.FC<Props> = ({isOpen, onClose, onApply}) => {
  const initialValues =
    getStateLocally('customFilters')?.custom ?? DEFAULT_FORM_VALUES;
  const label = 'Advanced filters';
  const {isMultiTenancyVisible} = useMultiTenancyDropdown();
  const {data: currentUser} = useCurrentUser();
  const groups = currentUser?.groups ?? [];

  return (
    <ComposedModal
      open={isOpen}
      preventCloseOnClickOutside
      size="md"
      onClose={onClose}
    >
      {isOpen ? (
        <Form<
          FormValues & {
            areAdvancedFiltersEnabled: boolean;
          }
        >
          onSubmit={({areAdvancedFiltersEnabled: _, ...values}) => {
            const result = customFiltersSchema.safeParse(values);

            if (result.success) {
              storeStateLocally('customFilters', {custom: result.data});
              onApply(result.data);

              return;
            }

            return result.error.flatten(({path, message}) => {
              const [firstPath] = path;

              if (firstPath !== 'variables') {
                return message;
              }

              return set({}, path[path.length - 1], message);
            }).fieldErrors;
          }}
          initialValues={{
            ...initialValues,
            areAdvancedFiltersEnabled: ADVANCED_FILTERS.some(
              (key) => initialValues?.[key] !== undefined,
            ),
          }}
          mutators={{...arrayMutators}}
        >
          {({handleSubmit, form, values}) => (
            <>
              <ModalHeader title="Apply filters" buttonOnClick={onClose} />
              <ModalBody hasForm>
                <form className={styles.twoColumnGrid} onSubmit={handleSubmit}>
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
                        <RadioButton
                          labelText="Unassigned"
                          value="unassigned"
                        />
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
                        className={styles.secondColumn}
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
                        {({input}) =>
                          groups.length === 0 ? (
                            <TextInput
                              {...input}
                              id={input.name}
                              labelText="In a group"
                              className={styles.secondColumn}
                              placeholder="Enter group name"
                              disabled={currentUser === undefined}
                            />
                          ) : (
                            <Select
                              {...input}
                              id={input.name}
                              labelText="In a group"
                              className={styles.secondColumn}
                            >
                              <SelectItem value="" text="" />
                              {groups.map((group) => (
                                <SelectItem
                                  key={group}
                                  value={group}
                                  text={group}
                                />
                              ))}
                            </Select>
                          )
                        }
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
                          labelText="Tenant"
                          className={styles.secondColumn}
                        />
                      )}
                    </Field>
                  ) : null}

                  <Field name="areAdvancedFiltersEnabled">
                    {({input}) => (
                      <Toggle
                        id="toggle-advanced-filters"
                        className={styles.toggle}
                        size="sm"
                        labelText={label}
                        aria-label={label}
                        hideLabel
                        labelA="Hidden"
                        labelB="Visible"
                        toggled={input.checked}
                        onToggle={input.onChange}
                      />
                    )}
                  </Field>

                  {values.areAdvancedFiltersEnabled ? (
                    <>
                      <FormGroup
                        className={styles.dateRangeFormGroup}
                        legendText="Due date"
                      >
                        <Field name="dueDateFrom">
                          {({input}) => (
                            <DatePicker
                              {...input}
                              onChange={(dates) => {
                                input.onChange(getDateValue(dates));
                              }}
                              className={styles.datePicker}
                              datePickerType="single"
                              dateFormat="d/m/y"
                            >
                              <DatePickerInput
                                id="due-date-from"
                                placeholder="dd/mm/yyyy"
                                labelText="From"
                                size="md"
                              />
                            </DatePicker>
                          )}
                        </Field>
                        <Field name="dueDateTo">
                          {({input}) => (
                            <DatePicker
                              {...input}
                              onChange={(dates) => {
                                input.onChange(getDateValue(dates));
                              }}
                              className={styles.datePicker}
                              datePickerType="single"
                              dateFormat="d/m/y"
                            >
                              <DatePickerInput
                                id="due-date-to"
                                placeholder="dd/mm/yyyy"
                                labelText="To"
                                size="md"
                              />
                            </DatePicker>
                          )}
                        </Field>
                      </FormGroup>

                      <FormGroup
                        legendText="Follow up date"
                        className={cn(
                          styles.dateRangeFormGroup,
                          styles.secondColumn,
                        )}
                      >
                        <Field name="followUpDateFrom">
                          {({input}) => (
                            <DatePicker
                              {...input}
                              onChange={(dates) => {
                                input.onChange(getDateValue(dates));
                              }}
                              className={styles.datePicker}
                              datePickerType="single"
                              dateFormat="d/m/y"
                            >
                              <DatePickerInput
                                id="follow-up-date-from"
                                placeholder="dd/mm/yyyy"
                                labelText="From"
                                size="md"
                              />
                            </DatePicker>
                          )}
                        </Field>
                        <Field name="followUpDateTo">
                          {({input}) => (
                            <DatePicker
                              {...input}
                              onChange={(dates) => {
                                input.onChange(getDateValue(dates));
                              }}
                              className={styles.datePicker}
                              datePickerType="single"
                              dateFormat="d/m/y"
                            >
                              <DatePickerInput
                                id="follow-up-date-to"
                                placeholder="dd/mm/yyyy"
                                labelText="To"
                                size="md"
                              />
                            </DatePicker>
                          )}
                        </Field>
                      </FormGroup>

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
                        {({fields, meta: arrayMeta}) => (
                          <>
                            <FormGroup
                              className={styles.variableFormGroup}
                              legendText="Task variables"
                            >
                              <div className={styles.variableGrid}>
                                {fields.map((name, index) => (
                                  <Fragment key={name}>
                                    <Field name={`${name}.name`}>
                                      {({input, meta}) => (
                                        <TextInput
                                          {...input}
                                          id={input.name}
                                          className={styles.variableGridItem}
                                          labelText="Name"
                                          autoFocus={
                                            index === (fields.length ?? 1) - 1
                                          }
                                          invalid={
                                            meta.submitError !== undefined &&
                                            !arrayMeta.dirtySinceLastSubmit
                                          }
                                          invalidText={meta.submitError}
                                        />
                                      )}
                                    </Field>

                                    <Field name={`${name}.value`}>
                                      {({input, meta}) => (
                                        <TextInput
                                          {...input}
                                          id={input.name}
                                          className={styles.variableGridItem}
                                          labelText="Value"
                                          invalid={
                                            meta.submitError !== undefined &&
                                            !arrayMeta.dirtySinceLastSubmit
                                          }
                                          invalidText={meta.submitError}
                                        />
                                      )}
                                    </Field>

                                    <Button
                                      type="button"
                                      className={styles.variableGridRemove}
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
                              </div>

                              <Button
                                type="button"
                                iconDescription="Remove variable"
                                renderIcon={Add}
                                kind="tertiary"
                                size="md"
                                onClick={() =>
                                  fields.push({name: '', value: ''})
                                }
                              >
                                Add variable
                              </Button>
                            </FormGroup>
                          </>
                        )}
                      </FieldArray>
                    </>
                  ) : null}
                </form>
              </ModalBody>
              <ModalFooter className={styles.modalFooter}>
                <Button
                  kind="ghost"
                  onClick={() => {
                    form.reset(DEFAULT_FORM_VALUES);
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
          )}
        </Form>
      ) : null}
    </ComposedModal>
  );
};

export {CustomFiltersModal};
