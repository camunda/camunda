/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const http = require('http');
const WebSocket = require('ws');
const opn = require('opn');
const ansiHTML = require('ansi-html');
const path = require('path');
const fs = require('fs');

module.exports = function createServer(
  {showLogsInTerminal},
  {generateData, isDataGenerationCompleted, restartBackend}
) {
  let addLog;

  const server = http.createServer((request, response) => {
    if (request.url === '/api/dataGenerationComplete') {
      response.writeHead(200, {'Content-Type': 'text/plain'});
      response.end(isDataGenerationCompleted().toString(), 'utf-8');
      return;
    }

    if (request.url === '/api/restartBackend') {
      addLog('--------- BACKEND RESTART INITIATED ---------', 'backend');
      restartBackend();
      response.statusCode = 200;
      response.end('Restarting Optimize backend...', 'utf-8');
      return;
    }

    if (request.url === '/api/generateNewData') {
      if (!isDataGenerationCompleted()) {
        response.statusCode = 102;
        response.end('Data is currently being generated', 'utf-8');
        return;
      }
      addLog('--------- DATA GENERATION INITIATED ---------', 'dataGenerator');
      generateData();
      response.statusCode = 200;
      response.end('Data generation initiated', 'utf-8');
      return;
    }

    var filename = path.parse(request.url).base || 'index.html';
    var filePath = path.join(__dirname, filename);

    var extname = String(path.extname(filePath)).toLowerCase();
    var mimeTypes = {
      '.html': 'text/html',
      '.js': 'text/javascript',
      '.css': 'text/css',
    };

    var contentType = mimeTypes[extname] || 'application/octet-stream';

    fs.readFile(filePath, function (error, content) {
      if (error) {
        if (error.code === 'ENOENT') {
          response.writeHead(404, {'Content-Type': contentType});
          response.end('Not found', 'utf-8');
        } else {
          response.writeHead(500);
          response.end('Internal server error :(', 'utf-8');
        }
      } else {
        response.writeHead(200, {'Content-Type': contentType});
        response.end(content, 'utf-8');
      }
    });
  });

  const socketServer = createWebSocketServer(server);
  addLog = (...args) => {
    if (showLogsInTerminal) {
      addLogInTerminal(...args);
    }
    socketServer.addLog(...args);
  };

  // closing the server to not having to manually kill it
  process.on('SIGINT', () => socketServer.close(() => server.close()));
  process.on('SIGTERM', () => socketServer.close(() => server.close()));

  server.listen(8100, 'localhost');

  opn('http://localhost:8100');

  console.log('Please check http://localhost:8100 for server logs!');

  return {addLog};
};

function createWebSocketServer(server) {
  const connectedSockets = [];

  const logs = {
    backend: [],
    docker: [],
    dataGenerator: [],
  };

  const wss = new WebSocket.Server({server});
  wss.on('connection', function connection(ws) {
    try {
      connectedSockets.push(ws);

      logs.backend.slice(-200).forEach((log) => ws.send(JSON.stringify(log)));
      logs.docker.slice(-400).forEach((log) => ws.send(JSON.stringify(log)));
      logs.dataGenerator.forEach((log) => ws.send(JSON.stringify(log)));

      ws.on('close', function close() {
        connectedSockets.splice(connectedSockets.indexOf(ws), 1);
      });
    } catch (err) {
      console.log(err);
    }
  });

  function addLog(data, type, error) {
    const newLog = {data: ansiHTML(data.toString()), type, error: !!error};

    logs[type].push(newLog);

    if (logs.length > 500) {
      logs.shift();
    }

    connectedSockets.forEach((socket) => {
      socket.send(JSON.stringify(newLog));
    });
  }

  return {
    addLog,
    close: (cb) => wss.close(cb),
  };
}

function addLogInTerminal(data, type, error) {
  // to see what's going on in jenkins
  let outLog = type + ':' + data.toString();
  if (!!error) {
    console.error('  -' + outLog);
  } else {
    console.log('  -' + outLog);
  }
}
