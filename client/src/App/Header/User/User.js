/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';
import {ThemeConsumer} from 'modules/theme';
import Dropdown from 'modules/components/Dropdown';
import PropTypes from 'prop-types';

import * as api from 'modules/api/header';
import * as Styled from './styled';

User.propTypes = {
  handleRedirect: PropTypes.func
};

export default function User({handleRedirect}) {
  const [user, setUser] = useState({
    firstname: null,
    lastname: null
  });

  useEffect(() => {
    getUser();
  }, []);

  const getUser = async () => {
    try {
      setUser(await api.fetchUser());
    } catch (e) {
      console.log('user could not be loaded');
    }
  };

  const handleLogout = async () => {
    await api.logout();
    handleRedirect({forceRedirect: true});
  };

  return (
    <Styled.ProfileDropdown>
      <ThemeConsumer>
        {({toggleTheme}) =>
          user.firstname || user.lastname ? (
            <Styled.Dropdown label={`${user.firstname} ${user.lastname}`}>
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
            <>
              <Styled.SkeletonBlock />
              <Styled.Circle />
            </>
          )
        }
      </ThemeConsumer>
    </Styled.ProfileDropdown>
  );
}
