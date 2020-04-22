/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {withRouter} from 'react-router-dom';
import {Dropdown} from 'components';

import './LogoutButton.scss';
import {t} from 'translation';

export function LogoutButton({history}) {
  const label = t('navigation.logout');
  return (
    <Dropdown.Option className="LogoutButton" onClick={() => history.push('/logout')} title={label}>
      {label}
    </Dropdown.Option>
  );
}

export default withRouter(LogoutButton);
