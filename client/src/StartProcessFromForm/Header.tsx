/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {C3Navigation} from '@camunda/camunda-composite-components';
import {tracking} from 'modules/tracking';
import {Link} from 'react-router-dom';

type Props = {
  name: string;
};

const Header: React.FC<Props> = ({name}) => {
  return (
    <C3Navigation
      app={{
        ariaLabel: name,
        name,
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
    />
  );
};

export {Header};
