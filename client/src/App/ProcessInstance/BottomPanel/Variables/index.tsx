/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef, useMemo} from 'react';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {variablesStore} from 'modules/stores/variables';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import * as Styled from './styled';
import {observer} from 'mobx-react';
import {reaction} from 'mobx';
import {VariableBackdrop} from './VariableBackdrop';
import {Skeleton} from './Skeleton';
import {Table, TH, TR} from './VariablesTable';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {ExistingVariable} from './ExistingVariable';
import {NewVariable} from './NewVariable';
import {PendingVariable} from './PendingVariable';
import {useForm, useFormState} from 'react-final-form';
import {useProcessInstancePageParams} from '../../useProcessInstancePageParams';
import {MAX_VARIABLES_STORED} from 'modules/constants/variables';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';
import {useNotifications} from 'modules/notifications';
import {Restricted} from 'modules/components/Restricted';
import {ActionButtons} from 'modules/components/ActionButtons';
import {ActionButton} from 'modules/components/ActionButton';
import {modificationsStore} from 'modules/stores/modifications';
import {AddVariableButton} from './AddVariableButton';
import {FieldArray, useFieldArray} from 'react-final-form-arrays';
import {NewVariableModification} from './NewVariableModification';
import {VariableFormValues} from 'modules/types/variables';
import {ViewFullVariableButton} from './ViewFullVariableButton';
import {OnLastVariableModificationRemoved} from './OnLastVariableModificationRemoved';

type Props = {
  isVariableModificationAllowed?: boolean;
};

