/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {createContext, useContext, useEffect, useState} from 'react';

import {getOptimizeVersion} from 'config';

const DocsContext = createContext();

export function DocsProvider({children}) {
  const [optimizeVersion, setOptimizeVersion] = useState('latest');

  useEffect(() => {
    (async () => {
      const version = (await getOptimizeVersion()).split('.');
      version.length = 2;
      setOptimizeVersion(version.join('.'));
    })();
  }, []);

  return (
    <DocsContext.Provider
      value={{docsLink: `https://docs.camunda.org/optimize/${optimizeVersion}/`}}
    >
      {children}
    </DocsContext.Provider>
  );
}

export default function withDocs(Component) {
  return (props) => <Component {...useContext(DocsContext)} {...props} />;
}
