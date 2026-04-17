/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef} from 'react';
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
import {useVariable} from 'modules/queries/variables/useVariable';
import {InlineJsonEditor} from 'modules/components/InlineJsonEditor';

type Props = {
  scopeId: string | null;
  isModificationModeEnabled?: boolean;
  isVariableModificationAllowed?: boolean;
};

type VariableValueCellProps = {
  variableKey: string;
  variableName: string;
  value: string;
  isTruncated: boolean | undefined;
  isModificationModeEnabled: boolean | undefined;
  isProcessInstanceRunning: boolean | undefined;
};

const VariableValueCell: React.FC<VariableValueCellProps> = ({
  variableKey,
  variableName,
  value,
  isTruncated,
  isModificationModeEnabled,
  isProcessInstanceRunning,
}) => {
  // Query is disabled by default and only executed on-demand when copying truncated values
  const {refetch} = useVariable(variableKey, {enabled: false});

  return (
    <InlineJsonEditor
      value={value}
      isTruncatedValue={Boolean(isTruncated)}
      readOnly
      onCopy={
        isTruncated
          ? async () => {
              const result = await refetch();
              if (result.data) {
                return result.data.value;
              }
              throw result.error ?? new Error(`Failed to fetch variable: ${variableName}`);
            }
          : undefined
      }
      renderButton={
        isTruncated
          ? () => (
              <ViewFullVariableButton
                mode="show"
                variableKey={variableKey}
                variableName={variableName}
                variableValue={value}
                buttonLabel="Show all"
                canEdit={
                  !isModificationModeEnabled && !!isProcessInstanceRunning
                }
              />
            )
          : undefined
      }
    />
  );
};

const VariablesTable: React.FC<Props> = ({
  scopeId,
  isModificationModeEnabled,
  isVariableModificationAllowed,
}) => {
  const {data: isProcessInstanceRunning} = useIsProcessInstanceRunning();
  const {initialValues} = useFormState();
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

  const rows =
    variablesData?.pages?.flatMap((page) =>
      page.items.map(({name, value, variableKey, isTruncated}) => ({
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
            cellContent: (
              <Operations>
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
                  if (isModificationModeEnabled || !isProcessInstanceRunning) {
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
                        form.reset({name, value});
                        form.change('value', value);
                      }}
                      hasIconOnly
                      renderIcon={Edit}
                    />
                  );
                })()}
              </Operations>
            ),
            width: '120px',
          },
        ],
      })),
    ) ?? [];

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
