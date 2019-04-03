/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

const Async = {
  React: {
    Component: class AsyncComponent extends React.Component {
      reqs = [];
      await = async (promise, then) => {
        this.reqs.push(then);

        const result = await promise;
        const idx = this.reqs.indexOf(then);
        if (idx > -1) {
          then(result);
          this.reqs.splice(idx, 1);
        }
      };

      cancelAwait = () => {
        this.reqs.length = 0;
      };

      render() {
        return null;
      }
    }
  }
};

export default Async;
