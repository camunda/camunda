var webpack = require('webpack');
var path = require('path');
var HtmlWebpackPlugin = require('html-webpack-plugin');

module.exports = {
  devtool: 'source-map',
  target: 'web',
  entry: {
    app: path.resolve(__dirname, 'app', 'app.js'),
    vendor: ['lodash.isequal', 'redux', 'babel-polyfill']
  },
  output: {
    publicPath: '/',
    filename: '[name].[hash].js',
    path: path.join(__dirname, 'dist')
  },
  resolve: {
    root: path.resolve(__dirname, 'app'),
    modules: [
      'node_modules'
    ]
  },
  module: {
    loaders: [
      {
        test: /\.json$/,
        loader: 'json-loader'
      },
      {
        test: /\.scss$/,
        loaders: [
          'style-loader',
          'css-loader?sourceMap',
          'sass-loader?sourceMap'
        ]
      },
      {
        test: /\.(jpg|png)$/,
        loader: 'url-loader'
      },
      {
        test: /\.js$/,
        include: [
          path.resolve(__dirname, 'app'),
        ],
        loader: 'babel-loader',
        query: {
          presets: ['latest'],
          plugins: [
            'transform-object-rest-spread',
            ['transform-react-jsx', {
              'pragma': 'jsx'
            }]
          ]
        }
      }
    ]
  },
  plugins: [
    new webpack.optimize.CommonsChunkPlugin({
      names: ['vendor', 'manifest']
    }),
    new HtmlWebpackPlugin({
      title: 'Camunda Optimize'
    })
  ],
  devServer: {
    contentBase: './dist',
    host: '0.0.0.0',
    port: 9000,
    inline: true,
    open: true,
    historyApiFallback: true
  }
};
