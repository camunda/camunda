/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {ThemeConsumer} from 'modules/theme';
import Dropdown from 'modules/components/Dropdown';
import PropTypes from 'prop-types';

import * as Styled from './styled';

User.propTypes = {
  firstname: PropTypes.string,
  lastname: PropTypes.string,
  canLogout: PropTypes.bool,
  handleLogout: PropTypes.func
};

export default function User({firstname, lastname, canLogout, handleLogout}) {
  return (
    <Styled.ProfileDropdown>
      <ThemeConsumer>
        {({toggleTheme}) =>
          firstname && lastname ? (
            <Styled.Dropdown label={`${firstname} ${lastname}`}>
              <Dropdown.Option
                label="Toggle Theme"
                data-test="toggle-theme-button"
                onClick={toggleTheme}
              />

              {canLogout && (
                <Dropdown.Option
                  label="Logout"
                  data-test="logout-button"
                  onClick={handleLogout}
                />
              )}
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
