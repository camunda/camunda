/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useHistory} from 'react-router';

const HashRouterMigrator: React.FC = () => {
  const history = useHistory();

  if (history.location.hash !== '') {
    history.replace(history.location.hash.replace(/^#/, ''));
  }

  return null;
};

export {HashRouterMigrator};
