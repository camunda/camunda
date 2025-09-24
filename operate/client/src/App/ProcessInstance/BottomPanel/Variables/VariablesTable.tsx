/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef} from 'react';
import {useForm, useFormState} from 'react-final-form';
import {Button, Loading} from '@carbon/react';
import {Edit} from '@carbon/react/icons';
import {StructuredList, VariableName, VariableValue} from './styled';
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
import {usePermissions} from 'modules/queries/permissions/usePermissions';
import {Restricted} from 'modules/components/Restricted';
import {useVariables} from 'modules/queries/variables/useVariables';
import {getVariable} from 'modules/api/v2/variables/getVariable';

type Props = {
  scopeId: string | null;
  isVariableModificationAllowed?: boolean;
};

const VariablesTable: React.FC<Props> = ({
  scopeId,
  isVariableModificationAllowed,
}) => {
  const {data: isProcessInstanceRunning} = useIsProcessInstanceRunning();
  const {data: permissions} = usePermissions();
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

  const createFetchFullVariableHandler = (variableKey: string) => async (): Promise<string | null> => {
    try {
      const {response} = await getVariable(variableKey);
      return response?.value ?? null;
    } catch {
      return null;
    }
  };

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
                isPreview={isTruncated}
              />
            ) : (
              <VariableValue $hasBackdrop={true}>
                {isFetchingNextPage && (
                  <Loading small data-testid="full-variable-loader" />
                )}
                {value}
              </VariableValue>
            ),
            width: '55%',
          },
          {
            cellContent: (
              <Operations>
                {(() => {
                  if (isVariableModificationAllowed) {
                    return null;
                  }

                  if (!isProcessInstanceRunning) {
                    return (
                      <ViewFullVariableButton 
                        variableName={name} 
                        onClick={isTruncated ? createFetchFullVariableHandler(variableKey) : undefined}
                        value={!isTruncated ? value : undefined}
                      />
                    );
                  }

                  if (initialValues?.name === name) {
                    return <EditButtons />;
                  }

                  return (
                    <Restricted
                      resourceBasedRestrictions={{
                        scopes: ['UPDATE_PROCESS_INSTANCE'],
                        permissions,
                      }}
                      fallback={
                        isTruncated ? (
                          <ViewFullVariableButton 
                            variableName={name} 
                            onClick={createFetchFullVariableHandler(variableKey)}
                          />
                        ) : null
                      }
                    >
                      <Button
                        kind="ghost"
                        size="sm"
                        tooltipPosition="left"
                        iconDescription={`Edit variable ${name}`}
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
                    </Restricted>
                  );
                })()}
              </Operations>
            ),
            width: '10%',
          },
        ],
      })),
    ) ?? [];

  return (
    <StructuredList
      dataTestId="variables-list"
      headerColumns={[
        {cellContent: 'Name', width: '35%'},
        {cellContent: 'Value', width: '55%'},
        {cellContent: '', width: '10%'},
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
                        width: '55%',
                      },
                      {
                        cellContent: (
                          <Operation
                            variableName={variableName}
                            onRemove={() => {
                              fields.remove(index);
                            }}
                          />
                        ),
                        width: '10%',
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
