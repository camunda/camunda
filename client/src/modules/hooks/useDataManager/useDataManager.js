/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useState, useEffect, useContext} from 'react';
import {DataContext} from 'modules/DataManager';

export default function useDataManager() {
  const {dataManager} = useContext(DataContext);
  const [subscriptions, setSubscription] = useState({});

  function sanatizeStates(statehooks) {
    if (typeof statehooks === 'string') {
      return [statehooks];
    } else if (Array.isArray(statehooks)) {
      return [...statehooks];
    }
  }

  function subscribe(topic, statehooks, cb) {
    const sanatizedStates = sanatizeStates(statehooks);

    const subscription = {
      [topic]: ({state, response}) => {
        if (!!sanatizedStates.includes(state)) {
          cb(response);
        }
      }
    };
    dataManager.subscribe(subscription);
    setSubscription(subscription);
  }

  useEffect(() => {
    return () =>
      !!Object.keys(subscriptions).length &&
      dataManager.unsubscribe(subscriptions);
  }, []);

  return {subscribe};
}
