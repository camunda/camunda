/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {C3Navigation} from '@camunda/camunda-composite-components';
import {observer} from 'mobx-react-lite';
import {tracking} from 'modules/tracking';
import {themeStore} from 'modules/stores/theme';
import {Link} from 'react-router-dom';

const Header: React.FC = observer(() => {
  const {selectedTheme, changeTheme} = themeStore;

  return (
    <C3Navigation
      app={{
        ariaLabel: 'Camunda Tasklist',
        name: 'Tasklist',
        prefix: 'Camunda',
        routeProps: {
          to: 'https://camunda.io',
          onClick: () => {
            tracking.track({
              eventName: 'navigation',
              link: 'header-logo',
            });
          },
        },
      }}
      forwardRef={Link}
      appBar={{}}
      navbar={{
        elements: [],
      }}
      userSideBar={{
        ariaLabel: 'Settings',
        customElements: {
          themeSelector: {
            currentTheme: selectedTheme,
            onChange: (theme: string) => {
              changeTheme(theme as 'system' | 'dark' | 'light');
            },
          },
        },
      }}
    />
  );
});

export {Header};
