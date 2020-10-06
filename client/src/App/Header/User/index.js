/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';
import {ThemeConsumer} from 'modules/theme';

import Dropdown from 'modules/components/Dropdown';
import {storeStateLocally, getStateLocally} from 'modules/utils/localStorage';

import PropTypes from 'prop-types';

import * as api from 'modules/api/header';
import * as Styled from './styled';
import {getUserName} from './service';

User.propTypes = {
  handleRedirect: PropTypes.func,
};

export default function User({handleRedirect}) {
  const {firstname, lastname, username} = getStateLocally();
  const [user, setUser] = useState({
    firstname,
    lastname,
    username,
  });

  useEffect(() => {
    if (!firstname && !lastname && !username) {
      getUser();
    }
  }, [firstname, lastname, username]);

  const getUser = async () => {
    try {
      const {firstname, lastname, username} = await api.fetchUser();
      setUser({firstname, lastname, username});
      storeStateLocally({firstname, lastname, username});
    } catch (e) {
      console.log('new user could not set');
    }
  };

  const handleLogout = async () => {
    await api.logout();
    handleRedirect({forceRedirect: true});
  };

  const userName = getUserName(user);
  return (
    <Styled.ProfileDropdown data-test="profile-dropdown">
      <ThemeConsumer>
        {({toggleTheme}) =>
          userName ? (
            <Styled.Dropdown label={userName}>
              <Dropdown.Option
                label="Toggle Theme"
                data-test="toggle-theme-button"
                onClick={toggleTheme}
              />

              <Dropdown.Option
                label="Logout"
                data-test="logout-button"
                onClick={handleLogout}
              />
            </Styled.Dropdown>
          ) : (
            <Styled.SkeletonBlock data-test="username-skeleton" />
          )
        }
      </ThemeConsumer>
    </Styled.ProfileDropdown>
  );
}
