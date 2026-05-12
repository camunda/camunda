/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, useCallback, useEffect, useState} from 'react';
import {Button, Dropdown, Modal, Stack} from '@carbon/react';
import {Add, ArrowLeft} from '@carbon/react/icons';
import {Form} from 'react-final-form';
import {FieldArray} from 'react-final-form-arrays';
import arrayMutators from 'final-form-arrays';
import {observer} from 'mobx-react-lite';
import {isValidJSON} from 'modules/utils';
import {EDITOR_MODE_TOGGLE} from 'modules/feature-flags';
import {editorModeStore, type EditorMode} from 'modules/stores/editorMode';
import {VariableFilterRow} from './VariableFilterRow';
import type {VariableCondition} from 'modules/stores/variableFilter';
import type {DraftCondition} from './constants';
import {
  BackButtonContent,
  Description,
  DimmedParentModalStyle,
  ExpandedEditorHeader,
  ExpandedEditorSection,
  ModalContent,
  ModeToggleContainer,
} from './styled';

const JSONEditor = lazy(async () => {
  const [{loadMonaco}, {JSONEditor}] = await Promise.all([
    import('modules/loadMonaco'),
    import('modules/components/JSONEditor'),
  ]);
  loadMonaco();
  return {default: JSONEditor};
});

const MAX_CONDITIONS = 5;

const EDITOR_MODE_OPTIONS: {id: EditorMode; label: string}[] = [
  {id: 'default', label: 'Default (Portal Modal)'},
  {id: 'inline', label: 'Full-Width Expandable'},
  {id: 'step', label: 'Step-Based Transition'},
  {id: 'slideOver', label: 'Expanded Modal'},
];

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

type Props = {
  isOpen: boolean;
  initialConditions: VariableCondition[];
  onApply: (conditions: VariableCondition[]) => void;
  onClose: () => void;
};

type FormValues = {
  conditions: DraftCondition[];
};

const createDraft = (): DraftCondition => ({
  name: '',
  operator: 'equals',
  value: '',
});

