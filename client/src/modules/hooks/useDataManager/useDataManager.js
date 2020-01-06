/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useState, useContext} from 'react';
import {DataContext} from 'modules/DataManager';

export default function useDataManager() {
  const {dataManager} = useContext(DataContext);
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
