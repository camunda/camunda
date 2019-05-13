/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {createRef, useState, useEffect} from 'react';
import PropTypes from 'prop-types';

import ActionStatus from 'modules/components/ActionStatus/ActionStatus.js';
import {isValidJSON} from 'modules/utils';

import {EMPTY_PLACEHOLDER, NULL_PLACEHOLDER} from './constants';
import * as Styled from './styled';

export default function Variables({
  variables,
  isEditMode,
  onVariableUpdate,
  isEditable,
  setEditMode
}) {
  const CONST = {EDIT: 'edit', ADD: 'add'};

  const [key, setKey] = useState('');
  const [value, setValue] = useState('');
  const [mode, setMode] = useState('');

  const variablesContentRef = createRef();

  function closeEdit() {
    setEditMode(false);
    setKey('');
    setValue('');
    setMode('');
  }

  function saveVariable() {
    onVariableUpdate(key, value);
    closeEdit();
  }

  function handleOpenAddVariable() {
    setEditMode(true);
    setMode(CONST.ADD);
  }

  function handleOpenEditVariable(name, value) {
    setEditMode(true);
    setMode(CONST.EDIT);
    setKey(name);
    setValue(value);
  }

  // scroll to the bottom of the table if the variables inputs got added
  useEffect(
    () => {
      if (isEditMode && mode === CONST.ADD) {
        variablesContentRef.current.scrollTop =
          variablesContentRef.current.scrollHeight;
      }
    },
    [isEditMode]
  );

  function renderEditButtons(customCondition = false) {
    return (
      <>
        <Styled.EditButton title="Open Modal">
          <Styled.ModalIcon />
        </Styled.EditButton>

        <Styled.EditButton
          title="Exit edit mode"
          data-test="exit-edit-inline-btn"
          onClick={closeEdit}
        >
          <Styled.CloseIcon />
        </Styled.EditButton>
        <Styled.EditButton
          data-test="save-var-btn"
          title="save variable"
          disabled={!value || !isValidJSON(value) || customCondition}
          onClick={saveVariable}
        >
          <Styled.CheckIcon />
        </Styled.EditButton>
      </>
    );
  }

  function renderInlineEdit(propValue) {
    const charactersPerRow = 65;
    const requiredRows = Math.ceil(propValue.length / charactersPerRow);
    const displayedRows = requiredRows > 4 ? 4 : requiredRows;
    const valueHasntChanged = propValue === value;
    return (
      <>
        <Styled.EditInputTD>
          <Styled.EditTextarea
            rows={displayedRows}
            cols={charactersPerRow}
            autoFocus
            placeholder="Value"
            value={value}
            onChange={e => setValue(e.target.value)}
          />
        </Styled.EditInputTD>
        <Styled.EditButtonsTD>
          {renderEditButtons(valueHasntChanged)}
        </Styled.EditButtonsTD>
      </>
    );
  }

  function renderInlineAdd() {
    const variableAlreadyExists =
      !!variables.map(variable => variable.name).filter(name => name === key)
        .length > 0;
    return (
      <Styled.TR data-test="add-key-row">
        <Styled.EditInputTD>
          <Styled.TextInput
            type="text"
            placeholder="Variable"
            value={key}
            onChange={e => setKey(e.target.value)}
          />
        </Styled.EditInputTD>

        <Styled.EditInputTD>
          <Styled.Textarea
            autoFocus
            placeholder="Value"
            value={value}
            onChange={e => setValue(e.target.value)}
          />
        </Styled.EditInputTD>
        <Styled.EditButtonsTD>
          {renderEditButtons(variableAlreadyExists)}
        </Styled.EditButtonsTD>
      </Styled.TR>
    );
  }

  return (
    <Styled.Variables>
      <Styled.VariablesContent ref={variablesContentRef}>
        {!isEditMode && (!variables || !variables.length) ? (
          <Styled.Placeholder>
            {!variables ? NULL_PLACEHOLDER : EMPTY_PLACEHOLDER}
          </Styled.Placeholder>
        ) : (
          <Styled.Table>
            <Styled.THead>
              <Styled.TR>
                <Styled.TH>Variable</Styled.TH>
                <Styled.TH>Value</Styled.TH>
                <Styled.TH />
              </Styled.TR>
            </Styled.THead>
            <tbody>
              {variables.map(({name, value: propValue, hasActiveOperation}) => (
                <Styled.TR
                  key={name}
                  data-test={name}
                  hasActiveOperation={hasActiveOperation}
                >
                  <Styled.TD isBold={true}>{name}</Styled.TD>
                  {key === name && mode === CONST.EDIT ? (
                    renderInlineEdit(propValue)
                  ) : (
                    <>
                      <Styled.TD>
                        <Styled.DisplayText>{propValue}</Styled.DisplayText>
                      </Styled.TD>
                      <Styled.EditButtonsTD>
                        {hasActiveOperation ? (
                          <ActionStatus.Spinner />
                        ) : (
                          <Styled.EditButton
                            title="Enter edit mode"
                            data-test="enter-edit-btn"
                            onClick={() =>
                              handleOpenEditVariable(name, propValue)
                            }
                          >
                            <Styled.EditIcon />
                          </Styled.EditButton>
                        )}
                      </Styled.EditButtonsTD>
                    </>
                  )}
                </Styled.TR>
              ))}
              {mode === CONST.ADD && renderInlineAdd()}
            </tbody>
          </Styled.Table>
        )}
      </Styled.VariablesContent>
      <Styled.VariablesFooter>
        <Styled.Button
          title="Add variable"
          size="small"
          data-test="enter-add-btn"
          onClick={() => handleOpenAddVariable()}
          disabled={!!mode || !isEditable}
        >
          <Styled.Plus /> Add Variable
        </Styled.Button>
      </Styled.VariablesFooter>
    </Styled.Variables>
  );
}

Variables.propTypes = {
  variables: PropTypes.array,
  isEditMode: PropTypes.bool.isRequired,
  isEditable: PropTypes.bool.isRequired,
  onVariableUpdate: PropTypes.func.isRequired,
  setEditMode: PropTypes.func.isRequired
};
