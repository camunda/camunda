/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const proxy = require('http-proxy-middleware');

module.exports = function(app) {
  app.use(proxy('/api', {target: 'http://localhost:8090'}));
  app.use(
    proxy('/ws', {
      target: 'ws://localhost:8090',
      ws: true
    })
  );
};
