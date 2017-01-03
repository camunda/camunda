var webpack = require('webpack');
var path = require('path');
var HtmlWebpackPlugin = require('html-webpack-plugin');

module.exports = {
  devtool: 'source-map',
  target: 'web',
  entry: {
    app: path.resolve(__dirname, 'app', 'app.js'),
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
        test: /\.html$/,
        loader: 'html-loader',
        include: [
          path.resolve(__dirname, 'app'),
        ]
      },
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
        ],
        include: [
          path.resolve(__dirname, 'app'),
        ]
      },
      {
        test: /\.(eot|svg|ttf|woff|woff2)$/,
        loader: 'file-loader?name=fonts/[name].[ext]'
      },
      {
        test: /\.(jpg|png)$/,
        loader: 'url-loader',
        include: [
          path.resolve(__dirname, 'app'),
        ]
      },
      {
        test: /\.gif$/,
        loader: 'url-loader',
        include: [
          path.resolve(__dirname, 'app'),
        ]
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
      names: ['manifest']
    }),
    new HtmlWebpackPlugin({
      title: 'Camunda Optimize',
      template: 'app/index.html'
    })
  ],
  devServer: {
    contentBase: './dist',
    port: 9000,
    inline: true,
    open: true,
    historyApiFallback: true
  }
};
