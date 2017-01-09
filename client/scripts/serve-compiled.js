var shell = require('shelljs');
var path = require('path');
var express = require('express');

var webpack = path.resolve(__dirname, '..', 'node_modules', '.bin', 'webpack');
var config = path.resolve(__dirname, '..', 'webpack-production.config.js');
var dist = path.resolve(__dirname, '..', 'dist');
var index = path.resolve(dist, 'index.html');

shell.rm('-rf', dist);
shell.exec(`${webpack} --config ${config}`);

var app = express();
var gzExtensions = ['*.js', '*.css', '*.eot', '*.ttf', '*.svg'];
var contentTypes = {
  '*.js': 'application/javascript',
  '*.css': 'text/css',
  '*.tff': 'application/x-font-ttf',
  '*.svg': 'image/svg+xml'
};

gzExtensions.forEach(function(extension) {
  app.get(extension, function(req, res, next) {
    req.url = req.url + '.gz';
    res.set('Content-Encoding', 'gzip');

    if (contentTypes[extension]) {
      res.set('Content-Type', contentTypes[extension]);
    }

    next();
  });
});

app.use(express.static(dist));

app.listen('3000', function() {
  console.log('*****************************************************************************************');
  console.log('Server started at localhost:3000');
  console.log('*****************************************************************************************');
});

app.get('*', function(req, res) {
  res.sendFile(index);
});
