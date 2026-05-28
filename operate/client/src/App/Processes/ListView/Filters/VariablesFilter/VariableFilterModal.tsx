/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, useEffect, useRef, useState} from 'react';
import {
  Button,
  ContentSwitcher,
  InlineNotification,
  Modal,
  Stack,
  Switch,
} from '@carbon/react';
import {Add} from '@carbon/react/icons';
import {Field, Form} from 'react-final-form';
import type {FormApi} from 'final-form';
import {FieldArray} from 'react-final-form-arrays';
import arrayMutators from 'final-form-arrays';
import {useNavigate, useLocation} from 'react-router-dom';
import truncate from 'lodash/truncate';
import {beautifyJSON} from 'modules/utils/editor/beautifyJSON';
import {Paths} from 'modules/Routes';
import {CopyButton} from 'modules/components/CopyButton';
import {IS_VARIABLE_FILTER_V2_ENABLED} from 'modules/feature-flags';
import {VariableFilterRow} from './VariableFilterRow';
import {
  variableFilterStore,
  type VariableCondition,
} from 'modules/stores/variableFilter';
import type {DraftCondition} from './constants';
import {
  validateCondition,
  hasErrors,
  mapToVariableCondition,
  type RowErrors,
} from './validation';
import {
  serializeConditions,
  parseConditionsJson,
  findConditionRanges,
} from './conditionsJsonCodec';
import {
  ConditionRowsScroll,
  Description,
  EditorToolbar,
  JsonEditorWrap,
  ModalContent,
  SwitcherWrap,
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

type Tab = 'fields' | 'json';

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
  setCustomMarkers: (
    markers: Array<{
      message: string;
      startLineNumber: number;
      startColumn: number;
      endLineNumber: number;
      endColumn: number;
      severity?: 'error' | 'warning' | 'info';
    }>,
  ) => void;
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

  const jsonEditorRef = useRef<EditorRef | null>(null);

  const [tab, setTab] = useState<Tab>('fields');
  const [jsonDraft, setJsonDraft] = useState('');
  const [jsonError, setJsonError] = useState<string | null>(null);
  const [jsonParseWarning, setJsonParseWarning] = useState<string | null>(null);

  const showSwitcher = IS_VARIABLE_FILTER_V2_ENABLED;

  useEffect(() => {
    if (isEditorValid) {
      editorRef.current?.hideMarkers();
    }
  }, [isEditorValid]);

  const handleTabChange = (newTab: Tab, form: FormApi<FormValues>) => {
    if (newTab === 'json') {
      if (jsonParseWarning === null) {
        const current = form.getState().values?.conditions ?? [];
        setJsonDraft(serializeConditions(current));
      }
      setJsonError(null);
      setJsonParseWarning(null);
    } else {
      const result = parseConditionsJson(jsonDraft);
      if (result.ok) {
        form.change('conditions', result.conditions);
        setJsonParseWarning(null);
      } else {
        setJsonParseWarning(
          'JSON could not be parsed. Switch back to the JSON tab to fix it. Existing conditions were kept.',
        );
      }
      setJsonError(null);
    }
    setTab(newTab);
  };

  const applyFromJson = () => {
    const result = parseConditionsJson(jsonDraft);
    if (!result.ok) {
      if (result.kind === 'syntax') {
        jsonEditorRef.current?.showMarkers();
      } else {
        const ranges = findConditionRanges(jsonDraft);
        const markers = result.error.split('; ').map((msg) => {
          const match = msg.match(/^Condition #(\d+)/);
          const conditionIndex = match ? parseInt(match[1]!, 10) - 1 : 0;
          const range = ranges[conditionIndex];
          return {
            message: msg,
            startLineNumber: range?.startLine ?? 1,
            startColumn: range?.startColumn ?? 1,
            endLineNumber: range?.endLine ?? 1,
            endColumn: range?.endColumn ?? 2,
            severity: 'error' as const,
          };
        });
        jsonEditorRef.current?.setCustomMarkers(markers);
        jsonEditorRef.current?.showMarkers();
      }
      setJsonError(result.error);
      return;
    }

    const conditionErrors = result.conditions.map(validateCondition);
    const errorMessages = conditionErrors
      .map((errors, i) => {
        if (!hasErrors(errors)) {
          return null;
        }
        const parts = [errors.name, errors.value].filter(Boolean);
        return `Condition #${i + 1}: ${parts.join(', ')}`;
      })
      .filter(Boolean);

    if (errorMessages.length > 0) {
      const ranges = findConditionRanges(jsonDraft);
      const markers = conditionErrors.flatMap((errors, i) => {
        if (!hasErrors(errors)) {
          return [];
        }
        const range = ranges[i];
        const parts = [errors.name, errors.value].filter(Boolean);
        return [
          {
            message: parts.join(', '),
            startLineNumber: range?.startLine ?? 1,
            startColumn: range?.startColumn ?? 1,
            endLineNumber: range?.endLine ?? 1,
            endColumn: range?.endColumn ?? 2,
            severity: 'error' as const,
          },
        ];
      });
      jsonEditorRef.current?.setCustomMarkers(markers);
      jsonEditorRef.current?.showMarkers();
      setJsonError(errorMessages.join('; '));
      return;
    }

    jsonEditorRef.current?.setCustomMarkers([]);
    setJsonError(null);
    handleApplyConditions(result.conditions.map(mapToVariableCondition));
  };

  const handleSubmit = (
    values: FormValues,
  ): {conditions: RowErrors[]} | undefined => {
    const conditionErrors = values.conditions.map(validateCondition);
    if (conditionErrors.some(hasErrors)) {
      return {conditions: conditionErrors};
    }
    handleApplyConditions(values.conditions.map(mapToVariableCondition));
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
              } else if (tab === 'json') {
                applyFromJson();
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
                  {showSwitcher && (
                    <SwitcherWrap>
                      <ContentSwitcher
                        size="sm"
                        selectedIndex={tab === 'fields' ? 0 : 1}
                        onChange={(e) => {
                          handleTabChange(
                            e.index === 0 ? 'fields' : 'json',
                            form,
                          );
                        }}
                      >
                        <Switch name="fields" text="Fields" />
                        <Switch name="json" text="JSON" />
                      </ContentSwitcher>
                    </SwitcherWrap>
                  )}
                  {tab === 'fields' ? (
                    <>
                      {jsonParseWarning !== null && (
                        <InlineNotification
                          kind="warning"
                          lowContrast
                          hideCloseButton
                          subtitle={jsonParseWarning}
                          role="status"
                        />
                      )}
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
                                        form.getState().values?.[
                                          'conditions'
                                        ]?.[i]?.value ?? '';
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
                            {form
                              .getState()
                              .values?.conditions?.some(
                                (c) => c.operator === 'contains',
                              ) && (
                              <InlineNotification
                                kind="warning"
                                lowContrast
                                hideCloseButton
                                subtitle='"contains" searches only the first ~8 000 characters of a variable value. Matches in longer values may not be returned.'
                                role="status"
                              />
                            )}
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
                    </>
                  ) : (
                    <Stack gap={4}>
                      <EditorToolbar>
                        <CopyButton value={jsonDraft} />
                      </EditorToolbar>
                      <JsonEditorWrap $invalid={jsonError !== null}>
                        <Suspense>
                          <RichTextEditor
                            value={jsonDraft}
                            onChange={(v) => {
                              setJsonDraft(v);
                              if (jsonError !== null) {
                                jsonEditorRef.current?.setCustomMarkers([]);
                                setJsonError(null);
                              }
                            }}
                            onMount={(editor) => {
                              jsonEditorRef.current = editor;
                            }}
                            height="300px"
                          />
                        </Suspense>
                      </JsonEditorWrap>
                      {jsonError !== null && (
                        <InlineNotification
                          kind="error"
                          lowContrast
                          hideCloseButton
                          title="Could not apply JSON"
                          subtitle={jsonError}
                          role="alert"
                        />
                      )}
                    </Stack>
                  )}
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
