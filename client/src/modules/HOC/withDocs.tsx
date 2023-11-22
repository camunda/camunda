/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createContext, ComponentType, useContext, ReactNode, useEffect, useState} from 'react';

import {getDocsVersion} from 'config';

export interface WithDocsProps {
  generateDocsLink: (path: string) => string;
}

const OPTIMIZE_DOCS_URL = 'https://docs.camunda.io/optimize/';

export const DocsContext = createContext<WithDocsProps>({
  generateDocsLink: (path) => OPTIMIZE_DOCS_URL + path,
});

export function DocsProvider({children}: {children: ReactNode}): JSX.Element {
  const [optimizeVersion, setOptimizeVersion] = useState('');

  useEffect(() => {
    (async () => {
      const version = (await getDocsVersion()).split('.');
      version.length = 2;
      setOptimizeVersion(version.join('.') + '.0');
    })();
  }, []);

  const optimizeVersionWithSlash = optimizeVersion ? optimizeVersion + '/' : '';
  const docsLink = OPTIMIZE_DOCS_URL + optimizeVersionWithSlash;
  const generateDocsLink = (path: string) => docsLink + path;

  return <DocsContext.Provider value={{generateDocsLink}}>{children}</DocsContext.Provider>;
}

export default function withDocs<T extends object>(Component: ComponentType<T>) {
  return (props: Omit<T, keyof WithDocsProps>) => (
    <Component {...useContext(DocsContext)} {...(props as T)} />
  );
}
