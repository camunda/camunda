/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useState} from 'react';

export default function useLocalStorage(storageKey = 'sharedState') {
  const [storedValue, setStoredValue] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem(storageKey) || '{}');
    } catch (error) {
      console.log(error);
    }
  });

  const setLocalStorage = (valueToStore, customKey) => {
    try {
      setStoredValue(valueToStore);
      localStorage.setItem(
        customKey || storageKey,
        JSON.stringify(valueToStore)
      );
    } catch (error) {
      console.log(error);
    }
  };

  const clearValue = () => {
    localStorage.removeItem(storageKey);
    setStoredValue({});
  };

  return {storedValue, setLocalStorage, clearValue};
}
