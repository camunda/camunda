/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const {spawn} = require('child_process');
const path = require('path');
const request = require('request');
const http = require('http');
const fs = require('fs');
const WebSocket = require('ws');
const opn = require('opn');
const ansiHTML = require('ansi-html');
const xml2js = require('xml2js');

// argument to determine if we are in CI mode
const ciMode = process.argv.indexOf('ci') > -1;

// if we are in ci mode we assume data generation is already complete
let engineDataGenerationComplete = ciMode;
let eventIngestionComplete = false;

let backendProcess;
let buildBackendProcess;
let dockerProcess;

fs.readFile(path.resolve(__dirname, '..', '..', 'pom.xml'), 'utf8', (err, data) => {
  xml2js.parseString(data, {explicitArray: false}, (err, data) => {
    if (err) {
      console.error(err);
      return -1;
    }

    const backendVersion = data.project.version;
    const elasticSearchVersion = data.project.properties['elasticsearch.version'];
    const cambpmVersion = data.project.profiles.profile[0].properties['camunda.engine.version'];

    startManagementServer();

    function buildAndStartOptimize() {
      buildBackend().then(() => {
        if (!dockerProcess) {
          startDocker().then(generateDemoData);
        }
        startBackend().then(ingestEventData).catch(() => {
          console.log("Optimize process killed, restarting...")
        });
      }).catch(() => {
        console.log("Optimize build interrupted")
      });
    }

    if (ciMode) {
      startBackend().then(ingestEventData);
    } else {
      buildAndStartOptimize();
    }

    const logs = {
      backend: [],
      docker: [],
      dataGenerator: []
    };

    const connectedSockets = [];

    function buildBackend() {
      return new Promise((resolve, reject) => {
        buildBackendProcess = spawn(
          'mvn',
          [
            'clean',
            'install',
            '-DskipTests',
            '-Dskip.docker',
            '-Dskip.fe.build',
            '-pl backend,qa/data-generation',
            '-am'
          ],
          {
            cwd: path.resolve(__dirname, '..', '..'),
            shell: true
          }
        );

        buildBackendProcess.stdout.on('data', data => addLog(data, 'backend'));
        buildBackendProcess.stderr.on('data', data => addLog(data, 'backend', true));
        buildBackendProcess.on('close', code => {
          buildBackendProcess = null;
          if (code === 0) {
            resolve();
          } else {
            reject(code);
          }
        });
      });
    }

    function startBackend() {
      return new Promise((resolve, reject) => {
        backendProcess = spawn(
          'java',
          [
            '-cp',
            `../src/main/resources/:optimize-backend-${backendVersion}.jar`,
            '-Xms1g',
            '-Xmx1g',
            '-XX:MetaspaceSize=256m',
            '-XX:MaxMetaspaceSize=256m',
            '-DOPTIMIZE_EVENT_INGESTION_API_SECRET=secret',
            '-DOPTIMIZE_EVENT_INGEST_IMPORT_ENABLED=true',
            'org.camunda.optimize.Main'
          ],
          {
            cwd: path.resolve(__dirname, '..', '..', 'backend', 'target'),
            shell: true
          }
        );

        backendProcess.stdout.on('data', data => addLog(data, 'backend'));
        backendProcess.stderr.on('data', data => addLog(data, 'backend', true));
        backendProcess.on('close', code => {
          backendProcess = null;
          if (code === 0) {
            resolve();
          } else {
            reject(code);
          }
        });

        // wait for the optimize endpoint to be up before resolving the promise
        serverCheck('http://localhost:8090', resolve);
      });
    }

    function startDocker() {
      return new Promise(resolve => {
        dockerProcess = spawn('docker-compose', ['up', '--force-recreate', '--no-color'], {
          cwd: path.resolve(__dirname, '..'),
          shell: true,
          env: {
            ...process.env, // https://github.com/nodejs/node/issues/12986#issuecomment-301101354
            ES_VERSION: elasticSearchVersion,
            CAMBPM_VERSION: cambpmVersion
          }
        });

        dockerProcess.stdout.on('data', data => addLog(data, 'docker'));
        dockerProcess.stderr.on('data', data => addLog(data, 'docker', true));

        process.on('SIGINT', stopDocker);
        process.on('SIGTERM', stopDocker);

        // wait for the engine rest endpoint to be up before resolving the promise
        serverCheck('http://localhost:8080', resolve);
      });
    }

    function serverCheck(url, onComplete) {
      setTimeout(() => {
        request(url, err => {
          if (err) {
            return serverCheck(url, onComplete);
          }
          onComplete();
        });
      }, 1000);
    }

    function generateDemoData() {
      const dataGenerator = spawn('node', ['scripts/generate-data']);

      dataGenerator.stdout.on('data', data => addLog(data, 'dataGenerator'));
      dataGenerator.stderr.on('data', data => addLog(data, 'dataGenerator', true));

      process.on('SIGINT', () => dataGenerator.kill('SIGINT'));
      process.on('SIGTERM', () => dataGenerator.kill('SIGTERM'));

      dataGenerator.on('exit', () => {
        engineDataGenerationComplete = true;
      });
    }

    function ingestEventData() {
      const eventIngestProcess = spawn('node', ['scripts/ingest-event-data']);

      eventIngestProcess.stdout.on('data', data => addLog(data, 'dataGenerator'));
      eventIngestProcess.stderr.on('data', data => addLog(data, 'dataGenerator', true));

      process.on('SIGINT', () => eventIngestProcess.kill('SIGINT'));
      process.on('SIGTERM', () => eventIngestProcess.kill('SIGTERM'));

      eventIngestProcess.on('exit', () => {
        eventIngestionComplete = true;
      });
    }

    function startManagementServer() {
      const server = http.createServer(function(request, response) {
        if (request.url === '/api/dataGenerationComplete') {
          response.writeHead(200, {'Content-Type': 'text/plain'});
          response.end((engineDataGenerationComplete && eventIngestionComplete).toString(), 'utf-8');
          return;
        }
        if (request.url === '/api/restartBackend') {
          addLog("--------- BACKEND RESTART INITIATED ---------", "backend");
          if (buildBackendProcess) {
            buildBackendProcess.kill();
          }
          if (backendProcess) {
            backendProcess.kill();
          }
          buildAndStartOptimize();
          response.statusCode = 200;
          response.end("Restarting Optimize backend...", "utf-8");
          return;
        }

        var filePath = __dirname + '/managementServer' + request.url;
        if (request.url === '/') {
          filePath += 'index.html';
        }

        var extname = String(path.extname(filePath)).toLowerCase();
        var mimeTypes = {
          '.html': 'text/html',
          '.js': 'text/javascript',
          '.css': 'text/css'
        };

        var contentType = mimeTypes[extname] || 'application/octet-stream';

        fs.readFile(filePath, function(error, content) {
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

      const wss = new WebSocket.Server({server});

      wss.on('connection', function connection(ws) {
        connectedSockets.push(ws);

        logs.backend
          .slice(-200)
          .forEach(entry => ws.send(JSON.stringify({...entry, type: 'backend'})));
        logs.docker
          .slice(-400)
          .forEach(entry => ws.send(JSON.stringify({...entry, type: 'docker'})));

        logs.dataGenerator.forEach(entry =>
          ws.send(JSON.stringify({...entry, type: 'dataGenerator'}))
        );

        ws.on('close', function close() {
          connectedSockets.splice(connectedSockets.indexOf(ws), 1);
        });
      });

      // closing the server to not having to manually kill it
      process.on('SIGINT', () => wss.close(() => server.close()));
      process.on('SIGTERM', () => wss.close(() => server.close()));

      server.listen(8100);

      opn('http://localhost:8100');

      console.log('Please check http://localhost:8100 for server logs!');
    }

    function addLog(data, type, error) {
      if (ciMode) {
        // to see what's going on in jenkins
        let outLog = type + ':' + data.toString();
        if (!!error) {
          console.error('  -' + outLog);
        } else {
          console.log('  -' + outLog);
        }
      }

      logs[type].push({data: ansiHTML(data.toString()), error: !!error});

      if (logs[type].length > 500) {
        logs[type].shift();
      }

      connectedSockets.forEach(socket => {
        socket.send(
          JSON.stringify({
            data: ansiHTML(data.toString()),
            type,
            error: !!error
          })
        );
      });
    }
  });
});

function stopDocker() {
  const dockerStopProcess = spawn('docker-compose', ['rm', '-sfv'], {
    cwd: path.resolve(__dirname, '..'),
    shell: true
  });

  dockerStopProcess.on('close', () => {
    process.exit();
    dockerProcess = null;
  });
}
