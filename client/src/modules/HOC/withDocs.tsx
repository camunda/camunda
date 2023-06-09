/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createContext, ComponentType, useContext, ReactNode} from 'react';

export interface WithDocsProps {
  docsLink: string;
}

const DocsContext = createContext<WithDocsProps | undefined>(undefined);

export function DocsProvider({children}: {children: ReactNode}): JSX.Element {
  return (
    <DocsContext.Provider value={{docsLink: `https://docs.camunda.io/optimize/`}}>
      {children}
    </DocsContext.Provider>
  );
}

export default function withDocs<T extends object>(Component: ComponentType<T>) {
  return (props: Omit<T, keyof WithDocsProps>) => (
    <Component {...useContext(DocsContext)} {...(props as T)} />
  );
}
