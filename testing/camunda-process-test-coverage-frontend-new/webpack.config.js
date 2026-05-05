/**
 * Webpack configuration for Camunda Process Test Coverage Frontend.
 *
 * Bundles:
 *   - camunda-bpmn-js NavigatedViewer
 *   - Bootstrap 5 (CSS + JS)
 *   - Bootstrap Icons (CSS + fonts)
 *   - Application modules
 *
 * Output structure (relative to coverage/):
 *   coverage/
 *   ├── index.html              (copied from src/)
 *   └── static/
 *       ├── bundle.js           (all JS)
 *       ├── bundle.css          (all CSS extracted to file)
 *       └── fonts/              (woff/woff2 referenced by CSS)
 */

'use strict';

const path = require('path');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');

const OUTPUT_DIR = process.env.BUILD_PATH
  ? path.resolve(process.env.BUILD_PATH)
  : path.resolve(__dirname, 'target/generated-frontend-resources/coverage');
const STATIC_DIR = path.join(OUTPUT_DIR, 'static');

module.exports = {
  entry: './src/app.js',

  output: {
    path: STATIC_DIR,
    filename: 'bundle.js',
    // Paths in extracted CSS are relative to the CSS file (static/bundle.css),
    // so font URLs like 'fonts/...' resolve correctly.
    publicPath: '',
    clean: true,
  },

  plugins: [
    // Extract CSS to a separate file so fonts resolve correctly via relative paths.
    new MiniCssExtractPlugin({ filename: 'bundle.css' }),

    // Copy the HTML template and static media to the output directory.
    new CopyWebpackPlugin({
      patterns: [
        // index.html → coverage/index.html (one level above static/)
        {
          from: path.resolve(__dirname, 'src/index.html'),
          to: path.join(OUTPUT_DIR, 'index.html'),
        },
        // Logo and favicon → coverage/static/media/
        {
          from: path.resolve(__dirname, 'public/static/media'),
          to: path.join(STATIC_DIR, 'media'),
          noErrorOnMissing: true,
        },
      ],
    }),
  ],

  module: {
    rules: [
      // CSS: extract to bundle.css
      {
        test: /\.css$/,
        use: [MiniCssExtractPlugin.loader, 'css-loader'],
      },
      // Fonts: copy to static/fonts/ and update URL references in CSS
      {
        test: /\.(woff|woff2|eot|ttf|otf|svg)$/,
        type: 'asset/resource',
        generator: {
          filename: 'fonts/[name][ext]',
        },
      },
    ],
  },

  resolve: {
    extensions: ['.js'],
  },
};