const Variables: React.FC<Props> = observer(
  ({isVariableModificationAllowed = false}) => {
    const {
      state: {items, pendingItem, loadingItemId, status},
      displayStatus,
    } = variablesStore;

    const scopeId =
      variablesStore.scopeId ??
      modificationsStore.getNewScopeIdForFlowNode(
        flowNodeSelectionStore.state.selection?.flowNodeId
      );

    const scrollableContentRef = useRef<HTMLDivElement>(null);
    const variablesContentRef = useRef<HTMLDivElement>(null);
    const variableRowRef = useRef<HTMLTableRowElement>(null);
    const {processInstanceId = ''} = useProcessInstancePageParams();
    const notifications = useNotifications();
    const {isModificationModeEnabled} = modificationsStore;

    const form = useForm<VariableFormValues>();

    const addVariableModifications = useMemo(
      () => modificationsStore.getAddVariableModifications(scopeId),
      [scopeId]
    );

    useEffect(() => {
      const disposer = reaction(
        () => modificationsStore.isModificationModeEnabled,
        (isModificationModeEnabled) => {
          if (!isModificationModeEnabled) {
            form.reset({});
          }
        }
      );

      return disposer;
    }, [isModificationModeEnabled, form]);

    const {initialValues} = useFormState();

    const fieldArray = useFieldArray('newVariables');

    const isViewMode = isModificationModeEnabled
      ? fieldArray.fields.length === 0 &&
        modificationsStore.getAddVariableModifications(scopeId).length === 0
      : initialValues === undefined ||
        Object.values(initialValues).length === 0;

    const isAddMode = initialValues?.name === '' && initialValues?.value === '';

    function fetchFullVariable({
      id,
      enableLoading = true,
    }: {
      id: VariableEntity['id'];
      enableLoading?: boolean;
    }) {
      return variablesStore.fetchVariable({
        id,
        onError: () => {
          notifications.displayNotification('error', {
            headline: 'Variable could not be fetched',
          });
        },
        enableLoading,
      });
    }

    if (displayStatus === 'no-content') {
      return null;
    }

    return (
      <Styled.VariablesContent ref={variablesContentRef}>
        {isViewMode && displayStatus === 'skeleton' && (
          <Skeleton type="skeleton" rowHeight={32} />
        )}
        {isViewMode && displayStatus === 'no-variables' && (
          <Skeleton type="info" label="The Flow Node has no Variables" />
        )}
        {(!isViewMode || displayStatus === 'variables') && (
          <>
            <Styled.TableScroll ref={scrollableContentRef}>
              <Table data-testid="variables-list">
                <Styled.THead
                  scrollBarWidth={
                    (scrollableContentRef?.current?.offsetWidth ?? 0) -
                    (scrollableContentRef?.current?.scrollWidth ?? 0)
                  }
                >
                  <TR>
                    <TH>Name</TH>
                    <TH>Value</TH>
                  </TR>
                </Styled.THead>
                <InfiniteScroller
                  onVerticalScrollStartReach={async (scrollDown) => {
                    if (
                      variablesStore.shouldFetchPreviousVariables() === false
                    ) {
                      return;
                    }
                    await variablesStore.fetchPreviousVariables(
                      processInstanceId
                    );

                    if (
                      variablesStore.state.items.length ===
                        MAX_VARIABLES_STORED &&
                      variablesStore.state.latestFetch.itemsCount !== 0
                    ) {
                      scrollDown(
                        variablesStore.state.latestFetch.itemsCount *
                          (variableRowRef.current?.offsetHeight ?? 0)
                      );
                    }
                  }}
                  onVerticalScrollEndReach={() => {
                    if (variablesStore.shouldFetchNextVariables() === false) {
                      return;
                    }
                    variablesStore.fetchNextVariables(processInstanceId);
                  }}
                  scrollableContainerRef={scrollableContentRef}
                >
                  <tbody>
                    {isModificationModeEnabled && (
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
                          {({fields}) =>
                            fields
                              .map((variableName, index) => {
                                return (
                                  <TR
                                    key={
                                      fields.value[index]?.id ?? variableName
                                    }
                                    data-testid={`newVariables[${index}]`}
                                  >
                                    <NewVariableModification
                                      variableName={variableName}
                                      onRemove={() => {
                                        fields.remove(index);
                                      }}
                                    />
                                  </TR>
                                );
                              })
                              .reverse()
                          }
                        </FieldArray>
                      </>
                    )}
                    {items.map(
                      ({
                        name: variableName,
                        value: variableValue,
                        hasActiveOperation,
                        isPreview,
                        id,
                      }) => (
                        <TR
                          ref={variableRowRef}
                          key={variableName}
                          data-testid={variableName}
                          hasActiveOperation={hasActiveOperation}
                        >
                          {(initialValues?.name === variableName &&
                            processInstanceDetailsStore.isRunning) ||
                          (isModificationModeEnabled &&
                            isVariableModificationAllowed) ? (
                            <ExistingVariable
                              id={id}
                              variableName={variableName}
                              variableValue={
                                variablesStore.getFullVariableValue(id) ??
                                variableValue
                              }
                              pauseValidation={
                                isPreview &&
                                variablesStore.getFullVariableValue(id) ===
                                  undefined
                              }
                              onFocus={() => {
                                if (
                                  isPreview &&
                                  variablesStore.getFullVariableValue(id) ===
                                    undefined
                                ) {
                                  variablesStore.fetchVariable({
                                    id,
                                    onSuccess: (variable: VariableEntity) => {
                                      variablesStore.setFullVariableValue(
                                        id,
                                        variable.value
                                      );
                                    },
                                    onError: () => {
                                      notifications.displayNotification(
                                        'error',
                                        {
                                          headline:
                                            'Variable could not be fetched',
                                        }
                                      );
                                    },
                                  });
                                }
                              }}
                              onExitEditMode={() => {
                                variablesStore.deleteFullVariableValue(id);
                              }}
                            />
                          ) : (
                            <>
                              <Styled.TD>
                                <Styled.VariableName title={variableName}>
                                  {variableName}
                                </Styled.VariableName>
                              </Styled.TD>

                              <Styled.DisplayTextTD>
                                <Styled.DisplayTextContainer>
                                  <Styled.DisplayText
                                    hasBackdrop={loadingItemId === id}
                                  >
                                    {loadingItemId === id && (
                                      <VariableBackdrop />
                                    )}
                                    {variableValue}
                                  </Styled.DisplayText>

                                  {processInstanceDetailsStore.isRunning &&
                                  (!isModificationModeEnabled ||
                                    isVariableModificationAllowed) ? (
                                    <>
                                      {hasActiveOperation ? (
                                        <Styled.Spinner data-testid="edit-variable-spinner" />
                                      ) : (
                                        <Restricted
                                          scopes={['write']}
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
                                                  const variable =
                                                    await fetchFullVariable({
                                                      id,
                                                      enableLoading: false,
                                                    });

                                                  return (
                                                    variable?.value ?? null
                                                  );
                                                }}
                                              />
                                            ) : null
                                          }
                                        >
                                          <ActionButtons>
                                            <ActionButton
                                              title="Enter edit mode"
                                              data-testid="edit-variable-button"
                                              disabled={loadingItemId !== null}
                                              onClick={async () => {
                                                let value = variableValue;
                                                if (isPreview) {
                                                  const variable =
                                                    await fetchFullVariable({
                                                      id,
                                                    });

                                                  if (variable === null) {
                                                    return;
                                                  }

                                                  variablesStore.setFullVariableValue(
                                                    id,
                                                    variable.value
                                                  );

                                                  value = variable.value;
                                                }

                                                form.reset({
                                                  name: variableName,
                                                  value,
                                                });
                                                form.change('value', value);
                                              }}
                                              icon={<Styled.EditIcon />}
                                            />
                                          </ActionButtons>
                                        </Restricted>
                                      )}
                                    </>
                                  ) : (
                                    <>
                                      {isPreview ? (
                                        <ViewFullVariableButton
                                          variableName={variableName}
                                          onClick={async () => {
                                            const variable =
                                              await fetchFullVariable({
                                                id,
                                                enableLoading: false,
                                              });

                                            return variable?.value ?? null;
                                          }}
                                        />
                                      ) : null}
                                    </>
                                  )}
                                </Styled.DisplayTextContainer>
                              </Styled.DisplayTextTD>
                            </>
                          )}
                        </TR>
                      )
                    )}
                  </tbody>
                </InfiniteScroller>
              </Table>
            </Styled.TableScroll>
          </>
        )}
        {!isModificationModeEnabled && (
          <Restricted
            scopes={['write']}
            resourceBasedRestrictions={{
              scopes: ['UPDATE_PROCESS_INSTANCE'],
              permissions: processInstanceDetailsStore.getPermissions(),
            }}
          >
            <Styled.Footer
              scrollBarWidth={
                (scrollableContentRef?.current?.offsetWidth ?? 0) -
                (scrollableContentRef?.current?.scrollWidth ?? 0)
              }
              hasPendingVariable={pendingItem !== null}
            >
              {processInstanceDetailsStore.isRunning && (
                <>
                  {pendingItem !== null && <PendingVariable />}
                  {isAddMode && pendingItem === null && <NewVariable />}
                </>
              )}

              {!isAddMode && pendingItem === null && (
                <AddVariableButton
                  onClick={() => {
                    form.reset({name: '', value: ''});
                  }}
                  disabled={
                    status === 'first-fetch' ||
                    !isViewMode ||
                    (flowNodeSelectionStore.isRootNodeSelected
                      ? !processInstanceDetailsStore.isRunning
                      : !flowNodeMetaDataStore.isSelectedInstanceRunning) ||
                    loadingItemId !== null
                  }
                />
              )}
            </Styled.Footer>
          </Restricted>
        )}
      </Styled.VariablesContent>
    );
  }
);

export default Variables;
