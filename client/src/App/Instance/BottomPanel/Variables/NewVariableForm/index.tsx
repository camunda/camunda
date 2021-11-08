/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useRef, useState} from 'react';
import {Form, EditButtonsContainer, Name, Value} from './styled';
import {EditButtons} from '../EditButtons';
import {useNotifications} from 'modules/notifications';
import {useInstancePageParams} from 'App/Instance/useInstancePageParams';
import {variablesStore} from 'modules/stores/variables';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';

type Props = {
  onExit: () => void;
};

const NewVariableForm: React.FC<Props> = ({onExit}) => {
  // const variableValueRef = useRef<HTMLCmTextfieldElement>(null);
  const formRef = useRef<HTMLCmFormElement>(null);
  const notifications = useNotifications();
  const {processInstanceId} = useInstancePageParams();
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [value, setValue] = useState<string>('');

  return (
    <Form
      ref={formRef}
      data-testid="add-variable-form"
      onCmSubmit={async (event) => {
        const {name, value} = event.detail.data;

        const params = {
          id: processInstanceId,
          name: name.toString(),
          value: value.toString(),
          onSuccess: () => {
            notifications.displayNotification('success', {
              headline: 'Variable added',
            });
          },
          onError: () => {
            notifications.displayNotification('error', {
              headline: 'Variable could not be saved',
            });
          },
        };

        await variablesStore.addVariable(params);
        onExit();
      }}
    >
      <Name
        formName="name"
        placeholder="Name"
        data-testid="add-variable-name"
      />
      <Value
        // ref={variableValueRef}
        formName="value"
        placeholder="Value"
        value={value}
        fieldSuffix={{
          type: 'icon',
          icon: 'window',
          press: () => {
            setIsModalVisible(true);
          },
        }}
        onCmInput={(event) => {
          setValue(event.detail.value);
        }}
        data-testid="add-variable-value"
      />
      <JSONEditorModal
        title={'Edit a new Variable'}
        value={value}
        onClose={() => {
          setIsModalVisible(false);
        }}
        onSave={(value) => {
          setValue(value);
          setIsModalVisible(false);
        }}
        isModalVisible={isModalVisible}
      />
      <EditButtonsContainer>
        <EditButtons
          onExitClick={onExit}
          onSaveClick={() => {
            formRef.current?.attemptSubmit();
          }}
        />
      </EditButtonsContainer>
    </Form>
  );
};

export {NewVariableForm};