const VariableFilterModal: React.FC<Props> = observer(
  ({isOpen, initialConditions, onApply, onClose}) => {
    const editorMode = EDITOR_MODE_TOGGLE ? editorModeStore.mode : 'default';
    const [initialDraftConditions] = useState<DraftCondition[]>(() =>
      initialConditions.length > 0
        ? initialConditions.map((c) => ({...c}))
        : [createDraft()],
    );

    const [isJsonEditorOpen, setIsJsonEditorOpen] = useState(false);
    const handleJsonEditorOpen = useCallback(
      () => setIsJsonEditorOpen(true),
      [],
    );
    const handleJsonEditorClose = useCallback(
      () => setIsJsonEditorOpen(false),
      [],
    );

    // Option A: Inline mode
    const [expandedRowIndex, setExpandedRowIndex] = useState<number | null>(
      null,
    );

    // Option B: Step mode
    const [editingRowIndex, setEditingRowIndex] = useState<number | null>(null);
    const [editedValue, setEditedValue] = useState('');

    // Option C: Slide-over mode
    const [slideOverRowIndex, setSlideOverRowIndex] = useState<number | null>(
      null,
    );
    const [slideOverValue, setSlideOverValue] = useState('');

    // Reset mode-specific state when mode changes
    useEffect(() => {
      setExpandedRowIndex(null);
      setEditingRowIndex(null);
      setSlideOverRowIndex(null);
    }, [editorMode]);

    const handleSubmit = (
      values: FormValues,
    ): {conditions: RowErrors[]} | undefined => {
      const conditionErrors = values.conditions.map(validateCondition);
      if (conditionErrors.some(hasErrors)) {
        return {conditions: conditionErrors};
      }
      onApply(
        values.conditions.map(({operator, name, value}): VariableCondition => {
          if (operator === 'exists' || operator === 'doesNotExist') {
            return {name, operator, value: ''};
          }
          return {name, operator, value: value ?? ''};
        }),
      );
      return undefined;
    };

    return (
      <Form<FormValues>
        onSubmit={handleSubmit}
        initialValues={{conditions: initialDraftConditions}}
        mutators={{...arrayMutators}}
      >
        {({handleSubmit: submitForm, form}) => (
          <Modal
            open={isOpen}
            modalHeading={
              editorMode === 'step' && editingRowIndex !== null
                ? (() => {
                    const variableName = form
                      .getState()
                      .values?.['conditions']?.[editingRowIndex]?.name?.trim();
                    return variableName
                      ? `Edit value: ${variableName}`
                      : 'Edit Variable Value';
                  })()
                : 'Filter by Variable'
            }
            primaryButtonText={
              (editorMode === 'step' && editingRowIndex !== null) ||
              (editorMode === 'slideOver' && slideOverRowIndex !== null)
                ? 'Save value'
                : 'Apply'
            }
            secondaryButtonText="Cancel"
            onRequestSubmit={() => {
              if (editorMode === 'step' && editingRowIndex !== null) {
                (
                  form as {change: (name: string, value: string) => void}
                ).change(`conditions[${editingRowIndex}].value`, editedValue);
                setEditingRowIndex(null);
              } else if (
                editorMode === 'slideOver' &&
                slideOverRowIndex !== null
              ) {
                (
                  form as {change: (name: string, value: string) => void}
                ).change(
                  `conditions[${slideOverRowIndex}].value`,
                  slideOverValue,
                );
                setSlideOverRowIndex(null);
              } else {
                submitForm();
              }
            }}
            onRequestClose={() => {
              if (editorMode === 'step' && editingRowIndex !== null) {
                setEditingRowIndex(null);
              } else if (
                editorMode === 'slideOver' &&
                slideOverRowIndex !== null
              ) {
                setSlideOverRowIndex(null);
              } else {
                onClose();
              }
            }}
            onSecondarySubmit={() => {
              if (editorMode === 'step' && editingRowIndex !== null) {
                setEditingRowIndex(null);
              } else if (
                editorMode === 'slideOver' &&
                slideOverRowIndex !== null
              ) {
                setSlideOverRowIndex(null);
              } else {
                onClose();
              }
            }}
            preventCloseOnClickOutside
            selectorsFloatingMenus={['.variable-filter-json-editor']}
            size={
              editorMode === 'slideOver' && slideOverRowIndex !== null
                ? 'lg'
                : 'md'
            }
            className={
              isJsonEditorOpen ? 'variable-filter-modal--dimmed' : undefined
            }
          >
            <DimmedParentModalStyle />
            <ModalContent>
              {editorMode === 'step' && editingRowIndex !== null ? (
                <Stack gap={5}>
                  <Button
                    kind="ghost"
                    size="sm"
                    onClick={() => setEditingRowIndex(null)}
                  >
                    <BackButtonContent>
                      <ArrowLeft size={16} />
                      Back to conditions
                    </BackButtonContent>
                  </Button>
                  <Suspense fallback={<div>Loading editor...</div>}>
                    <JSONEditor
                      value={editedValue}
                      onChange={setEditedValue}
                      height="45vh"
                    />
                  </Suspense>
                </Stack>
              ) : (
                <Stack gap={5}>
                  <Description>
                    Define one or more conditions to filter process instances by
                    variable values. All conditions are combined with AND logic.
                  </Description>
                  <FieldArray<DraftCondition> name="conditions">
                    {({fields}) => (
                      <Stack gap={4}>
                        {fields.map((fieldName, index) => (
                          <VariableFilterRow
                            key={fieldName}
                            fieldName={fieldName}
                            rowIndex={index}
                            onDelete={() => {
                              if (expandedRowIndex === index) {
                                setExpandedRowIndex(null);
                              }
                              if (slideOverRowIndex === index) {
                                setSlideOverRowIndex(null);
                              }
                              fields.remove(index);
                            }}
                            isDeleteHidden={fields.length === 1}
                            onJsonEditorOpen={handleJsonEditorOpen}
                            onJsonEditorClose={handleJsonEditorClose}
                            onExpandRow={(i) => setExpandedRowIndex(i)}
                            onEditValue={(i) => {
                              const val =
                                form.getState().values?.['conditions']?.[i]
                                  ?.value ?? '';
                              setEditedValue(val);
                              setEditingRowIndex(i);
                            }}
                            onSlideOverOpen={(i) => {
                              const val =
                                form.getState().values?.['conditions']?.[i]
                                  ?.value ?? '';
                              setSlideOverValue(val);
                              setSlideOverRowIndex(i);
                            }}
                          />
                        ))}
                        <Button
                          kind="ghost"
                          size="sm"
                          renderIcon={Add}
                          disabled={(fields.length ?? 0) >= MAX_CONDITIONS}
                          onClick={() => fields.push(createDraft())}
                        >
                          Add condition
                        </Button>
                      </Stack>
                    )}
                  </FieldArray>
                  {editorMode === 'inline' && (
                    <ExpandedEditorSection $isOpen={expandedRowIndex !== null}>
                      {expandedRowIndex !== null && (
                        <>
                          <ExpandedEditorHeader>
                            <h4>
                              {(() => {
                                const variableName = form
                                  .getState()
                                  .values?.[
                                    'conditions'
                                  ]?.[expandedRowIndex]?.name?.trim();
                                return variableName
                                  ? `Edit value: ${variableName}`
                                  : 'Edit Variable Value';
                              })()}
                            </h4>
                            <Button
                              kind="ghost"
                              size="sm"
                              onClick={() => setExpandedRowIndex(null)}
                            >
                              Done
                            </Button>
                          </ExpandedEditorHeader>
                          <Suspense fallback={<div>Loading editor...</div>}>
                            <JSONEditor
                              value={
                                form.getState().values?.['conditions']?.[
                                  expandedRowIndex
                                ]?.value ?? ''
                              }
                              onChange={(val: string) => {
                                (
                                  form as {
                                    change: (
                                      name: string,
                                      value: string,
                                    ) => void;
                                  }
                                ).change(
                                  `conditions[${expandedRowIndex}].value`,
                                  val,
                                );
                              }}
                              height="35vh"
                            />
                          </Suspense>
                        </>
                      )}
                    </ExpandedEditorSection>
                  )}
                  {editorMode === 'slideOver' && (
                    <ExpandedEditorSection $isOpen={slideOverRowIndex !== null}>
                      {slideOverRowIndex !== null && (
                        <>
                          <ExpandedEditorHeader>
                            <h4>
                              {(() => {
                                const variableName = form
                                  .getState()
                                  .values?.[
                                    'conditions'
                                  ]?.[slideOverRowIndex]?.name?.trim();
                                return variableName
                                  ? `Edit value: ${variableName}`
                                  : 'Edit Variable Value';
                              })()}
                            </h4>
                            <Button
                              kind="ghost"
                              size="sm"
                              onClick={() => setSlideOverRowIndex(null)}
                            >
                              Done
                            </Button>
                          </ExpandedEditorHeader>
                          <Suspense fallback={<div>Loading editor...</div>}>
                            <JSONEditor
                              value={slideOverValue}
                              onChange={setSlideOverValue}
                              height="35vh"
                            />
                          </Suspense>
                        </>
                      )}
                    </ExpandedEditorSection>
                  )}
                  {EDITOR_MODE_TOGGLE && (
                    <ModeToggleContainer>
                      <Dropdown
                        id="editor-mode-toggle"
                        titleText="UX Mode (PoC)"
                        label="Select editor mode"
                        size="sm"
                        items={EDITOR_MODE_OPTIONS}
                        itemToString={(item) => item?.label ?? ''}
                        selectedItem={
                          EDITOR_MODE_OPTIONS.find(
                            (opt) => opt.id === editorMode,
                          ) ?? EDITOR_MODE_OPTIONS[0]!
                        }
                        onChange={({selectedItem}) => {
                          if (selectedItem) {
                            editorModeStore.setMode(selectedItem.id);
                          }
                        }}
                      />
                    </ModeToggleContainer>
                  )}
                </Stack>
              )}
            </ModalContent>
          </Modal>
        )}
      </Form>
    );
  },
);

export {VariableFilterModal};
