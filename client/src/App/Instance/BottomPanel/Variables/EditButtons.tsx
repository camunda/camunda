/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Warning as WarningIcon} from 'modules/components/Warning';
import {Warning, EditButton, CloseIcon, CheckIcon} from './styled';
import {useForm, useFormState} from 'react-final-form';
import {useNotifications} from 'modules/notifications';
import {useInstancePageParams} from 'App/Instance/useInstancePageParams';
import {variablesStore} from 'modules/stores/variables';
import {getError} from './getError';

const EditButtons: React.FC = () => {
  const {processInstanceId} = useInstancePageParams();
  const notifications = useNotifications();
  const form = useForm();
  const {values, initialValues, errors} = useFormState();

  const handleError = () => {
    notifications.displayNotification('error', {
      headline: 'Variable could not be saved',
    });
  };

  const exitEditMode = () => {
    form.reset({});
  };

  const saveVariable = () => {
    const params = {
      id: processInstanceId,
      name: values.name,
      value: values.value,
      onError: handleError,
    };

    if (initialValues.name === '') {
      variablesStore.addVariable(params);
    } else if (initialValues.name === values.name) {
      variablesStore.updateVariable(params);
    }

    exitEditMode();
  };

  const errorMessage = getError(errors);

  return (
    <>
      <Warning>
        {errorMessage !== undefined && <WarningIcon title={errorMessage} />}
      </Warning>

      <EditButton
        type="button"
        title="Exit edit mode"
        onClick={exitEditMode}
        size="large"
        iconButtonTheme="default"
        icon={<CloseIcon />}
      />

      <EditButton
        type="button"
        title="Save variable"
        disabled={
          initialValues.value === values.value || errorMessage !== undefined
        }
        onClick={saveVariable}
        size="large"
        iconButtonTheme="default"
        icon={<CheckIcon />}
      />
    </>
  );
};

export {EditButtons};
