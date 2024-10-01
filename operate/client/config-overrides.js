/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const MonacoWebpackPlugin = require('monaco-editor-webpack-plugin');
const {CycloneDxWebpackPlugin} = require('@cyclonedx/webpack-plugin');

module.exports = function override(config) {
  config.plugins.push(
    new MonacoWebpackPlugin({
      languages: ['json'],
    }),
    new CycloneDxWebpackPlugin({
      specVersion: '1.5',
    }),
  );
  return config;
};
