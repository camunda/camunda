/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {C3Navigation} from '@camunda/camunda-composite-components';
import {useStartProcessParams} from 'modules/routing';
import {tracking} from 'modules/tracking';
import {Link} from 'react-router-dom';
import {Main, LogoIcon} from './styled';
import {themeStore} from 'modules/stores/theme';
import {observer} from 'mobx-react-lite';

const StartProcessFromForm: React.FC = observer(() => {
  const {bpmnProcessId} = useStartProcessParams();
  const {selectedTheme, changeTheme} = themeStore;
  return (
    <>
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
      <Main id="main-content" className="cds--content">
        <p>bpmnProcessId: {bpmnProcessId}</p>
        <LogoIcon />
      </Main>
    </>
  );
});

export {StartProcessFromForm};
