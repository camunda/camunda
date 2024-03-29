/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect} from 'react';

import {UiConfig, createAccessorFunction} from 'config';

export default function useUiConfig<T extends keyof UiConfig>(...keys: T[]): Pick<UiConfig, T> {
  const [value, setValue] = useState<Partial<Pick<UiConfig, T>>>({});

  useEffect(() => {
    if (Object.keys(value).length === keys.length) {
      return;
    }

    const accessorFunctions = keys.map((k) => createAccessorFunction<UiConfig[T]>(k));
    (async () => {
      const values = await Promise.all(accessorFunctions.map((fn) => fn()));
      setValue((currentState) => {
        const newState = {...currentState};
        keys.forEach((k, i) => {
          newState[k] = values[i];
        });
        return newState;
      });
    })();
  }, [keys, value]);

  return value as Pick<UiConfig, T>;
}
