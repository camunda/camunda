/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled';

import {OPERATION_TYPE} from 'modules/constants';
import Dropdown from 'modules/components/Dropdown';
import {DROPDOWN_PLACEMENT} from 'modules/constants';
import useFilterContext from 'modules/hooks/useFilterContext';
import useInstanceSelectionContext from 'modules/hooks/useInstanceSelectionContext';
import useDataManager from 'modules/hooks/useDataManager';

const CreateOperationDropdown = ({label}) => {
  const {query} = useFilterContext();
  const {ids, excludeIds, reset} = useInstanceSelectionContext();
  const {applyBatchOperation} = useDataManager();

  const handleCreateOperation = operationType => {
    reset();
    applyBatchOperation(operationType, {...query, ids, excludeIds});
  };

  return (
    <Styled.DropdownContainer>
      <Dropdown
        buttonStyles={Styled.dropdownButtonStyles}
        placement={DROPDOWN_PLACEMENT.TOP}
        label={label}
      >
        <Dropdown.Option
          onClick={() => handleCreateOperation(OPERATION_TYPE.RESOLVE_INCIDENT)}
          label="Retry"
        />
        <Dropdown.Option
          onClick={() =>
            handleCreateOperation(OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE)
          }
          label="Cancel"
        />
      </Dropdown>
    </Styled.DropdownContainer>
  );
};

CreateOperationDropdown.propTypes = {
  label: PropTypes.string
};

export default CreateOperationDropdown;
