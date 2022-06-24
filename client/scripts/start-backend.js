/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const {spawn} = require('child_process');
const path = require('path');
const fetch = require('node-fetch');
const http = require('http');
const fs = require('fs');
const WebSocket = require('ws');
const opn = require('opn');
const ansiHTML = require('ansi-html');
const xml2js = require('xml2js');
const users = require('../demo-data/users.json');
const license = require('./license');

// argument to determine if we are in CI mode
const ciMode = process.argv.indexOf('ci') > -1;
const zeebeMode = process.argv.indexOf('zeebe') > -1;

// if we are in ci mode we assume data generation is already complete
let engineDataGenerationComplete = ciMode;
let eventIngestionComplete = false;
let seenStateInitializationComplete = false;

let backendProcess;
let buildBackendProcess;
let dockerProcess;
let dataGeneratorProcess;

fs.readFile(path.resolve(__dirname, '..', '..', 'pom.xml'), 'utf8', (err, data) => {
  xml2js.parseString(data, {explicitArray: false}, (err, data) => {
    if (err) {
      console.error(err);
      return -1;
    }

    const backendVersion = data.project.version;
    const elasticSearchVersion = data.project.properties['elasticsearch.version'];
    const cambpmVersion = data.project.properties['camunda.engine.version'];
    const zeebeVersion = data.project.properties['zeebe.version'];

    startManagementServer();

    function buildAndStartOptimize() {
      Promise.all([
        dockerProcess ? Promise.resolve() : startDocker().then(restoreSqlDump),
        buildBackend().catch(() => {
          console.log('Optimize build interrupted');
        }),
      ]).then(() => {
        startBackend()
          .then(postStartupActions)
          .catch(() => {
            console.log('Optimize process killed, restarting...');
          });
      });
    }

    if (ciMode) {
      startBackend().then(postStartupActions);
    } else {
      buildAndStartOptimize();
    }

    const logs = {
      backend: [],
      docker: [],
      dataGenerator: [],
    };

    const connectedSockets = [];

    function buildBackend() {
      return new Promise((resolve, reject) => {
        buildBackendProcess = spawnWithArgs(
          'mvn clean install -DskipTests -Dskip.docker -Dskip.fe.build -pl backend,qa/data-generation -am',
          {
            cwd: path.resolve(__dirname, '..', '..'),
            shell: true,
          }
        );

        buildBackendProcess.stdout.on('data', (data) => addLog(data, 'backend'));
        buildBackendProcess.stderr.on('data', (data) => addLog(data, 'backend', true));
        buildBackendProcess.on('close', (code) => {
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
        const eventUserIds = users.join(',') + ',demo';
        const superUserIds = 'demo';
        backendProcess = spawnWithArgs(
          `java -cp ../src/main/resources/:./lib/*:optimize-backend-${backendVersion}.jar:../../client/demo-data/  -Xms1g -Xmx1g -XX:MaxMetaspaceSize=256m -Dfile.encoding=UTF-8 -DOPTIMIZE_API_ACCESS_TOKEN=secret -DOPTIMIZE_CAMUNDA_BPM_EVENT_IMPORT_ENABLED=true -DOPTIMIZE_EVENT_BASED_PROCESSES_IMPORT_ENABLED=true -DOPTIMIZE_EVENT_BASED_PROCESSES_USER_IDS=[${eventUserIds}] -DOPTIMIZE_SUPER_USER_IDS=[${superUserIds}] ${
            zeebeMode ? '-Dspring.profiles.active=cloud -DZEEBE_IMPORT_ENABLED=true' : ''
          } org.camunda.optimize.Main`,
          {
            cwd: path.resolve(__dirname, '..', '..', 'backend', 'target'),
            shell: true,
          }
        );

        backendProcess.stdout.on('data', (data) => addLog(data, 'backend'));
        backendProcess.stderr.on('data', (data) => addLog(data, 'backend', true));
        backendProcess.on('close', (code) => {
          backendProcess = null;
          if (code === 0) {
            resolve();
          } else {
            reject(code);
          }
        });

        // wait for the optimize endpoint to be up before resolving the promise
        serverCheck('http://localhost:8090/api/readyz', resolve);
      });
    }

    function startDocker() {
      return new Promise((resolve) => {
        // this directory should be mounted by docker, on Linux this results in root bering the owner of that directory
        // we create it with the current user to ensure we have write permissions
        if (!fs.existsSync('databaseDumps')) {
          fs.mkdirSync('databaseDumps');
        }
        dockerProcess = spawnWithArgs('docker-compose up --force-recreate --no-color', {
          cwd: path.resolve(__dirname, '..'),
          shell: true,
          env: {
            ...process.env, // https://github.com/nodejs/node/issues/12986#issuecomment-301101354
            ES_VERSION: elasticSearchVersion,
            CAMBPM_VERSION: cambpmVersion,
            ZEEBE_VERSION: zeebeVersion,
            COMPOSE_PROFILES: zeebeMode ? 'zeebe' : 'cambpm',
          },
        });

        dockerProcess.stdout.on('data', (data) => addLog(data, 'docker'));
        dockerProcess.stderr.on('data', (data) => addLog(data, 'docker', true));

        process.on('SIGINT', stopDocker);
        process.on('SIGTERM', stopDocker);

        // wait for the engine/zeebe rest endpoint to be up before resolving the promise
        serverCheck(zeebeMode ? 'http://localhost:9600/ready' : 'http://localhost:8080', resolve);
      });
    }

    function serverCheck(url, onComplete) {
      setTimeout(async () => {
        try {
          const response = await fetch(url);
          if (response.status !== 200) {
            return serverCheck(url, onComplete);
          }
        } catch (e) {
          return serverCheck(url, onComplete);
        }
        onComplete();
      }, 1000);
    }

    function restoreSqlDump() {
      return new Promise(async (resolve) => {
        await downloadFile('gs://optimize-data/optimize_data-e2e.sqlc', 'databaseDumps/dump.sqlc');

        dataGeneratorProcess = spawnWithArgs(
          'docker exec postgres pg_restore --clean --if-exists -v -h localhost -U camunda -d engine dump/dump.sqlc'
        );

        dataGeneratorProcess.stdout.on('data', (data) => {
          addLog(data.toString(), 'dataGenerator');
        });

        dataGeneratorProcess.stderr.on('data', (data) => {
          addLog(data.toString(), 'dataGenerator', true);
        });

        dataGeneratorProcess.on('exit', () => {
          engineDataGenerationComplete = true;
          dataGeneratorProcess = null;
          resolve();
          spawnWithArgs('rm -rf databaseDumps/');
        });
      });
    }

    function downloadFile(downloadUrl, filePath) {
      return new Promise(async (resolve) => {
        const downloadFile = spawnWithArgs(`gsutil -q cp ${downloadUrl} ${filePath}`);
        downloadFile.on('close', () => {
          resolve();
        });
      });
    }

    function generateDemoData() {
      dataGeneratorProcess = runDataGenerationProcess('generate-data');

      dataGeneratorProcess.on('exit', () => {
        engineDataGenerationComplete = true;
        dataGeneratorProcess = null;
      });
    }

    async function postStartupActions() {
      await ensureLicense();
      setWhatsNewSeenStateForAllUsers();
      ingestEventData();
    }

    async function ensureLicense() {
      await fetch('http://localhost:8090/api/license/validate-and-store', {
        method: 'POST',
        body: license,
      });
    }

    function ingestEventData() {
      const eventIngestProcess = runDataGenerationProcess('ingest-event-data');

      eventIngestProcess.on('exit', () => {
        eventIngestionComplete = true;
      });
    }

    function setWhatsNewSeenStateForAllUsers() {
      // wait for users to be generated before setting the what's new seen state
      setTimeout(() => {
        if (!engineDataGenerationComplete) {
          return setWhatsNewSeenStateForAllUsers();
        }

        const seenStateProcess = runDataGenerationProcess('set-whatsnew-seen-state');

        seenStateProcess.on('exit', () => {
          seenStateInitializationComplete = true;
        });
      }, 1000);
    }

    function runDataGenerationProcess(scriptName) {
      const startedProcess = spawnWithArgs('node scripts/' + scriptName);

      startedProcess.stdout.on('data', (data) => addLog(data, 'dataGenerator'));
      startedProcess.stderr.on('data', (data) => addLog(data, 'dataGenerator', true));

      process.on('SIGINT', () => startedProcess.kill('SIGINT'));
      process.on('SIGTERM', () => startedProcess.kill('SIGTERM'));

      return startedProcess;
    }

    function startManagementServer() {
      const server = http.createServer(function (request, response) {
        if (request.url === '/api/dataGenerationComplete') {
          response.writeHead(200, {'Content-Type': 'text/plain'});
          response.end(
            (
              engineDataGenerationComplete &&
              eventIngestionComplete &&
              seenStateInitializationComplete
            ).toString(),
            'utf-8'
          );
          return;
        }
        if (request.url === '/api/restartBackend') {
          addLog('--------- BACKEND RESTART INITIATED ---------', 'backend');
          if (buildBackendProcess) {
            buildBackendProcess.kill();
          }
          if (backendProcess) {
            backendProcess.kill();
          }
          buildAndStartOptimize();
          response.statusCode = 200;
          response.end('Restarting Optimize backend...', 'utf-8');
          return;
        }

        if (request.url === '/api/generateNewData') {
          if (dataGeneratorProcess) {
            response.statusCode = 400;
            response.end('Data is currently being generated', 'utf-8');
            return;
          }
          addLog('--------- DATA GENERATION INITIATED ---------', 'dataGenerator');
          generateDemoData();
          response.statusCode = 200;
          response.end('Data generation initiated', 'utf-8');
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

      const wss = new WebSocket.Server({server});

      wss.on('connection', function connection(ws) {
        connectedSockets.push(ws);

        logs.backend
          .slice(-200)
          .forEach((entry) => ws.send(JSON.stringify({...entry, type: 'backend'})));
        logs.docker
          .slice(-400)
          .forEach((entry) => ws.send(JSON.stringify({...entry, type: 'docker'})));

        logs.dataGenerator.forEach((entry) =>
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

      connectedSockets.forEach((socket) => {
        socket.send(
          JSON.stringify({
            data: ansiHTML(data.toString()),
            type,
            error: !!error,
          })
        );
      });
    }
  });
});

function stopDocker() {
  const dockerStopProcess = spawnWithArgs('docker-compose rm -sfv', {
    cwd: path.resolve(__dirname, '..'),
    shell: true,
  });

  dockerStopProcess.on('close', () => {
    process.exit();
    dockerProcess = null;
  });
}

function spawnWithArgs(commandString, options) {
  const args = commandString.split(' ');
  const command = args.splice(0, 1)[0];
  return spawn(command, args, options);
}
