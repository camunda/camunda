/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect, useState} from 'react';

import {getOptimizeVersion} from 'config';

export default function DocsLink({children, location}) {
  const [optimizeVersion, setOptimizeVersion] = useState('latest');

  useEffect(() => {
    (async () => {
      const version = (await getOptimizeVersion()).split('.');
      version.length = 2;
      setOptimizeVersion(version.join('.'));
    })();
  }, []);

  return children(`https://docs.camunda.org/optimize/${optimizeVersion}/${location}`);
}
