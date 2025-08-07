/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Fragment} from 'react';
import {
  Button,
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
import {MultitenancySelect} from 'common/multitenancy/MultitenancySelect';
import {useCurrentUser} from 'common/api/useCurrentUser.query';
import {useIsMultitenancyEnabled} from 'common/multitenancy/useIsMultitenancyEnabled';
import {
  type NamedCustomFilters,
  namedCustomFiltersSchema,
} from 'common/tasks/filters/customFiltersSchema';
import {ProcessesSelect} from './ProcessesSelect';
import styles from './fieldsModal.module.scss';
import cn from 'classnames';
import {Modal} from 'common/components/Modal';
import {useTranslation} from 'react-i18next';

type FormValues = NamedCustomFilters & {
  areAdvancedFiltersEnabled: boolean;
  action: 'apply' | 'save' | 'edit';
};

const DEFAULT_FORM_VALUES: NamedCustomFilters = {
  assignee: 'all',
  status: 'all',
};

const ADVANCED_FILTERS: Array<keyof NamedCustomFilters> = [
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
  onApply: (values: NamedCustomFilters) => void;
  onSave: (values: NamedCustomFilters) => void;
  onEdit: (values: NamedCustomFilters) => void;
  onDelete: () => void;
  initialValues: NamedCustomFilters;
};

const FieldsModal: React.FC<Props> = ({
  isOpen,
  onClose,
  onApply,
  onSave,
  onEdit,
  onDelete,
  initialValues,
}) => {
  const {t, i18n} = useTranslation();
  const label = t('customFiltersModalAdvancedFiltersLabel');
  const {isMultitenancyEnabled} = useIsMultitenancyEnabled();
  const {data: currentUser} = useCurrentUser();
  const groups = currentUser?.groups ?? [];

  return (
    <Modal
      variant="composed-modal"
      open={isOpen}
      preventCloseOnClickOutside
      size="md"
      onClose={onClose}
      aria-label={t('customFiltersModalAriaLabel')}
    >
      {isOpen ? (
        <Form<FormValues>
          onSubmit={({areAdvancedFiltersEnabled: _, action, ...values}) => {
            const result = namedCustomFiltersSchema.safeParse(values);

            if (!result.success) {
              return result.error.flatten(({path, message}) => {
                const [firstPath] = path;

                if (firstPath !== 'variables') {
                  return message;
                }

                return set({}, path[path.length - 1], message);
              }).fieldErrors;
            }

            if (action === 'apply') {
              onApply(result.data);
              return;
            }

            if (action === 'save') {
              onSave(result.data);
              return;
            }

            if (action === 'edit') {
              onEdit(result.data);
            }

            return;
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
              <ModalHeader
                title={t('customFiltersModalApplyFiltersTitle')}
                iconDescription={t('optionsModalCloseButton')}
                buttonOnClick={onClose}
              />
              <ModalBody hasForm>
                <form
                  className={styles.twoColumnGrid}
                  onSubmit={handleSubmit}
                  tabIndex={-1}
                >
                  {[undefined, 'custom'].includes(
                    initialValues?.name,
                  ) ? null : (
                    <Field name="name" defaultValue="">
                      {({input}) => (
                        <TextInput
                          {...input}
                          id={input.name}
                          labelText={t('customFiltersModalFilterNameLabel')}
                          className={styles.nameField}
                        />
                      )}
                    </Field>
                  )}

                  <Field name="assignee">
                    {({input}) => (
                      <RadioButtonGroup
                        legendText={t('customFiltersModalAssigneeLabel')}
                        name={input.name}
                        onChange={input.onChange}
                        valueSelected={input.value}
                        defaultSelected="all"
                        orientation="vertical"
                      >
                        <RadioButton
                          labelText={t('customFiltersModalAssigneeAll')}
                          value="all"
                          data-modal-primary-focus
                        />
                        <RadioButton
                          labelText={t('customFiltersModalAssigneeUnassigned')}
                          value="unassigned"
                        />
                        <RadioButton
                          labelText={t('customFiltersModalAssigneeMe')}
                          value="me"
                        />
                        <RadioButton
                          labelText={t(
                            'customFiltersModalAssigneeUserAndGround',
                          )}
                          value="user-and-group"
                        />
                      </RadioButtonGroup>
                    )}
                  </Field>

                  <Field name="status">
                    {({input}) => (
                      <RadioButtonGroup
                        legendText={t('customFiltersModalStatusLabel')}
                        name={input.name}
                        onChange={input.onChange}
                        valueSelected={input.value}
                        defaultSelected="all"
                        orientation="vertical"
                        className={styles.secondColumn}
                      >
                        <RadioButton
                          labelText={t('customFiltersModalStatusAll')}
                          value="all"
                        />
                        <RadioButton
                          labelText={t('customFiltersModalStatusOpen')}
                          value="open"
                        />
                        <RadioButton
                          labelText={t('customFiltersModalStatusCompleted')}
                          value="completed"
                        />
                      </RadioButtonGroup>
                    )}
                  </Field>

                  {values?.assignee === 'user-and-group' ? (
                    <>
                      <Field name="assignedTo">
                        {({input}) => (
                          <TextInput
                            {...input}
                            id={input.name}
                            labelText={t('customFiltersModalAssignedToLabel')}
                            placeholder={t(
                              'customFiltersModalAssignedToPlaceholder',
                            )}
                          />
                        )}
                      </Field>
                      <Field name="candidateGroup">
                        {({input}) =>
                          groups.length === 0 ? (
                            <TextInput
                              {...input}
                              id={input.name}
                              labelText={t('customFiltersModalInAGroupLabel')}
                              className={styles.secondColumn}
                              placeholder={t(
                                'customFiltersModalInAGroupPlaceholder',
                              )}
                              disabled={currentUser === undefined}
                            />
                          ) : (
                            <Select
                              {...input}
                              id={input.name}
                              labelText={t('customFiltersModalInAGroupLabel')}
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
                        tenantId={values?.tenant}
                        disabled={!isOpen}
                        labelText={t(
                          'customFiltersModalLatestProcessVersionLabel',
                        )}
                      />
                    )}
                  </Field>
                  {isMultitenancyEnabled ? (
                    <Field name="tenant">
                      {({input}) => (
                        <MultitenancySelect
                          {...input}
                          id={input.name}
                          labelText={t('multiTenancyDropdownLabel')}
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
                        labelA={t(
                          'customFiltersModalAdvancedFiltersToggleHidden',
                        )}
                        labelB={t(
                          'customFiltersModalAdvancedFiltersToggleVisible',
                        )}
                        toggled={input.value}
                        onToggle={input.onChange}
                      />
                    )}
                  </Field>

                  {values?.areAdvancedFiltersEnabled ? (
                    <>
                      <FormGroup
                        className={styles.dateRangeFormGroup}
                        legendText={t('customFiltersModalDueDateLabel')}
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
                              dateFormat={t('globalWrittenCalendarDateFormat')}
                              locale={{locale: i18n.resolvedLanguage}}
                            >
                              <DatePickerInput
                                id="due-date-from"
                                placeholder={t('globalDatePlaceholder')}
                                labelText={t('customFiltersModalFromLabel')}
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
                              dateFormat={t('globalWrittenCalendarDateFormat')}
                              locale={{locale: i18n.resolvedLanguage}}
                            >
                              <DatePickerInput
                                id="due-date-to"
                                placeholder={t('globalDatePlaceholder')}
                                labelText={t('customFiltersModalToLabel')}
                                size="md"
                              />
                            </DatePicker>
                          )}
                        </Field>
                      </FormGroup>

                      <FormGroup
                        legendText={t('customFiltersModalFollowUpDateLabel')}
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
                              dateFormat={t('globalWrittenCalendarDateFormat')}
                              locale={{locale: i18n.resolvedLanguage}}
                            >
                              <DatePickerInput
                                id="follow-up-date-from"
                                placeholder={t('globalDatePlaceholder')}
                                labelText={t('customFiltersModalFromLabel')}
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
                              dateFormat={t('globalWrittenCalendarDateFormat')}
                              locale={{locale: i18n.resolvedLanguage}}
                            >
                              <DatePickerInput
                                id="follow-up-date-to"
                                placeholder={t('globalDatePlaceholder')}
                                labelText={t('customFiltersModalToLabel')}
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
                            labelText={t('customFiltersModalTaskIDLabel')}
                          />
                        )}
                      </Field>

                      <FieldArray name="variables">
                        {({fields, meta: arrayMeta}) => (
                          <>
                            <FormGroup
                              className={styles.variableFormGroup}
                              legendText={t(
                                'customFiltersModalTaskVariableLabel',
                              )}
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
                                          labelText={t(
                                            'taskDetailsNewVariableNameFieldLabel',
                                          )}
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
                                          labelText={t(
                                            'customFiltersModalVariableValueLabel',
                                          )}
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
                                      iconDescription={t(
                                        'customFiltersModalRemoveVariableButton',
                                      )}
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
                                iconDescription={t(
                                  'customFiltersModalAddVariableButtonAria',
                                )}
                                renderIcon={Add}
                                kind="tertiary"
                                size="md"
                                onClick={() =>
                                  fields.push({name: '', value: ''})
                                }
                              >
                                {t('customFiltersModalAddVariableButton')}
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
                    form.reset(
                      values?.name === undefined
                        ? DEFAULT_FORM_VALUES
                        : {
                            ...DEFAULT_FORM_VALUES,
                            name: values.name,
                          },
                    );
                  }}
                  type="button"
                >
                  {t('customFiltersModalResetButton')}
                </Button>
                {initialValues?.name === undefined ? (
                  <>
                    <Button kind="secondary" onClick={onClose} type="button">
                      {t('customFiltersModalCancelButton')}
                    </Button>
                    <Button
                      kind="secondary"
                      onClick={() => {
                        form.change('action', 'save');
                        form.submit();
                      }}
                      type="submit"
                    >
                      {t('customFiltersModalSaveButton')}
                    </Button>
                    <Button
                      kind="primary"
                      type="submit"
                      onClick={() => {
                        form.change('action', 'apply');
                        form.submit();
                      }}
                    >
                      {t('customFiltersModalApplyButton')}
                    </Button>
                  </>
                ) : (
                  <>
                    <Button kind="secondary" onClick={onDelete} type="button">
                      {t('customFiltersModalDeleteButton')}
                    </Button>
                    <Button kind="secondary" onClick={onClose} type="button">
                      {t('customFiltersModalCancelButton')}
                    </Button>
                    <Button
                      kind="primary"
                      onClick={() => {
                        form.change('action', 'edit');
                        form.submit();
                      }}
                      type="submit"
                    >
                      {t('customFiltersModalSaveAndApplyButton')}
                    </Button>
                  </>
                )}
              </ModalFooter>
            </>
          )}
        </Form>
      ) : null}
    </Modal>
  );
};

export {FieldsModal};
