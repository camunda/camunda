/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect} from 'react';

import {UiConfig, createAccessorFunction} from 'config';

export default function useUiConfig<T extends keyof UiConfig>(key: T): UiConfig[T] | undefined {
  const [value, setValue] = useState<UiConfig[T] | undefined>();

  useEffect(() => {
    if (value !== undefined) {
      return;
    }
    const accessorFunction = createAccessorFunction<UiConfig[T]>(key);
    (async () => setValue(await accessorFunction()))();
  }, [key, value]);

  return value;
}
