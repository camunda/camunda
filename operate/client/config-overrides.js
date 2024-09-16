/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const MonacoWebpackPlugin = require('monaco-editor-webpack-plugin');
const LicensePlugin = require('webpack-license-plugin');
const DepencencyCSVConverterWebpackPlugin = require('./DepencencyCSVConverterWebpackPlugin');

module.exports = function override(config, env) {
  config.plugins.push(
    new MonacoWebpackPlugin({
      languages: ['json'],
    }),
    new DepencencyCSVConverterWebpackPlugin({
      inputFile: 'dependencies.json',
    }),
    new LicensePlugin({
      outputFilename: 'dependencies.json',
      excludedPackageTest: (packageName) => {
        return (
          packageName.startsWith('bpmn-js') || packageName.startsWith('dmn-js')
        );
      },
    }),
  );
  return config;
};
