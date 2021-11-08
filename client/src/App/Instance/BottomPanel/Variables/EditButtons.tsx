/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {EditButton, CloseIcon, CheckIcon, EditButtonsContainer} from './styled';

type Props = {
  onExitClick: () => void;
  onSaveClick: () => void;
};

const EditButtons: React.FC<Props> = ({onExitClick, onSaveClick}) => {
  return (
    <EditButtonsContainer>
      <EditButton
        type="button"
        title="Exit edit mode"
        onClick={onExitClick}
        size="large"
        iconButtonTheme="default"
        icon={<CloseIcon />}
      />

      <EditButton
        type="button"
        title="Save variable"
        // disabled={
        //   initialValues.value === values.value ||
        //   validating ||
        //   hasValidationErrors ||
        //   errorMessage !== undefined
        // }
        onClick={onSaveClick}
        size="large"
        iconButtonTheme="default"
        icon={<CheckIcon />}
      />
    </EditButtonsContainer>
  );
};

export {EditButtons};
