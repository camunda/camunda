/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useContext} from 'react';
import {NotificationContext} from 'modules/contexts/NotificationContext';

const useNotifications = () => {
  return useContext(NotificationContext);
};

export {useNotifications};
