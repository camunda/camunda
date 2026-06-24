/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createServer as _createServer} from 'http';
import {WebSocketServer} from 'ws';
import opn from 'opn';
import ansiHTML from 'ansi-html';
import {parse, join, extname as _extname, dirname} from 'path';
import {realpathSync, readFile} from 'fs';
import {fileURLToPath} from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));

export default function createServer({showLogsInTerminal}, {restartBackend}) {
  let addLog;

  const server = _createServer(async (request, response) => {
    if (request.url === '/api/restartBackend') {
      addLog('--------- BACKEND RESTART INITIATED ---------', 'backend');
      restartBackend();
      response.statusCode = 200;
      response.end('Restarting Optimize backend...', 'utf-8');
      return;
    }

    var filename = parse(request.url).base || 'index.html';
    var filePath;

    try {
      filePath = realpathSync(join(__dirname, filename));
    } catch (_error) {
      response.writeHead(500);
      response.end('Internal server error', 'utf-8');
      return;
    }

    var extname = String(_extname(filePath)).toLowerCase();
    var mimeTypes = {
      '.html': 'text/html',
      '.js': 'text/javascript',
      '.css': 'text/css',
    };

    var contentType = mimeTypes[extname] || 'application/octet-stream';

    if (!filePath.startsWith(__dirname)) {
      response.writeHead(403, {'Content-Type': contentType});
      response.end('Forbidden', 'utf-8');
      return;
    }

    readFile(filePath, function (error, content) {
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
}

function createWebSocketServer(server) {
  const connectedSockets = [];

  const logs = {
    backend: [],
    docker: [],
  };

  const wss = new WebSocketServer({server});
  wss.on('connection', function connection(ws) {
    try {
      connectedSockets.push(ws);

      logs.backend.slice(-200).forEach((log) => ws.send(JSON.stringify(log)));
      logs.docker.slice(-400).forEach((log) => ws.send(JSON.stringify(log)));

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
  let outLog = type + ':' + data.toString();
  if (error) {
    console.error('  -' + outLog);
  } else {
    console.log('  -' + outLog);
  }
}
