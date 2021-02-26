/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
        headline: 'To continue to getting started, go back to',
        isDismissable: false,
        navigation: {
          label: 'Cloud',
          navigationHandler: () => {
            window.location.href = gseUrl;
          },
        },
      });
    }
  }, [gseUrl, displayNotification]);

  return null;
};

export {GettingStartedExperience};
