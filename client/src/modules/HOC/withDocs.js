/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {createContext, useContext} from 'react';

const DocsContext = createContext();

export function DocsProvider({children}) {
  return (
    <DocsContext.Provider value={{docsLink: `https://docs.camunda.io/docs/`}}>
      {children}
    </DocsContext.Provider>
  );
}

export default function withDocs(Component) {
  return (props) => <Component {...useContext(DocsContext)} {...props} />;
}
