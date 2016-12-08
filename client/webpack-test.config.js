var config = require('./webpack.config');
var path = require('path');

deleteUnneededConfigEntries();
addTestRoot();
addBabelRewirePlugin();
addTestFilesBabelLoader();

module.exports = config;

function deleteUnneededConfigEntries() {
  delete config.entry;
  delete config.output;
  delete config.plugins;
}

function addTestRoot() {
  config.resolve.root = [
    config.resolve.root,
    path.resolve(__dirname, 'test')
  ];
}

function addBabelRewirePlugin() {
  config.module.loaders
    .filter(function(loaderConf){
      return loaderConf.loader === 'babel-loader';
    })
    .forEach(function(loader) {
      loader.query.plugins.push('babel-plugin-rewire');
    });
}

function addTestFilesBabelLoader() {
  config.module.loaders.push({
    test: /\.js$/,
    include: [
      path.resolve(__dirname, 'test'),
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
  });
}
