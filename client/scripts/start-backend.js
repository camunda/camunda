const {spawn} = require('child_process');
const path = require('path');
const request = require('request');
const http = require('http');
const fs = require('fs');
const WebSocket = require('ws');
const opn = require('opn');
const ansiHTML = require('ansi-html');

const CamundaClient = require('camunda-bpm-sdk-js').Client;

// adjust this number to specify how many parallel connections are used per process definition to create instances
const connections = 5;

startManagementServer();

buildBackend().then(startBackend);

startDocker().then(deployDemoData);

const logs = {
  backend: [],
  docker: []
};

const connectedSockets = [];

function buildBackend() {
  return new Promise((resolve, reject) => {
    const buildBackendProcess = spawn('mvn', ['clean', 'install', '-DskipTests', '-Dskip.docker'], {
      cwd: path.resolve(__dirname, '..', '..', 'backend'),
      shell: true
    });

    buildBackendProcess.stdout.on('data', data => addLog(data, 'backend'));
    buildBackendProcess.stderr.on('data', data => addLog(data, 'backend', true));
    buildBackendProcess.on('close', code => {
      if (code === 0) {
        resolve();
      } else {
        reject(code);
      }
    });
  });
}

function startBackend() {
  const backendProcess = spawn(
    'java',
    [
      '-cp',
      'optimize-backend-2.4.0-SNAPSHOT.jar',
      'org.camunda.optimize.Main',
      '-Xms1g',
      '-Xmx1g',
      '-XX:MetaspaceSize=256m',
      '-XX:MaxMetaspaceSize=256m'
    ],
    {
      cwd: path.resolve(__dirname, '..', '..', 'backend', 'target'),
      shell: true
    }
  );

  backendProcess.stdout.on('data', data => addLog(data, 'backend'));
  backendProcess.stderr.on('data', data => addLog(data, 'backend', true));
}

function startDocker() {
  return new Promise(resolve => {
    const dockerProcess = spawn('docker-compose', ['up', '--force-recreate', '--no-color'], {
      cwd: path.resolve(__dirname, '..'),
      shell: true
    });

    dockerProcess.stdout.on('data', data => addLog(data, 'docker'));
    dockerProcess.stderr.on('data', data => addLog(data, 'docker', true));

    process.on('SIGINT', () => {
      const dockerStopProcess = spawn('docker-compose', ['rm', '-sfv'], {
        cwd: path.resolve(__dirname, '..'),
        shell: true
      });

      dockerStopProcess.on('close', () => {
        process.exit();
      });
    });

    // wait for the engine rest endpoint to be up before resolving the promise
    function serverCheck() {
      setTimeout(() => {
        request('http://localhost:8080', err => {
          if (err) {
            return serverCheck();
          }
          resolve();
        });
      }, 1000);
    }

    serverCheck();
  });
}

function deployDemoData() {
  const data = require('../demo-data');

  const camAPI = new CamundaClient({
    mock: false,
    apiUri: 'http://localhost:8080/engine-rest'
  });
  const deploymentService = new camAPI.resource('deployment');
  const processDefinitionService = new camAPI.resource('process-definition');

  Object.keys(data).forEach(entry => {
    const {definition, instances} = data[entry];
    deploymentService
      .create({
        deploymentName: entry,
        files: [definition]
      })
      .then(resp => {
        const id = Object.keys(resp.deployedProcessDefinitions)[0];

        function startInstance(idx) {
          if (!instances[idx]) return;
          processDefinitionService
            .start({
              id,
              variables: instances[idx]
            })
            .then(() => {
              startInstance(idx + connections);
            })
            .catch(() => {
              startInstance(idx);
            });
        }
        for (let i = 0; i < connections; i++) {
          startInstance(i);
        }
      });
  });
}

function startManagementServer() {
  const server = http.createServer(function(request, response) {
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

    logs.backend.slice(-200).forEach(entry => ws.send(JSON.stringify({...entry, type: 'backend'})));
    logs.docker.slice(-400).forEach(entry => ws.send(JSON.stringify({...entry, type: 'docker'})));

    ws.on('close', function close() {
      connectedSockets.splice(connectedSockets.indexOf(ws), 1);
    });
  });

  server.listen(8100);

  opn('http://localhost:8100');

  console.log('Please check http://localhost:8100 for server logs!');
}

function addLog(data, type, error) {
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
