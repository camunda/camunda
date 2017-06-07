var config = require('./webpack-development.config');

config.devServer.host = '0.0.0.0';
config.output.publicPath = 'http://192.168.21.120:9000/';

module.exports = config;
