/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {withDocs} from 'HOC';

export default withDocs(function ({docsLink, location, children}) {
  return (
    <a href={docsLink + location} target="_blank" rel="noopener noreferrer">
      {children}
    </a>
  );
});
