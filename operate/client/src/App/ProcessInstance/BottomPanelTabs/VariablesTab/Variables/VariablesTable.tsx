/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useRef} from 'react';
import {useForm, useFormState} from 'react-final-form';
import {Button} from '@carbon/react';
import {Edit} from '@carbon/react/icons';
import {StructuredList, VariableName} from './styled';
import {StructuredRows} from 'modules/components/StructuredList';
import {OnLastVariableModificationRemoved} from './OnLastVariableModificationRemoved';
import {FieldArray} from 'react-final-form-arrays';
import {Operations} from './Operations';
import type {VariableFormValues} from 'modules/types/variables';
import {EditButtons} from './EditButtons';
import {ExistingVariableValue} from './ExistingVariableValue';
import {Name} from './NewVariableModification/Name';
import {Value} from './NewVariableModification/Value';
import {Operation} from './NewVariableModification/Operation';
import {ViewFullVariableButton} from './ViewFullVariableButton';
import {useIsProcessInstanceRunning} from 'modules/queries/processInstance/useIsProcessInstanceRunning';
import {useVariables} from 'modules/queries/variables/useVariables';
import {VariableValueCell} from './VariableValueCell';
import {recognizeDocumentValue} from './documents/recognize';
import {MOCK_DOCUMENT_VARIABLES} from './documents/mockDocumentVariables';
import {DocumentActions} from './documents/DocumentActions';
import type {VariableTypeFilter} from './documents/VariablesToolbar';
import type {Variable} from '@camunda/camunda-api-zod-schemas/8.10';

type Props = {
  scopeId: string | null;
  isModificationModeEnabled?: boolean;
  isVariableModificationAllowed?: boolean;
  searchTerm?: string;
  typeFilter?: VariableTypeFilter;
};

type DisplayedVariable = Pick<
  Variable,
  'name' | 'value' | 'variableKey' | 'isTruncated'
>;

