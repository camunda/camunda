/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useState} from 'react';
import deepEqual from 'fast-deep-equal';

export default function useChangedState<T>(initialState: T) {
  const [state, _setState] = useState<T>(initialState);

  const setState = useCallback((newState: T) => {
    _setState((prevState) => (deepEqual(prevState, newState) ? prevState : newState));
  }, []);

  return [state, setState];
}
