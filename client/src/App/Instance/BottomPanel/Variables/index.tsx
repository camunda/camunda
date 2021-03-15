/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useRef, useEffect, useState} from 'react';

import {isValidJSON} from 'modules/utils';
import {isRunning} from 'modules/utils/instance';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {variablesStore} from 'modules/stores/variables';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {useNotifications} from 'modules/notifications';

import * as Styled from './styled';
import {observer} from 'mobx-react';
import SpinnerSkeleton from 'modules/components/SpinnerSkeleton';
import {Skeleton} from './Skeleton';
import {VARIABLE_MODE} from './constants';
import {Table, TH, TR} from './VariablesTable';
import {useInstancePageParams} from 'App/Instance/useInstancePageParams';

const Variables: React.FC = observer(function Variables() {
  const {
    state: {items, status},
    hasNoVariables,
  } = variablesStore;
  const {workflowInstanceId} = useInstancePageParams();
  const notifications = useNotifications();

  useEffect(() => {
    variablesStore.fetchVariables(workflowInstanceId);
    return () => variablesStore.clearItems();
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
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'children' does not exist on type 'never'... Remove this comment to see the full error message
      container = container.children[0];

      // distance from top edge of container to bottom edge of td
      const tdPosition =
        // @ts-expect-error ts-migrate(2339) FIXME: Property 'offsetTop' does not exist on type 'never... Remove this comment to see the full error message
        inputTD.offsetTop -
        theadHeight -
        // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
        container.scrollTop +
        // @ts-expect-error ts-migrate(2339) FIXME: Property 'offsetHeight' does not exist on type 'ne... Remove this comment to see the full error message
        inputTD.offsetHeight;

      // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
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

  function handleError() {
    notifications.displayNotification('error', {
      headline: 'Variable could not be saved',
    });
  }

  function saveVariable() {
    if (editMode === VARIABLE_MODE.ADD) {
      variablesStore.addVariable({
        id: workflowInstanceId,
        name: key,
        value,
        onError: handleError,
      });
    } else if (editMode === VARIABLE_MODE.EDIT) {
      variablesStore.updateVariable({
        id: workflowInstanceId,
        name: key,
        value,
        onError: handleError,
      });
    }

    closeEdit();
  }

  function handleOpenEditVariable(name: any, value: any) {
    setEditMode(VARIABLE_MODE.EDIT);
    setKey(name);
    setValue(value);
  }

  function scrollToBottom() {
    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
    const scrollableElement = variablesContentRef.current.children[0];
    scrollableElement.scrollTop = scrollableElement.scrollHeight;
  }

  function renderEditButtons({isDisabled}: any) {
    return (
      <>
        <Styled.EditButton
          // @ts-expect-error
          title="Exit edit mode"
          onClick={closeEdit}
          size="large"
          iconButtonTheme="default"
          icon={<Styled.CloseIcon />}
        />

        <Styled.EditButton
          // @ts-expect-error
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

  function renderInlineEdit(propValue: any) {
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
            onChange={(e: any) => setValue(e.target.value)}
            onHeightChange={handleHeightChange}
          />
        </Styled.EditInputTD>
        <Styled.EditButtonsTD>
          {renderEditButtons({
            isDisabled: propValue === value,
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
      <TR data-testid="add-key-row">
        <Styled.EditInputTD>
          <Styled.TextInput
            autoFocus
            type="text"
            placeholder="Variable"
            value={key}
            onChange={(e: any) => setKey(e.target.value)}
          />
        </Styled.EditInputTD>
        <Styled.EditInputTD>
          <Styled.AddTextarea
            placeholder="Value"
            hasAutoSize
            minRows={1}
            maxRows={4}
            value={value}
            onChange={(e: any) => setValue(e.target.value)}
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
    const {instance} = currentInstanceStore.state;
    const isCurrentInstanceRunning =
      instance && isRunning({state: instance.state});
    return (
      <Styled.TableScroll>
        <Table data-testid="variables-list">
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
                data-testid={name}
                // @ts-expect-error ts-migrate(2769) FIXME: Property 'hasActiveOperation' does not exist on ty... Remove this comment to see the full error message
                hasActiveOperation={hasActiveOperation}
              >
                <Styled.TD isBold={true}>
                  <Styled.VariableName title={name}>{name}</Styled.VariableName>
                </Styled.TD>
                {key === name &&
                editMode === VARIABLE_MODE.EDIT &&
                isCurrentInstanceRunning ? (
                  // @ts-expect-error ts-migrate(2554) FIXME: Expected 1 arguments, but got 2.
                  renderInlineEdit(propValue, name)
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
                            // @ts-expect-error ts-migrate(2769) FIXME: Property 'title' does not exist on type 'Intrinsic... Remove this comment to see the full error message
                            title="Enter edit mode"
                            data-testid="edit-variable-button"
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

  function handleOpenAddVariable() {
    setEditMode(VARIABLE_MODE.ADD);
  }

  return (
    <>
      <Styled.VariablesContent ref={variablesContentRef}>
        {status === 'fetching' && (
          <Styled.EmptyPanel
            data-testid="variables-spinner"
            type="skeleton"
            // @ts-expect-error ts-migrate(2769) FIXME: Type '(props: any) => Element' is not assignable t... Remove this comment to see the full error message
            Skeleton={SpinnerSkeleton}
          />
        )}
        {!editMode && ['initial', 'first-fetch'].includes(status) && (
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
            status === 'first-fetch' ||
            editMode !== '' ||
            !(
              flowNodeMetaDataStore.isSelectedInstanceRunning ||
              currentInstanceStore.isRunning
            )
          }
        >
          <Styled.Plus /> Add Variable
        </Styled.Button>
      </Styled.Footer>
    </>
  );
});

export default Variables;
