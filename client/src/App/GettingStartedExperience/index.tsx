/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect} from 'react';
import {useNotifications} from 'modules/notifications';
import {useLocation} from 'react-router-dom';

const GettingStartedExperience: React.FC = () => {
  const {displayNotification} = useNotifications();
  const location = useLocation();

  const searchParams = new URLSearchParams(location.search);
  const gseUrl = searchParams.get('gseUrl');

  useEffect(() => {
    if (gseUrl !== null) {
      displayNotification('info', {
        headline: 'To continue getting started, head back to Console',
        isDismissable: false,
        navigation: {
          label: 'Open Console',
          navigationHandler: () => {
            window.location.href = gseUrl;
          },
        },
        showCreationTime: false,
      });
    }
  }, [gseUrl, displayNotification]);

  return null;
};

export {GettingStartedExperience};
