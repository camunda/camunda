/*eslint-disable*/
var shell = require('shelljs');
var path = require('path');
var http = require('http');
var serveStatic = require('serve-static');
var fs = require('fs');
var proxy = require('http-proxy-middleware');

var webpack = path.resolve(__dirname, '..', 'node_modules', '.bin', 'webpack');
var config = path.resolve(__dirname, '..', 'webpack-production.config.js');
var dist = path.resolve(__dirname, '..', 'dist');
var index = path.resolve(dist, 'index.html');

shell.rm('-rf', dist);
shell.exec(`${webpack} --config ${config}`);

var serve = serveStatic(dist);
var gzExtensions = ['.js', '.css', '.eot', '.ttf', '.svg'];
var contentTypes = {
  '.js': 'application/javascript',
  '.css': 'text/css',
  '.tff': 'application/x-font-ttf',
  '.svg': 'image/svg+xml'
};

function redirectGzips(req, res, next) {
  var extension = gzExtensions.filter(matchesExtension.bind(null, req.url))[0];

  if (extension) {
    req.url = req.url + '.gz';
    res.setHeader('Content-Encoding', 'gzip');

    if (contentTypes[extension]) {
      res.setHeader('Content-Type', contentTypes[extension]);
    }
  }

  next();
}

function matchesExtension(url, extension) {
  return url.substr(url.length - extension.length) === extension;
}

var proxyInstance = proxy({
  target: 'http://localhost:8080/',
  changeOrigin: true
});

function proxyApi(req, res, next) {
  if (req.url.substr(0, 4) === '/api') {
    proxyInstance(req, res, next);
  } else {
    next();
  }
}

function serveIndex(req, res) {
  var stat = fs.statSync(index);
  var indexStream = fs.createReadStream(index);

  res.writeHead(200, {
    'Content-Type': 'text/html',
    'Content-Length': stat.size
  });

  indexStream.pipe(res);
}

var server = http.createServer(applyMiddlewares([
  redirectGzips,
  serve,
  proxyApi,
  serveIndex
]));

function applyMiddlewares(functions) {
  return applyMiddleware(0);

  function applyMiddleware(index) {
    return function(req, res) {
      var middleWare = functions[index];
      var next = function() {
        applyMiddleware(index + 1)(req, res);
      };

      middleWare(req, res, next);
    };
  }
}

server.listen('3000', function() {
  console.log('*****************************************************************************************');
  console.log('Server started at localhost:3000');
  console.log('*****************************************************************************************');
});
