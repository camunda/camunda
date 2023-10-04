/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createContext, ComponentType, useContext, ReactNode, useEffect, useState} from 'react';

import {getOptimizeVersion} from 'config';

export interface WithDocsProps {
  docsLink: string;
}

const OPTIMIZE_DOCS_URL = 'https://docs.camunda.io/optimize/';

export const DocsContext = createContext<WithDocsProps>({docsLink: OPTIMIZE_DOCS_URL});

export function DocsProvider({children}: {children: ReactNode}): JSX.Element {
  const [optimizeVersion, setOptimizeVersion] = useState('');

  useEffect(() => {
    (async () => {
      const version = (await getOptimizeVersion()).split('.');
      version.length = 2;
      setOptimizeVersion(version.join('.'));
    })();
  }, []);

  const optimizeVersionWithSlash = optimizeVersion ? optimizeVersion + '/' : '';

  return (
    <DocsContext.Provider value={{docsLink: OPTIMIZE_DOCS_URL + optimizeVersionWithSlash}}>
      {children}
    </DocsContext.Provider>
  );
}

export default function withDocs<T extends object>(Component: ComponentType<T>) {
  return (props: Omit<T, keyof WithDocsProps>) => (
    <Component {...useContext(DocsContext)} {...(props as T)} />
  );
}
