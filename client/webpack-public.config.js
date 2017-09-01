var config = require('./webpack-development.config');

config.devServer.host = '192.168.31.1';
config.output.publicPath = 'http://192.168.31.1:9000/';

module.exports = config;
