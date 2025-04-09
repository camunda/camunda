/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {StructuredList, VariableName, VariableValue} from './styled';
import {StructuredRows} from 'modules/components/StructuredList';
import {OnLastVariableModificationRemoved} from './OnLastVariableModificationRemoved';
import {FieldArray} from 'react-final-form-arrays';
import {variablesStore} from 'modules/stores/variables';
import {observer} from 'mobx-react';
import {modificationsStore} from 'modules/stores/modifications';
import {useMemo, useRef} from 'react';
import {Restricted} from 'modules/components/Restricted';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {Button, Loading} from '@carbon/react';
import {useForm, useFormState} from 'react-final-form';
import {Operations} from './Operations';
import {useProcessInstancePageParams} from '../../useProcessInstancePageParams';
import {Edit} from '@carbon/react/icons';
import {VariableFormValues} from 'modules/types/variables';
import {EditButtons} from './EditButtons';
import {ExistingVariableValue} from './ExistingVariableValue';
import {Name} from './NewVariableModification/Name';
import {Value} from './NewVariableModification/Value';
import {Operation} from './NewVariableModification/Operation';
import {Operation as OperationV2} from './NewVariableModification/v2/Operation';
import {ViewFullVariableButton} from './ViewFullVariableButton';
import {MAX_VARIABLES_STORED} from 'modules/constants/variables';
import {notificationsStore} from 'modules/stores/notifications';
import {IS_FLOWNODE_INSTANCE_STATISTICS_V2_ENABLED} from 'modules/feature-flags';

type Props = {
  scopeId: string | null;
  isVariableModificationAllowed?: boolean;
};

