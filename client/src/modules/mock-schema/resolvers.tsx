/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import {Resolvers} from 'apollo-boost';

interface ResolverMap {
  [field: string]: (parent: any, args: any, context: any) => any;
}

interface AppResolvers extends Resolvers {
  Task: ResolverMap;
}

const resolvers: AppResolvers = {
  Task: {
    variables: () => {
      return [
        {name: 'myVar', value: '123'},
        {name: 'myVar2', value: 'true'},
      ];
    },
  },
};

export {resolvers};
