/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useRef, useEffect, useState, useCallback} from 'react';

import {isValidJSON} from 'modules/utils';
import {isRunning} from 'modules/utils/instance';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {variablesStore} from 'modules/stores/variables';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {useNotifications} from 'modules/notifications';

import * as Styled from './styled';
import {observer} from 'mobx-react';
import {SpinnerSkeleton} from 'modules/components/SpinnerSkeleton';
import {Skeleton} from './Skeleton';
import {VARIABLE_MODE} from './constants';
import {Table, TH, TR} from './VariablesTable';
import {useInstancePageParams} from 'App/Instance/useInstancePageParams';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {Warning} from 'modules/components/Warning';

const Variables: React.FC = observer(() => {
  const {
    state: {items: variables, status},
    hasNoVariables,
  } = variablesStore;
  const {processInstanceId} = useInstancePageParams();
  const notifications = useNotifications();

  const [editMode, setEditMode] = useState('');
  const [name, setName] = useState('');
  const [value, setValue] = useState('');
  const [errorMessage, setErrorMessage] = useState<string>();

  const variablesContentRef = useRef<HTMLDivElement>(null);
  const editInputTDRef = useRef<HTMLTableDataCellElement>(null);

  const validateName = useCallback(() => {
    if (value.trim() !== '' && !name) {
      return 'Name has to be filled';
    }

    if (name.includes('"') || (name.length > 0 && name.trim() === '')) {
      return 'Name is invalid';
    }

    if (editMode === VARIABLE_MODE.ADD) {
      const isVariableDuplicate =
        variables
          .map((variable) => variable.name)
          .filter((variableName) => variableName === name).length > 0;

      if (isVariableDuplicate) {
        return 'Name should be unique';
      }
    }
  }, [name, value, editMode, variables]);

  const validateValue = useCallback(() => {
    if ((value !== '' || name !== '') && !isValidJSON(value)) {
      return 'Value has to be JSON';
    }
  }, [name, value]);

  useEffect(() => {
    const errorMessageForVariable = validateName();
    const errorMessageForValue = validateValue();

    if (
      errorMessageForVariable !== undefined &&
      errorMessageForValue !== undefined
    ) {
      return setErrorMessage(
        `${errorMessageForVariable} and ${errorMessageForValue}`
      );
    }
    setErrorMessage(errorMessageForVariable || errorMessageForValue);
  }, [validateName, validateValue]);

  function isTextareaOutOfBounds() {
    const inputTD = editInputTDRef.current;

    const theadHeight = 45;

    if (inputTD && variablesContentRef.current) {
      const container = variablesContentRef.current.children[0];
      // distance from top edge of container to bottom edge of td
      const tdPosition =
        inputTD.offsetTop -
        theadHeight -
        container.scrollTop +
        inputTD.offsetHeight;

      return tdPosition > container.clientHeight;
    }
  }

  function handleHeightChange() {
    if (isTextareaOutOfBounds()) {
      scrollToBottom();
    }
  }

  function closeEdit() {
    setEditMode('');
    setName('');
    setValue('');
  }

  function handleError() {
    notifications.displayNotification('error', {
      headline: 'Variable could not be saved',
    });
  }

  function saveVariable() {
    if (editMode === VARIABLE_MODE.ADD) {
      variablesStore.addVariable({
        id: processInstanceId,
        name,
        value,
        onError: handleError,
      });
    } else if (editMode === VARIABLE_MODE.EDIT) {
      variablesStore.updateVariable({
        id: processInstanceId,
        name,
        value,
        onError: handleError,
      });
    }

    closeEdit();
  }

  function handleOpenEditVariable(name: string, value: string) {
    setEditMode(VARIABLE_MODE.EDIT);
    setName(name);
    setValue(value);
  }

  function scrollToBottom() {
    if (variablesContentRef !== null && variablesContentRef.current !== null) {
      const scrollableElement = variablesContentRef.current.children[0];
      scrollableElement.scrollTop = scrollableElement.scrollHeight;
    }
  }

  function renderEditButtons(
    errorMessage?: string,
    isValueChangedOnEdit?: boolean
  ) {
    return (
      <>
        <Styled.Warning>
          {errorMessage !== undefined && <Warning title={errorMessage} />}
        </Styled.Warning>

        <Styled.EditButton
          title="Exit edit mode"
          onClick={closeEdit}
          size="large"
          iconButtonTheme="default"
          icon={<Styled.CloseIcon />}
        />

        <Styled.EditButton
          title="Save variable"
          disabled={
            (editMode === VARIABLE_MODE.EDIT && !isValueChangedOnEdit) ||
            errorMessage !== undefined ||
            name.trim() === '' ||
            value.trim() === ''
          }
          onClick={saveVariable}
          size="large"
          iconButtonTheme="default"
          icon={<Styled.CheckIcon />}
        />
      </>
    );
  }

  function renderInlineEdit(propValue: string) {
    return (
      <>
        <Styled.EditInputTD ref={editInputTDRef}>
          <Styled.EditTextarea
            autoFocus
            hasAutoSize
            minRows={1}
            maxRows={4}
            data-testid="edit-value"
            placeholder="Value"
            value={value}
            hasError={errorMessage !== undefined}
            onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) =>
              setValue(e.target.value)
            }
            onHeightChange={handleHeightChange}
          />
        </Styled.EditInputTD>
        <Styled.EditButtonsTD>
          {renderEditButtons(errorMessage, propValue !== value)}
        </Styled.EditButtonsTD>
      </>
    );
  }

  function renderInlineAdd() {
    return (
      <TR data-testid="add-key-row">
        <Styled.EditInputTD>
          <Styled.TextInput
            autoFocus
            type="text"
            placeholder="Name"
            value={name}
            hasError={validateName() !== undefined}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              setName(e.target.value)
            }
          />
        </Styled.EditInputTD>
        <Styled.EditInputTD>
          <Styled.AddTextarea
            placeholder="Value"
            hasAutoSize
            minRows={1}
            maxRows={4}
            value={value}
            hasError={validateValue() !== undefined}
            onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) =>
              setValue(e.target.value)
            }
            onHeightChange={scrollToBottom}
          />
        </Styled.EditInputTD>
        <Styled.AddButtonsTD>
          {renderEditButtons(errorMessage)}
        </Styled.AddButtonsTD>
      </TR>
    );
  }

  function renderContent() {
    const {instance} = currentInstanceStore.state;
    const isCurrentInstanceRunning =
      instance && isRunning({state: instance.state});

    const isVariableHeaderVisible =
      (editMode !== '' || !hasNoVariables) &&
      variablesStore.state.status === 'fetched';

    return (
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
              ({name: variableName, value: propValue, hasActiveOperation}) => (
                <TR
                  key={variableName}
                  data-testid={variableName}
                  hasActiveOperation={hasActiveOperation}
                >
                  <Styled.TD isBold={true}>
                    <Styled.VariableName title={variableName}>
                      {variableName}
                    </Styled.VariableName>
                  </Styled.TD>
                  {name === variableName &&
                  editMode === VARIABLE_MODE.EDIT &&
                  isCurrentInstanceRunning ? (
                    renderInlineEdit(propValue)
                  ) : (
                    <>
                      <Styled.DisplayTextTD>
                        <Styled.DisplayText>{propValue}</Styled.DisplayText>
                      </Styled.DisplayTextTD>
                      {isCurrentInstanceRunning && (
                        <Styled.EditButtonsTD>
                          {hasActiveOperation ? (
                            <Styled.Spinner data-testid="edit-variable-spinner" />
                          ) : (
                            <Styled.EditButton
                              title="Enter edit mode"
                              data-testid="edit-variable-button"
                              onClick={() =>
                                handleOpenEditVariable(variableName, propValue)
                              }
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
            {editMode === VARIABLE_MODE.ADD &&
              isCurrentInstanceRunning &&
              renderInlineAdd()}
          </tbody>
        </Table>
      </Styled.TableScroll>
    );
  }

  return (
    <>
      <Styled.VariablesContent ref={variablesContentRef}>
        {status === 'fetching' && (
          <Styled.EmptyPanel
            data-testid="variables-spinner"
            type="skeleton"
            Skeleton={SpinnerSkeleton}
          />
        )}
        {!editMode && ['initial', 'first-fetch'].includes(status) && (
          <Skeleton type="skeleton" rowHeight={32} />
        )}
        {!editMode && hasNoVariables && (
          <Skeleton type="info" label="The Flow Node has no Variables" />
        )}
        {renderContent()}
      </Styled.VariablesContent>
      <Styled.Footer>
        <Styled.Button
          title="Add variable"
          size="small"
          onClick={() => setEditMode(VARIABLE_MODE.ADD)}
          disabled={
            status === 'first-fetch' ||
            editMode !== '' ||
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
