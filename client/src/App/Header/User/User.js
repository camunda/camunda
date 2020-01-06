/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';
import {ThemeConsumer} from 'modules/theme';

import Dropdown from 'modules/components/Dropdown';
import withSharedState from 'modules/components/withSharedState';

import PropTypes from 'prop-types';

import * as api from 'modules/api/header';
import * as Styled from './styled';

User.propTypes = {
  handleRedirect: PropTypes.func,
  getStateLocally: PropTypes.func,
  storeStateLocally: PropTypes.func,
  clearStateLocally: PropTypes.func
};

function User({
  handleRedirect,
  getStateLocally,
  storeStateLocally,
  clearStateLocally,
  ...props
}) {
  const {firstname, lastname} = getStateLocally('sharedState');

  const [user, setUser] = useState({
    firstname,
    lastname
  });

  useEffect(() => {
    (firstname && lastname) || getUser();
  }, []);

  const getUser = async () => {
    try {
      const {firstname, lastname} = await api.fetchUser();
      setUser({firstname, lastname});
      storeStateLocally({firstname, lastname}, 'sharedState');
    } catch (e) {
      console.log('user could not be loaded');
    }
  };

  const handleLogout = async () => {
    handleRedirect({forceRedirect: true});
    await api.logout();
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
            <Styled.SkeletonBlock />
          )
        }
      </ThemeConsumer>
    </Styled.ProfileDropdown>
  );
}

export default withSharedState(User);
