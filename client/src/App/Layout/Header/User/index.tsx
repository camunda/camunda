/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import Option from 'modules/components/Dropdown/Option';
import {ProfileDropdown, Dropdown, SkeletonBlock} from './styled';
import {currentTheme} from 'modules/stores/currentTheme';
import {authenticationStore} from 'modules/stores/authentication';
import {observer} from 'mobx-react';
import {useEffect} from 'react';
import {tracking} from 'modules/tracking';

const User: React.FC = observer(() => {
  const {displayName, canLogout, userId} = authenticationStore.state;

  useEffect(() => {
    if (userId) {
      tracking.identifyUser(userId);
    }
  }, [userId]);

  return (
    <ProfileDropdown data-testid="profile-dropdown">
      {displayName ? (
        <Dropdown label={displayName}>
          <Option
            label="Toggle Theme"
            data-testid="toggle-theme-button"
            onClick={currentTheme.toggle}
          />

          {canLogout && (
            <Option
              label="Logout"
              data-testid="logout-button"
              onClick={authenticationStore.handleLogout}
            />
          )}
        </Dropdown>
      ) : (
        <SkeletonBlock data-testid="username-skeleton" />
      )}
    </ProfileDropdown>
  );
});

export {User};
