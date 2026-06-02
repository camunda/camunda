/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, useEffect, useRef, useState} from 'react';
import {Button, InlineNotification, Modal, Stack} from '@carbon/react';
import {Add} from '@carbon/react/icons';
import {Field, Form} from 'react-final-form';
import {FieldArray} from 'react-final-form-arrays';
import arrayMutators from 'final-form-arrays';
import {useNavigate, useLocation} from 'react-router-dom';
import truncate from 'lodash/truncate';
import {isValidJSON} from 'modules/utils';
import {beautifyJSON} from 'modules/utils/editor/beautifyJSON';
import {Paths} from 'modules/Routes';
import {CopyButton} from 'modules/components/CopyButton';
import {VariableFilterRow} from './VariableFilterRow';
import {
  variableFilterStore,
  type VariableCondition,
} from 'modules/stores/variableFilter';
import type {DraftCondition} from './constants';
import {
  ConditionRowsScroll,
  Description,
  EditorToolbar,
  ModalContent,
} from './styled';
import {observer} from 'mobx-react-lite';

const RichTextEditor = lazy(async () => {
  const [{loadMonaco}, {RichTextEditor}] = await Promise.all([
    import('modules/loadMonaco'),
    import('modules/components/RichTextEditor'),
  ]);
  loadMonaco();
  return {default: RichTextEditor};
});

const SOFT_WARNING_THRESHOLD = 8;

type RowErrors = {
  name?: string;
  value?: string;
};

const validateCondition = (condition: DraftCondition): RowErrors => {
  const errors: RowErrors = {};

  if (!condition.name?.trim()) {
    errors.name = 'Variable name is required';
  }

  if (
    condition.operator !== 'exists' &&
    condition.operator !== 'doesNotExist'
  ) {
    if (!condition.value?.trim()) {
      errors.value = 'Value is required';
    } else if (condition.operator === 'oneOf') {
      let parsed: unknown;
      try {
        parsed = JSON.parse(condition.value);
      } catch {
        // handled below
      }
      if (!Array.isArray(parsed)) {
        errors.value = 'Value must be a JSON array (e.g. ["val1", "val2"])';
      }
    } else if (
      condition.operator !== 'contains' &&
      !isValidJSON(condition.value)
    ) {
      errors.value = 'Value must be valid JSON';
    }
  }

  return errors;
};

const hasErrors = (errors: RowErrors) => Object.keys(errors).length > 0;

type FormValues = {
  conditions: DraftCondition[];
};

const createDraft = (): DraftCondition => ({
  name: '',
  operator: 'equals',
  value: '',
});

type EditorRef = {
  showMarkers: () => void;
  hideMarkers: () => void;
};

