var config = require('./webpack.config');
var path = require('path');

deleteUnneededConfigEntries();
addTestRoot();
addBabelRewirePlugin();
addTestFilesBabelLoader();

module.exports = config;

config.externals = {
  'react/lib/ExecutionEnvironment': true,
  'react/addons': true,
  'react/lib/ReactContext': 'window'
};

function deleteUnneededConfigEntries() {
  delete config.devtool; // It is much quicker that way and source map do not work anyway
  delete config.entry;
  delete config.output;
  delete config.plugins;
}

function addTestRoot() {
  config.resolve.modules.push(
    path.resolve(__dirname, 'test')
  );
}

function addBabelRewirePlugin() {
  config.module.rules
    .filter(function(loaderConf){
      return loaderConf.loader === 'babel-loader';
    })
    .forEach(function(loader) {
      loader.query.plugins.push('babel-plugin-rewire');
    });
}

function addTestFilesBabelLoader() {
  config.module.rules.push({
    test: /\.js$/,
    include: [
      path.resolve(__dirname, 'test'),
    ],
    loader: 'babel-loader',
    query: {
      presets: [
        ['env', {
          targets: {
            browsers: ['last 2 versions', 'IE 11'],
            modules: false
          }
        }]
      ],
      plugins: [
        'transform-object-rest-spread',
        'transform-class-properties',
        ['transform-react-jsx', {
          'pragma': 'jsx'
        }]
      ]
    }
  });
}