const VariablesTable: React.FC<Props> = observer(
  ({scopeId, isVariableModificationAllowed}) => {
    const {
      state: {items, loadingItemId},
    } = variablesStore;
    const {isModificationModeEnabled} = modificationsStore;

    const addVariableModifications = useMemo(
      () => modificationsStore.getAddVariableModifications(scopeId),
      [scopeId],
    );

    const {processInstanceId = ''} = useProcessInstancePageParams();
    const {initialValues} = useFormState();
    const variableNameRef = useRef<HTMLDivElement>(null);

    function fetchFullVariable({
      processInstanceId,
      variableId,
      enableLoading = true,
    }: {
      processInstanceId: ProcessInstanceEntity['id'];
      variableId: VariableEntity['id'];
      enableLoading?: boolean;
    }) {
      return variablesStore.fetchVariable({
        processInstanceId,
        variableId,
        onError: () => {
          notificationsStore.displayNotification({
            kind: 'error',
            title: 'Variable could not be fetched',
            isDismissable: true,
          });
        },
        enableLoading,
      });
    }

    const isEditMode = (variableName: string) =>
      (initialValues?.name === variableName &&
        processInstanceDetailsStore.isRunning) ||
      (isModificationModeEnabled && isVariableModificationAllowed);

    const form = useForm<VariableFormValues>();

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
        onVerticalScrollStartReach={async (scrollDown) => {
          if (variablesStore.shouldFetchPreviousVariables() === false) {
            return;
          }
          await variablesStore.fetchPreviousVariables(processInstanceId);

          if (
            variablesStore.state.items.length === MAX_VARIABLES_STORED &&
            variablesStore.state.latestFetch.itemsCount !== 0
          ) {
            scrollDown(
              variablesStore.state.latestFetch.itemsCount *
                (variableNameRef.current?.closest<HTMLElement>('[role=row]')
                  ?.offsetHeight ?? 0),
            );
          }
        }}
        onVerticalScrollEndReach={() => {
          if (variablesStore.shouldFetchNextVariables() === false) {
            return;
          }
          variablesStore.fetchNextVariables(processInstanceId);
        }}
        dynamicRows={
          isModificationModeEnabled ? (
            <>
              <OnLastVariableModificationRemoved />
              <FieldArray
                name="newVariables"
                initialValue={
                  addVariableModifications.length > 0
                    ? addVariableModifications
                    : undefined
                }
              >
                {({fields}) => (
                  <StructuredRows
                    verticalCellPadding="var(--cds-spacing-02)"
                    rows={fields
                      .map((variableName, index) => {
                        return {
                          key: fields.value[index]?.id ?? variableName,
                          dataTestId: `variable-${variableName}`,
                          columns: [
                            {
                              cellContent: (
                                <Name
                                  variableName={variableName}
                                  scopeId={scopeId}
                                />
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
                              cellContent:
                                IS_FLOWNODE_INSTANCE_STATISTICS_V2_ENABLED ? (
                                  <OperationV2
                                    variableName={variableName}
                                    onRemove={() => {
                                      fields.remove(index);
                                    }}
                                  />
                                ) : (
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
                        };
                      })
                      .reverse()}
                  />
                )}
              </FieldArray>
            </>
          ) : undefined
        }
        rows={items.map(
          ({
            name: variableName,
            value: variableValue,
            hasActiveOperation,
            isPreview,
            id,
          }) => ({
            key: variableName,
            dataTestId: `variable-${variableName}`,
            columns: [
              {
                cellContent: (
                  <VariableName title={variableName} ref={variableNameRef}>
                    {variableName}
                  </VariableName>
                ),
                width: '35%',
              },
              {
                cellContent: isEditMode(variableName) ? (
                  <ExistingVariableValue
                    id={id}
                    variableName={variableName}
                    variableValue={
                      variablesStore.getFullVariableValue(id) ?? variableValue
                    }
                    pauseValidation={
                      isPreview &&
                      variablesStore.getFullVariableValue(id) === undefined
                    }
                    onFocus={() => {
                      if (
                        isPreview &&
                        variablesStore.getFullVariableValue(id) === undefined
                      ) {
                        variablesStore.fetchVariable({
                          processInstanceId,
                          variableId: id,
                          onSuccess: (variable: VariableEntity) => {
                            variablesStore.setFullVariableValue(
                              id,
                              variable.value,
                            );
                          },
                          onError: () => {
                            notificationsStore.displayNotification({
                              kind: 'error',
                              title: 'Variable could not be fetched',
                              isDismissable: true,
                            });
                          },
                        });
                      }
                    }}
                  />
                ) : (
                  <VariableValue $hasBackdrop={true}>
                    {loadingItemId === id && (
                      <Loading small data-testid="full-variable-loader" />
                    )}
                    {variableValue}
                  </VariableValue>
                ),
                width: '55%',
              },
              {
                cellContent: (
                  <Operations
                    showLoadingIndicator={
                      initialValues?.name !== variableName &&
                      !isModificationModeEnabled &&
                      hasActiveOperation
                    }
                  >
                    {(() => {
                      if (isModificationModeEnabled) {
                        return null;
                      }

                      if (!processInstanceDetailsStore.isRunning) {
                        if (isPreview) {
                          return (
                            <ViewFullVariableButton
                              variableName={variableName}
                              onClick={async () => {
                                const variable = await fetchFullVariable({
                                  processInstanceId,
                                  variableId: id,
                                  enableLoading: false,
                                });

                                return variable?.value ?? null;
                              }}
                            />
                          );
                        }

                        return null;
                      }

                      if (initialValues?.name === variableName) {
                        return (
                          <EditButtons
                            onExitEditMode={() =>
                              variablesStore.deleteFullVariableValue(id)
                            }
                          />
                        );
                      }

                      if (!hasActiveOperation) {
                        return (
                          <Restricted
                            resourceBasedRestrictions={{
                              scopes: ['UPDATE_PROCESS_INSTANCE'],
                              permissions:
                                processInstanceDetailsStore.getPermissions(),
                            }}
                            fallback={
                              isPreview ? (
                                <ViewFullVariableButton
                                  variableName={variableName}
                                  onClick={async () => {
                                    const variable = await fetchFullVariable({
                                      processInstanceId,
                                      variableId: id,
                                      enableLoading: false,
                                    });

                                    return variable?.value ?? null;
                                  }}
                                />
                              ) : null
                            }
                          >
                            <Button
                              kind="ghost"
                              size="sm"
                              iconDescription={`Edit variable ${variableName}`}
                              aria-label={`Edit variable ${variableName}`}
                              disabled={
                                variablesStore.state.loadingItemId !== null
                              }
                              onClick={async () => {
                                let value = variableValue;
                                if (isPreview) {
                                  const variable = await fetchFullVariable({
                                    processInstanceId,
                                    variableId: id,
                                  });

                                  if (variable === null) {
                                    return;
                                  }

                                  variablesStore.setFullVariableValue(
                                    id,
                                    variable.value,
                                  );

                                  value = variable.value;
                                }

                                form.reset({
                                  name: variableName,
                                  value,
                                });
                                form.change('value', value);
                              }}
                              hasIconOnly
                              tooltipPosition="left"
                              renderIcon={Edit}
                            />
                          </Restricted>
                        );
                      }
                    })()}
                  </Operations>
                ),
                width: '10%',
              },
            ],
          }),
        )}
      />
    );
  },
);

export {VariablesTable};
