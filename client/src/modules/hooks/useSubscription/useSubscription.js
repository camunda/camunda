/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useState} from 'react';
import useDataManager from 'modules/hooks/useDataManager';

export default function useSubscription() {
  const dataManager = useDataManager();
  const [subscriptions, setSubscription] = useState({});

  function sanitizeStates(statehooks) {
    if (typeof statehooks === 'string') {
      return [statehooks];
    } else if (Array.isArray(statehooks)) {
      return statehooks;
    }
  }

  function subscribe(topic, statehooks, cb) {
    const sanitizedStates = sanitizeStates(statehooks);

    const subscription = {
      [topic]: ({state, response}) => {
        if (!!sanitizedStates.includes(state)) {
          cb(response);
        }
      }
    };
    dataManager.subscribe(subscription);
    setSubscription(subscription);
  }

  function unsubscribe() {
    return (
      !!Object.keys(subscriptions).length &&
      dataManager.unsubscribe(subscriptions)
    );
  }

  return {subscribe, unsubscribe};
}