const VariablesTable: React.FC<Props> = ({
  scopeId,
  isModificationModeEnabled,
  isVariableModificationAllowed,
  searchTerm = '',
  typeFilter = 'all',
}) => {
  const {data: isProcessInstanceRunning} = useIsProcessInstanceRunning();
  const {initialValues} = useFormState<VariableFormValues>();
  const form = useForm<VariableFormValues>();
  const variableNameRef = useRef<HTMLDivElement>(null);

  const {
    data: variablesData,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useVariables();

  const isEditMode = (variableName: string) =>
    (initialValues?.name === variableName && isProcessInstanceRunning) ||
    isVariableModificationAllowed;

  // Merge real backend variables with prototype document mocks. Mocks come
  // first so reviewers always see the document cases, even before any real
  // variables paginate in.
  const mergedVariables: DisplayedVariable[] = useMemo(() => {
    const realVariables: DisplayedVariable[] =
      variablesData?.pages?.flatMap((page) =>
        page.items.map(({name, value, variableKey, isTruncated}) => ({
          name,
          value,
          variableKey,
          isTruncated,
        })),
      ) ?? [];

    const realNames = new Set(realVariables.map(({name}) => name));
    const mockedVariables = MOCK_DOCUMENT_VARIABLES.filter(
      ({name}) => !realNames.has(name),
    ).map(({name, value, variableKey, isTruncated}) => ({
      name,
      value,
      variableKey,
      isTruncated,
    }));

    return [...mockedVariables, ...realVariables];
  }, [variablesData]);

  const filteredVariables = useMemo(() => {
    const search = searchTerm.trim().toLowerCase();
    return mergedVariables.filter(({name, value, isTruncated}) => {
      if (search !== '' && !name.toLowerCase().includes(search)) {
        return false;
      }
      if (typeFilter === 'documents') {
        return (
          recognizeDocumentValue(value, Boolean(isTruncated)).kind !== 'none'
        );
      }
      return true;
    });
  }, [mergedVariables, searchTerm, typeFilter]);

  const rows = filteredVariables.map(
    ({name, value, variableKey, isTruncated}) => {
      const recognition = recognizeDocumentValue(value, Boolean(isTruncated));
      // truncated-list still needs the regular variable actions (so the user
      // can "Show all" to load the full payload). single and list fully
      // replace the action column with document-specific buttons.
      const hasDocumentActions =
        recognition.kind === 'single' || recognition.kind === 'list';

      return {
        key: name,
        dataTestId: `variable-${name}`,
        columns: [
          {
            cellContent: (
              <VariableName title={name} ref={variableNameRef}>
                {name}
              </VariableName>
            ),
            width: '35%',
          },
          {
            cellContent: isEditMode(name) ? (
              <ExistingVariableValue
                id={variableKey}
                variableName={name}
                variableValue={value}
                // TODO #46571: Verify if it's really optional
                isPreview={Boolean(isTruncated)}
              />
            ) : (
              <VariableValueCell
                variableKey={variableKey}
                variableName={name}
                value={value}
                isTruncated={isTruncated}
                isModificationModeEnabled={isModificationModeEnabled}
                isProcessInstanceRunning={isProcessInstanceRunning}
              />
            ),
            width: 'auto',
          },
          {
            cellContent: (() => {
              const variableOps = (
                <>
                  <ViewFullVariableButton
                    variableName={name}
                    variableKey={variableKey}
                    variableValue={value}
                    mode={isEditMode(name) ? 'edit' : 'show'}
                    canEdit={
                      !isModificationModeEnabled && !!isProcessInstanceRunning
                    }
                  />
                  {(() => {
                    if (
                      isModificationModeEnabled ||
                      !isProcessInstanceRunning
                    ) {
                      return null;
                    }

                    if (initialValues?.name === name) {
                      return <EditButtons />;
                    }

                    return (
                      <Button
                        kind="ghost"
                        size="sm"
                        tooltipPosition="top"
                        iconDescription="Edit"
                        aria-label={`Edit variable ${name}`}
                        disabled={
                          isFetchingNextPage || form.getState().submitting
                        }
                        onClick={async () => {
                          form.reset({name, value, variableKey});
                          form.change('value', value);
                        }}
                        hasIconOnly
                        renderIcon={Edit}
                      />
                    );
                  })()}
                </>
              );

              return (
                <Operations>
                  {hasDocumentActions ? (
                    <DocumentActions
                      variableName={name}
                      recognition={recognition}
                    />
                  ) : (
                    variableOps
                  )}
                </Operations>
              );
            })(),
            width: '120px',
          },
        ],
      };
    },
  );

  return (
    <StructuredList
      dataTestId="variables-list"
      headerColumns={[
        {cellContent: 'Name', width: '35%'},
        {cellContent: 'Value', width: 'auto'},
        {cellContent: '', width: '120px'},
      ]}
      headerSize="sm"
      verticalCellPadding="var(--cds-spacing-02)"
      label="Variable List"
      onVerticalScrollEndReach={() => {
        if (hasNextPage && !isFetchingNextPage) {
          fetchNextPage();
        }
      }}
      dynamicRows={
        isVariableModificationAllowed ? (
          <>
            <OnLastVariableModificationRemoved />
            <FieldArray name="newVariables">
              {({fields}) => (
                <StructuredRows
                  verticalCellPadding="var(--cds-spacing-02)"
                  rows={fields.map((variableName, index) => ({
                    key: fields.value[index]?.id ?? variableName,
                    dataTestId: `variable-${variableName}`,
                    columns: [
                      {
                        cellContent: (
                          <Name variableName={variableName} scopeId={scopeId} />
                        ),
                        width: '35%',
                      },
                      {
                        cellContent: (
                          <Value
                            variableName={variableName}
                            scopeId={scopeId}
                          />
                        ),
                        width: 'auto',
                      },
                      {
                        cellContent: (
                          <Operations>
                            <ViewFullVariableButton
                              shouldSubmitOnApply={false}
                              mode="add"
                              scopeId={scopeId}
                              variableName={variableName}
                            />
                            <Operation
                              variableName={variableName}
                              onRemove={() => {
                                fields.remove(index);
                              }}
                            />
                          </Operations>
                        ),
                        width: '120px',
                      },
                    ],
                  }))}
                />
              )}
            </FieldArray>
          </>
        ) : undefined
      }
      rows={rows}
    />
  );
};

export {VariablesTable};
