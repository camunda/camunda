/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

import Dropdown from 'modules/components/Dropdown';
import {DROPDOWN_PLACEMENT} from 'modules/constants';

const CreateOperationDropdown = ({label}) => {
  return (
    <Styled.DropdownContainer>
      <Dropdown
        buttonStyles={Styled.dropdownButtonStyles}
        placement={DROPDOWN_PLACEMENT.TOP}
        label={label}
      >
        <Dropdown.Option onClick={() => {}} label="Retry" />
        <Dropdown.Option onClick={() => {}} label="Cancel" />
      </Dropdown>
    </Styled.DropdownContainer>
  );
};

CreateOperationDropdown.propTypes = {
  label: PropTypes.string
};

export default CreateOperationDropdown;
