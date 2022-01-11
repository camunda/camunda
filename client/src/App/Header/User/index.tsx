/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import Option from 'modules/components/Dropdown/Option';
import * as api from 'modules/api/header';
import * as Styled from './styled';
import {currentTheme} from 'modules/stores/currentTheme';
import {authenticationStore} from 'modules/stores/authentication';
import {observer} from 'mobx-react';

type Props = {
  handleRedirect: () => void;
};

const User: React.FC<Props> = observer(({handleRedirect}) => {
  const {displayName, canLogout} = authenticationStore.state;

  return (
    <Styled.ProfileDropdown data-testid="profile-dropdown">
      {displayName ? (
        <Styled.Dropdown label={displayName}>
          <Option
            label="Toggle Theme"
            data-testid="toggle-theme-button"
            onClick={currentTheme.toggle}
          />

          {canLogout && (
            <Option
              label="Logout"
              data-testid="logout-button"
              onClick={async () => {
                await api.logout();
                handleRedirect();
              }}
            />
          )}
        </Styled.Dropdown>
      ) : (
        <Styled.SkeletonBlock data-testid="username-skeleton" />
      )}
    </Styled.ProfileDropdown>
  );
});

export {User};
