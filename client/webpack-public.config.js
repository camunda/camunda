var config = require('./webpack-development.config');

config.devServer.host = '0.0.0.0';
config.output.publicPath = '/';

module.exports = config;
