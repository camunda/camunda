/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useRef} from 'react';

import {currentInstanceStore} from 'modules/stores/currentInstance';
import {variablesStore} from 'modules/stores/variables';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';

import * as Styled from './styled';
import {observer} from 'mobx-react';
import {SpinnerSkeleton} from 'modules/components/SpinnerSkeleton';
import {Skeleton} from './Skeleton';
import {Table, TH, TR} from './VariablesTable';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {ExistingVariable} from './ExistingVariable';
import {NewVariable} from './NewVariable';
import {useForm, useFormState} from 'react-final-form';

const Variables: React.FC = observer(() => {
  const {
    state: {items: variables, status},
    hasNoVariables,
    displayStatus,
    scopeId,
  } = variablesStore;

  const variablesContentRef = useRef<HTMLDivElement>(null);

  const isTextareaOutOfBounds = (
    itemRef: React.RefObject<HTMLTableDataCellElement>
  ) => {
    const inputTD = itemRef.current;

    const theadHeight = 45;

    if (inputTD && variablesContentRef?.current) {
      const container = variablesContentRef.current.children[0];
      // distance from top edge of container to bottom edge of td
      const tdPosition =
        inputTD.offsetTop -
        theadHeight -
        container.scrollTop +
        inputTD.offsetHeight;

      return tdPosition > container.clientHeight;
    }
  };

  const scrollToItem = (itemRef: React.RefObject<HTMLTableDataCellElement>) => {
    if (isTextareaOutOfBounds(itemRef)) {
      itemRef.current?.scrollIntoView();
    }
  };
  const form = useForm();

  useEffect(() => {
    form.reset({});
  }, [form, scopeId]);

  const {initialValues} = useFormState();

  const isViewMode =
    initialValues === undefined || Object.values(initialValues).length === 0;

  const isVariableHeaderVisible =
    !isViewMode ||
    (!hasNoVariables && variablesStore.state.status === 'fetched');

  return (
    <>
      <Styled.VariablesContent ref={variablesContentRef}>
        {displayStatus === 'spinner' && (
          <Styled.EmptyPanel
            data-testid="variables-spinner"
            type="skeleton"
            Skeleton={SpinnerSkeleton}
          />
        )}

        {isViewMode && displayStatus === 'skeleton' && (
          <Skeleton type="skeleton" rowHeight={32} />
        )}
        {isViewMode && displayStatus === 'no-variables' && (
          <Skeleton type="info" label="The Flow Node has no Variables" />
        )}
        {(!isViewMode || displayStatus === 'variables') && (
          <Styled.TableScroll>
            <Table data-testid="variables-list">
              <Styled.THead isVariableHeaderVisible={isVariableHeaderVisible}>
                <TR>
                  <Styled.TH>Variables</Styled.TH>
                </TR>
                {isVariableHeaderVisible && (
                  <TR>
                    <TH>Name</TH>
                    <TH>Value</TH>
                    <TH />
                  </TR>
                )}
              </Styled.THead>
              <tbody>
                {variables.map(
                  ({
                    name: variableName,
                    value: variableValue,
                    hasActiveOperation,
                  }) => (
                    <TR
                      key={variableName}
                      data-testid={variableName}
                      hasActiveOperation={hasActiveOperation}
                    >
                      {initialValues?.name === variableName &&
                      currentInstanceStore.isRunning ? (
                        <ExistingVariable
                          variableName={variableName}
                          variableValue={variableValue}
                          onHeightChange={scrollToItem}
                        />
                      ) : (
                        <>
                          <Styled.TD isBold={true}>
                            <Styled.VariableName title={variableName}>
                              {variableName}
                            </Styled.VariableName>
                          </Styled.TD>
                          <Styled.DisplayTextTD>
                            <Styled.DisplayText>
                              {variableValue}
                            </Styled.DisplayText>
                          </Styled.DisplayTextTD>
                          {currentInstanceStore.isRunning && (
                            <Styled.EditButtonsTD>
                              {hasActiveOperation ? (
                                <Styled.Spinner data-testid="edit-variable-spinner" />
                              ) : (
                                <Styled.EditButton
                                  title="Enter edit mode"
                                  type="button"
                                  data-testid="edit-variable-button"
                                  onClick={() => {
                                    form.reset({
                                      name: variableName,
                                      value: variableValue,
                                    });
                                  }}
                                  size="large"
                                  iconButtonTheme="default"
                                  icon={<Styled.EditIcon />}
                                />
                              )}
                            </Styled.EditButtonsTD>
                          )}
                        </>
                      )}
                    </TR>
                  )
                )}
                {initialValues?.name === '' &&
                  initialValues?.value === '' &&
                  currentInstanceStore.isRunning && (
                    <NewVariable onHeightChange={scrollToItem} />
                  )}
              </tbody>
            </Table>
          </Styled.TableScroll>
        )}
      </Styled.VariablesContent>
      <Styled.Footer>
        <Styled.Button
          title="Add variable"
          size="small"
          onClick={() => {
            form.reset({name: '', value: ''});
          }}
          disabled={
            status === 'first-fetch' ||
            !isViewMode ||
            (flowNodeSelectionStore.isRootNodeSelected
              ? !currentInstanceStore.isRunning
              : !flowNodeMetaDataStore.isSelectedInstanceRunning)
          }
        >
          <Styled.Plus /> Add Variable
        </Styled.Button>
      </Styled.Footer>
    </>
  );
});

export default Variables;
