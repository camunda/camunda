const shell = require('shelljs');
const path = require('path');
const http = require('http');
const serveStatic = require('serve-static');
const fs = require('fs');
const proxy = require('http-proxy-middleware');

const webpack = path.resolve(__dirname, '..', 'node_modules', '.bin', 'webpack');
const config = path.resolve(__dirname, '..', 'webpack-production.config.js');
const dist = path.resolve(__dirname, '..', 'dist');
const index = path.resolve(dist, 'index.html');

shell.rm('-rf', dist);
shell.exec(`${webpack} --config ${config}`);

const serve = serveStatic(dist);
const gzExtensions = ['.js', '.css', '.eot', '.ttf', '.svg'];
const contentTypes = {
  '.js': 'application/javascript',
  '.css': 'text/css',
  '.tff': 'application/x-font-ttf',
  '.svg': 'image/svg+xml'
};

function redirectGzips(req, res, next) {
  const extension = gzExtensions.filter(matchesExtension.bind(null, req.url))[0];

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

const proxyInstance = proxy({
  target: 'http://localhost:8090/',
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
  const stat = fs.statSync(index);
  const indexStream = fs.createReadStream(index);

  res.writeHead(200, {
    'Content-Type': 'text/html',
    'Content-Length': stat.size
  });

  indexStream.pipe(res);
}

const server = http.createServer(applyMiddlewares([
  redirectGzips,
  serve,
  proxyApi,
  serveIndex
]));

function applyMiddlewares(functions) {
  return applyMiddleware(0);

  function applyMiddleware(index) {
    return (req, res) => {
      const middleWare = functions[index];
      const next = function() {
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
