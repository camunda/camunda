/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {createRef, useState, useEffect} from 'react';
import PropTypes from 'prop-types';

import ActionStatus from 'modules/components/ActionStatus/ActionStatus.js';

import {EMPTY_PLACEHOLDER, NULL_PLACEHOLDER} from './constants';
import * as Styled from './styled';

export default function Variables({
  variables,
  isEditMode,
  onVariableUpdate,
  isEditable,
  setEditMode
}) {
  const [key, setKey] = useState('');
  const [value, setValue] = useState('');

  const variablesContentRef = createRef();

  function toggleIsEdit() {
    setEditMode(!isEditMode);

    // clear fields
    if (isEditMode) {
      setKey('');
      setValue('');
    }
  }

  function saveVariable() {
    onVariableUpdate(key, value);
    toggleIsEdit();
  }

  // scroll to the bottom of the table if the variables inputs got added
  useEffect(
    () => {
      if (isEditMode) {
        variablesContentRef.current.scrollTop =
          variablesContentRef.current.scrollHeight;
      }
    },
    [isEditMode]
  );

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
              {variables.map(({name, value, hasActiveOperation}) => (
                <Styled.TR
                  key={name}
                  data-test={name}
                  hasActiveOperation={hasActiveOperation}
                >
                  <Styled.TD isBold={true}>{name}</Styled.TD>
                  <Styled.TD>{value}</Styled.TD>
                  <Styled.EditButtonsTD>
                    {hasActiveOperation && <ActionStatus.Spinner />}
                  </Styled.EditButtonsTD>
                </Styled.TR>
              ))}
              {isEditMode && (
                <Styled.TR data-test="add-key-row">
                  <Styled.EditInputTD>
                    <Styled.TextInput
                      placeholder="Variable"
                      value={key}
                      onChange={e => setKey(e.target.value)}
                    />
                  </Styled.EditInputTD>
                  <Styled.EditInputTD>
                    <Styled.Textarea
                      placeholder="Value"
                      value={value}
                      onChange={e => setValue(e.target.value)}
                    />
                  </Styled.EditInputTD>
                  <Styled.EditButtonsTD>
                    <Styled.EditButton title="Exit edit mode">
                      <Styled.CloseIcon onClick={toggleIsEdit} />
                    </Styled.EditButton>
                    <Styled.EditButton
                      data-test="save-var-btn"
                      title="Update variable"
                      disabled={!value}
                      onClick={saveVariable}
                    >
                      <Styled.CheckIcon />
                    </Styled.EditButton>
                  </Styled.EditButtonsTD>
                </Styled.TR>
              )}
            </tbody>
          </Styled.Table>
        )}
      </Styled.VariablesContent>
      <Styled.VariablesFooter>
        <Styled.AddButton
          title="Add key"
          size="small"
          data-test="add-var-btn"
          onClick={toggleIsEdit}
          disabled={isEditMode || !isEditable}
        >
          <Styled.Plus /> Add Key
        </Styled.AddButton>
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
