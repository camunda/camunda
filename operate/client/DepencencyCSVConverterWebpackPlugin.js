/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const fs = require('node:fs');
const path = require('node:path');

class DepencencyCSVConverterWebpackPlugin {
  /**
   * @param {object} options
   * @param {string} options.inputFile - The input file to read and format
   * @param {string} options.outputFile - The output file to write the formatted JSON to
   */
  constructor(options = {}) {
    this.options = {
      inputFile: options.inputFile || 'oss-licenses.json',
      outputFile: options.outputFile || 'dependencies.csv',
    };
  }

  apply(compiler) {
    compiler.hooks.afterEmit.tapAsync(
      'DependencyCSVConverterWebpackPlugin',
      (compilation, callback) => {
        const inputPath = path.resolve(
          compilation.options.output.path,
          this.options.inputFile,
        );
        const outputPath = path.resolve(
          compilation.options.output.path,
          this.options.outputFile,
        );

        fs.readFile(inputPath, 'utf8', (err, data) => {
          if (err) {
            console.error('Error reading input JSON file:', err);
            return callback();
          }

          try {
            const parsedData = JSON.parse(data);
            const csvData = parsedData
              .map((item) => {
                return `${item.name}","${item.version}","${item.license}"`;
              })
              .join('\n');

            fs.writeFile(outputPath, csvData, 'utf8', (writeErr) => {
              if (writeErr) {
                console.error('Error writing formatted CSV file:', writeErr);
              } else {
                console.log(`CSV file written to ${outputPath}`);
              }
              callback();
            });
          } catch (parseErr) {
            console.error('Error parsing JSON:', parseErr);
            callback();
          }
        });
      },
    );
  }
}

module.exports = DepencencyCSVConverterWebpackPlugin;
