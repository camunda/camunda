/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import useDataManager from 'modules/hooks/useDataManager';

export default function useSubscription() {
  const dataManager = useDataManager();

  function subscribe(topic, statehooks, cb) {
    if (typeof statehooks !== 'string' && !Array.isArray(statehooks)) {
      throw new Error('Unexpected statehooks type');
    }

    const subscription = {
      [topic]: ({state, response}) => {
        if (statehooks.includes(state)) {
          cb(response);
        }
      },
    };

    dataManager.subscribe(subscription);

    return () => {
      dataManager.unsubscribe(subscription);
    };
  }

  return {subscribe};
}
