/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useRef, useEffect, useState} from 'react';
import PropTypes from 'prop-types';

import {isValidJSON} from 'modules/utils';
import {isRunning} from 'modules/utils/instance';
import {currentInstance} from 'modules/stores/currentInstance';
import {flowNodeInstance} from 'modules/stores/flowNodeInstance';
import {variables} from 'modules/stores/variables';

import * as Styled from './styled';
import {observer} from 'mobx-react';
import SpinnerSkeleton from 'modules/components/SpinnerSkeleton';
import {Skeleton} from './Skeleton';
import {VARIABLE_MODE} from './constants';
import {STATE} from 'modules/constants';
import {Table, TH, TR} from '../VariablesTable';
import {useParams} from 'react-router-dom';

const Variables = observer(function Variables() {
  const {
    state: {items, isLoading, isInitialLoadComplete},
    hasNoVariables,
  } = variables;
  const {id: workflowInstanceId} = useParams();

  useEffect(() => {
    variables.fetchVariables(workflowInstanceId);
    return () => variables.clearItems();
  }, [workflowInstanceId]);

  const [editMode, setEditMode] = useState('');
  const [key, setKey] = useState('');
  const [value, setValue] = useState('');

  const variablesContentRef = useRef(null);
  const editInputTDRef = useRef(null);
  /**
   * Determine, if bottom of currently opened edit textarea is
   * out of bottom bounds of visible scroll area.
   *
   * @return boolean
   */
  function isTextareaOutOfBounds() {
    const inputTD = editInputTDRef.current;
    let container = variablesContentRef.current;

    const theadHeight = 45;

    if (inputTD && container) {
      container = container.children[0];

      // distance from top edge of container to bottom edge of td
      const tdPosition =
        inputTD.offsetTop -
        theadHeight -
        container.scrollTop +
        inputTD.offsetHeight;

      return tdPosition > container.offsetHeight;
    }
  }

  function handleHeightChange() {
    if (isTextareaOutOfBounds()) {
      scrollToBottom();
    }
  }

  function closeEdit() {
    setEditMode('');
    setKey('');
    setValue('');
  }

  function saveVariable() {
    if (editMode === VARIABLE_MODE.ADD) {
      variables.addVariable(workflowInstanceId, key, value);
    } else if (editMode === VARIABLE_MODE.EDIT) {
      variables.updateVariable(workflowInstanceId, key, value);
    }

    closeEdit();
  }

  function handleOpenEditVariable(name, value) {
    setEditMode(VARIABLE_MODE.EDIT);
    setKey(name);
    setValue(value);
  }

  function scrollToBottom() {
    const scrollableElement = variablesContentRef.current.children[0];
    scrollableElement.scrollTop = scrollableElement.scrollHeight;
  }

  function renderEditButtons({isDisabled}) {
    return (
      <>
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
            !value || !isValidJSON(value) || key.includes('"') || isDisabled
          }
          onClick={saveVariable}
          size="large"
          iconButtonTheme="default"
          icon={<Styled.CheckIcon />}
        />
      </>
    );
  }

  renderEditButtons.propTypes = {
    isDisabled: PropTypes.bool,
  };

  function renderInlineEdit(propValue) {
    const valueHasntChanged = propValue === value;
    return (
      <>
        <Styled.EditInputTD ref={editInputTDRef}>
          <Styled.EditTextarea
            autoFocus
            hasAutoSize
            minRows={1}
            maxRows={4}
            data-test="edit-value"
            placeholder="Value"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onHeightChange={handleHeightChange}
          />
        </Styled.EditInputTD>
        <Styled.EditButtonsTD>
          {renderEditButtons({
            isDisabled: valueHasntChanged,
          })}
        </Styled.EditButtonsTD>
      </>
    );
  }

  function renderInlineAdd() {
    const variableAlreadyExists =
      items.map((variable) => variable.name).filter((name) => name === key)
        .length > 0;
    const isVariableEmpty = key.trim() === '';
    return (
      <TR data-test="add-key-row">
        <Styled.EditInputTD>
          <Styled.TextInput
            autoFocus
            type="text"
            placeholder="Variable"
            value={key}
            onChange={(e) => setKey(e.target.value)}
          />
        </Styled.EditInputTD>
        <Styled.EditInputTD>
          <Styled.AddTextarea
            placeholder="Value"
            hasAutoSize
            minRows={1}
            maxRows={4}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onHeightChange={scrollToBottom}
          />
        </Styled.EditInputTD>
        <Styled.AddButtonsTD>
          {renderEditButtons({
            isDisabled: variableAlreadyExists || isVariableEmpty,
          })}
        </Styled.AddButtonsTD>
      </TR>
    );
  }

  function renderContent() {
    const {instance} = currentInstance.state;
    const isCurrentInstanceRunning =
      instance && isRunning({state: instance.state});
    return (
      <Styled.TableScroll>
        <Table data-test="variables-list">
          <Styled.THead>
            <TR>
              <TH>Variable</TH>
              <TH>Value</TH>
              <TH />
            </TR>
          </Styled.THead>
          <tbody>
            {items.map(({name, value: propValue, hasActiveOperation}) => (
              <TR
                key={name}
                data-test={name}
                hasActiveOperation={hasActiveOperation}
              >
                <Styled.TD isBold={true}>
                  <Styled.VariableName title={name}>{name}</Styled.VariableName>
                </Styled.TD>
                {key === name &&
                editMode === VARIABLE_MODE.EDIT &&
                isCurrentInstanceRunning ? (
                  renderInlineEdit(propValue, name)
                ) : (
                  <>
                    <Styled.DisplayTextTD>
                      <Styled.DisplayText>{propValue}</Styled.DisplayText>
                    </Styled.DisplayTextTD>
                    {isCurrentInstanceRunning && (
                      <Styled.EditButtonsTD>
                        {hasActiveOperation ? (
                          <Styled.Spinner data-test="edit-variable-spinner" />
                        ) : (
                          <Styled.EditButton
                            title="Enter edit mode"
                            data-test="edit-variable-button"
                            onClick={() =>
                              handleOpenEditVariable(name, propValue)
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
            ))}
            {editMode === VARIABLE_MODE.ADD &&
              isCurrentInstanceRunning &&
              renderInlineAdd()}
          </tbody>
        </Table>
      </Styled.TableScroll>
    );
  }

  function isInstanceRunning() {
    const {
      state: {
        selection: {flowNodeId, treeRowIds},
      },
      flowNodeIdToFlowNodeInstanceMap,
    } = flowNodeInstance;

    const {instance} = currentInstance.state;

    if (instance === null && flowNodeId === null) {
      return false;
    }

    const selectedRowState = !flowNodeId
      ? instance.state
      : flowNodeIdToFlowNodeInstanceMap.get(flowNodeId).get(treeRowIds[0])
          .state;

    return [STATE.ACTIVE, STATE.INCIDENT].includes(selectedRowState);
  }

  function handleOpenAddVariable() {
    setEditMode(VARIABLE_MODE.ADD);
  }

  return (
    <>
      <Styled.VariablesContent ref={variablesContentRef}>
        {isLoading && isInitialLoadComplete && (
          <Styled.EmptyPanel
            data-test="variables-spinner"
            type="skeleton"
            Skeleton={SpinnerSkeleton}
          />
        )}
        {!editMode && !isInitialLoadComplete && (
          <Skeleton type="skeleton" rowHeight={32} />
        )}
        {!editMode && hasNoVariables && (
          <Skeleton type="info" label="The Flow Node has no variables." />
        )}
        {renderContent()}
      </Styled.VariablesContent>
      <Styled.Footer>
        <Styled.Button
          title="Add variable"
          size="small"
          onClick={() => handleOpenAddVariable()}
          disabled={
            isLoading ||
            !isInitialLoadComplete ||
            !!editMode ||
            !isInstanceRunning()
          }
        >
          <Styled.Plus /> Add Variable
        </Styled.Button>
      </Styled.Footer>
    </>
  );
});

export default Variables;