const VariableFilterModal: React.FC = observer(() => {
  const {conditions} = variableFilterStore;
  const initialValues = useRef<FormValues>({
    conditions: conditions.length > 0 ? [...conditions] : [createDraft()],
  });
  const navigate = useNavigate();
  const location = useLocation();
  const handleCloseModal = () => {
    navigate({pathname: Paths.processes(), search: location.search});
  };

  const handleApplyConditions = (newConditions: VariableCondition[]) => {
    variableFilterStore.setConditions(newConditions);
    navigate({pathname: Paths.processes(), search: location.search});
  };

  const [editingRowIndex, setEditingRowIndex] = useState<number | null>(null);
  const [isEditorValid, setIsEditorValid] = useState(true);
  const editorRef = useRef<EditorRef | null>(null);
  const preEditValueRef = useRef('');

  useEffect(() => {
    if (isEditorValid) {
      editorRef.current?.hideMarkers();
    }
  }, [isEditorValid]);

  const handleSubmit = (
    values: FormValues,
  ): {conditions: RowErrors[]} | undefined => {
    const conditionErrors = values.conditions.map(validateCondition);
    if (conditionErrors.some(hasErrors)) {
      return {conditions: conditionErrors};
    }
    handleApplyConditions(
      values.conditions.map(({operator, name, value}): VariableCondition => {
        if (operator === 'exists' || operator === 'doesNotExist') {
          return {name, operator, value: ''};
        }
        return {name, operator, value: value ?? ''};
      }),
    );
    return undefined;
  };

  const isEditing = editingRowIndex !== null;

  return (
    <Form<FormValues>
      onSubmit={handleSubmit}
      initialValues={initialValues.current}
      mutators={{...arrayMutators}}
    >
      {({handleSubmit: submitForm, form}) => {
        const changeField = form.change as (
          name: string,
          value?: unknown,
        ) => void;
        const editingVariableName = isEditing
          ? form
              .getState()
              .values?.['conditions']?.[editingRowIndex]?.name?.trim()
          : undefined;

        return (
          <Modal
            open
            modalLabel={isEditing ? 'Filter by variable' : undefined}
            modalHeading={
              isEditing
                ? editingVariableName
                  ? `Edit value: ${truncate(editingVariableName, {length: 50})}`
                  : 'Edit variable value'
                : 'Filter by variable'
            }
            primaryButtonText={isEditing ? 'Save' : 'Apply'}
            secondaryButtonText="Cancel"
            onRequestSubmit={() => {
              if (isEditing) {
                if (isEditorValid) {
                  setEditingRowIndex(null);
                } else {
                  editorRef.current?.showMarkers();
                }
              } else {
                submitForm();
              }
            }}
            onRequestClose={() => {
              if (isEditing) {
                changeField(
                  `conditions[${editingRowIndex}].value`,
                  preEditValueRef.current,
                );
                setEditingRowIndex(null);
              } else {
                handleCloseModal();
              }
            }}
            onSecondarySubmit={() => {
              if (isEditing) {
                changeField(
                  `conditions[${editingRowIndex}].value`,
                  preEditValueRef.current,
                );
                setEditingRowIndex(null);
              } else {
                handleCloseModal();
              }
            }}
            preventCloseOnClickOutside
            size="md"
          >
            <ModalContent>
              {isEditing && (
                <Field
                  name={`conditions[${editingRowIndex}].value`}
                  subscription={{value: true}}
                >
                  {({input: editorInput}) => (
                    <Stack gap={4}>
                      <EditorToolbar>
                        <CopyButton value={editorInput.value} />
                      </EditorToolbar>
                      <Suspense>
                        <RichTextEditor
                          value={editorInput.value}
                          onChange={editorInput.onChange}
                          onValidate={setIsEditorValid}
                          onMount={(editor) => {
                            editorRef.current = editor;
                          }}
                          height="45vh"
                        />
                      </Suspense>
                    </Stack>
                  )}
                </Field>
              )}
              <div style={{display: isEditing ? 'none' : undefined}}>
                <Stack gap={5}>
                  <Description>
                    Define one or more conditions to filter process instances by
                    variable values. All conditions are combined with AND logic.
                  </Description>
                  <FieldArray<DraftCondition> name="conditions">
                    {({fields}) => (
                      <>
                        <ConditionRowsScroll>
                          <Stack gap={4}>
                            {fields.map((fieldName, index) => (
                              <VariableFilterRow
                                key={fieldName}
                                fieldName={fieldName}
                                rowIndex={index}
                                onDelete={() => fields.remove(index)}
                                isDeleteHidden={fields.length === 1}
                                onEditValue={(i) => {
                                  const val =
                                    form.getState().values?.['conditions']?.[i]
                                      ?.value ?? '';
                                  preEditValueRef.current = val;
                                  changeField(
                                    `conditions[${i}].value`,
                                    beautifyJSON(val),
                                  );
                                  setEditingRowIndex(i);
                                }}
                              />
                            ))}
                          </Stack>
                        </ConditionRowsScroll>
                        {(fields.length ?? 0) >= SOFT_WARNING_THRESHOLD && (
                          <InlineNotification
                            kind="info"
                            lowContrast
                            hideCloseButton
                            subtitle="Filtering by many conditions can be slow. Add conditions only if you need them."
                            role="status"
                          />
                        )}
                        <Button
                          kind="ghost"
                          size="sm"
                          renderIcon={Add}
                          onClick={() => fields.push(createDraft())}
                        >
                          Add condition
                        </Button>
                      </>
                    )}
                  </FieldArray>
                </Stack>
              </div>
            </ModalContent>
          </Modal>
        );
      }}
    </Form>
  );
});

export {VariableFilterModal};
