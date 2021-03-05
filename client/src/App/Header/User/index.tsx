/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';

import Dropdown from 'modules/components/Dropdown';
import {storeStateLocally, getStateLocally} from 'modules/utils/localStorage';
import * as api from 'modules/api/header';
import * as Styled from './styled';
import {getDisplayName} from './getDisplayName';
import {currentTheme} from 'modules/stores/currentTheme';

type Props = {
  handleRedirect: () => void;
};

function User({handleRedirect}: Props) {
  const [{firstname, lastname, username, canLogout}, setUser] = useState(
    getStateLocally()
  );
  const displayName = getDisplayName({firstname, lastname, username});

  useEffect(() => {
    if (!firstname && !lastname && !username) {
      getUser();
    }
  }, [firstname, lastname, username]);

  async function getUser() {
    try {
      const user = await api.fetchUser();
      setUser(user);
      storeStateLocally(user);
    } catch {
      console.log('User could not be fetched');
    }
  }

  return (
    <Styled.ProfileDropdown data-testid="profile-dropdown">
      {displayName ? (
        <Styled.Dropdown label={displayName}>
          <Dropdown.Option
            label="Toggle Theme"
            data-testid="toggle-theme-button"
            onClick={currentTheme.toggle}
          />

          {canLogout && (
            <Dropdown.Option
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
}

export {User};
