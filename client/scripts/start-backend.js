/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const {spawn} = require('child_process');
const path = require('path');
const os = require('os');
const fetch = require('node-fetch');
const fs = require('fs');
const xml2js = require('xml2js');

const users = require('../demo-data/users.json');
const license = require('./license');
const createServer = require('./managementServer/server.js');

// argument to determine if we are in CI mode
const ciMode = process.argv.indexOf('ci') > -1;
if (!ciMode) {
  require('dotenv').config();
}

let mode = 'platform';
if (process.argv.indexOf('cloud') > -1) {
  mode = 'cloud';
}
if (process.argv.indexOf('self-managed') > -1) {
  mode = 'self-managed';
}

// if we are in ci mode we assume data generation is already complete
let platformDataGenerationComplete = ciMode;
let eventIngestionComplete = false;
let seenStateInitializationComplete = false;

let backendProcess;
let buildBackendProcess;
let dockerProcess;
let dataGeneratorProcess;

let backendVersion;
let elasticSearchVersion;
let cambpmVersion;
let zeebeVersion;

const commonEnv = {
  OPTIMIZE_API_ACCESS_TOKEN: 'secret',
  OPTIMIZE_SUPER_USER_IDS: '[demo]',
};

const platformEnv = {
  OPTIMIZE_CAMUNDA_BPM_EVENT_IMPORT_ENABLED: 'true',
  OPTIMIZE_EVENT_BASED_PROCESSES_IMPORT_ENABLED: 'true',
  OPTIMIZE_EVENT_BASED_PROCESSES_USER_IDS: `[${users.join(',')},demo]`,
};

const cloudEnv = {
  SPRING_PROFILES_ACTIVE: 'cloud',
  ZEEBE_IMPORT_ENABLED: 'true',
  CAMUNDA_OPTIMIZE_AUTH0_BACKENDDOMAIN: 'camunda-excitingdev.eu.auth0.com',
  CAMUNDA_OPTIMIZE_AUTH0_CLIENTID: '4ySAuc47zUsrQVHzQGTTPSDCiecSoqnp',
  CAMUNDA_OPTIMIZE_AUTH0_CLIENTSECRET: process.env.AUTH0_CLIENTSECRET,
  CAMUNDA_OPTIMIZE_AUTH0_DOMAIN: 'weblogin.cloud.dev.ultrawombat.com',
  CAMUNDA_OPTIMIZE_AUTH0_ORGANIZATION: 'f4e522a8-f642-4293-b5cb-1d14e1730534',
  CAMUNDA_OPTIMIZE_AUTH0_TOKEN_URL: 'https://login.cloud.dev.ultrawombat.com/oauth/token',
  SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI:
    'https://camunda-excitingdev.eu.auth0.com/.well-known/jwks.json',
  CAMUNDA_OPTIMIZE_CLIENT_AUDIENCE: 'optimize.dev.ultrawombat.com',
  CAMUNDA_OPTIMIZE_M2M_ACCOUNTS_URL: 'https://accounts.cloud.dev.ultrawombat.com',
  CAMUNDA_OPTIMIZE_M2M_ACCOUNTS_AUTH0_AUDIENCE: 'cloud.dev.ultrawombat.com',
};

const selfManagedEnv = {
  SPRING_PROFILES_ACTIVE: 'ccsm',
  CAMUNDA_OPTIMIZE_ZEEBE_ENABLED: 'true',
  CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL: 'http://localhost:18080/auth/realms/camunda-platform',
  CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_BACKEND_URL:
    'http://localhost:18080/auth/realms/camunda-platform',
  CAMUNDA_OPTIMIZE_IDENTITY_CLIENTID: 'optimize',
  CAMUNDA_OPTIMIZE_IDENTITY_CLIENTSECRET: 'XALaRPl5qwTEItdwCMiPS62nVpKs7dL7',
  CAMUNDA_OPTIMIZE_IDENTITY_AUDIENCE: 'optimize-api',
  CAMUNDA_OPTIMIZE_SECURITY_AUTH_COOKIE_SAME_SITE_ENABLED: 'false',
  CAMUNDA_OPTIMIZE_ENTERPRISE: 'false',
  CAMUNDA_OPTIMIZE_ZEEBE_NAME: 'zeebe-record',
  CAMUNDA_OPTIMIZE_ZEEBE_PARTITION_COUNT: '1',
};

const server = createServer(
  {showLogsInTerminal: ciMode},
  {generateData, isDataGenerationCompleted, restartBackend}
);

setVersionInfo()
  .then(setupEnvironment)
  .then(startBackend)
  .then(setLicense)
  .then(
    () => mode === 'platform' && Promise.all([setWhatsNewSeenStateForAllUsers(), ingestEventData()])
  );

function startBackend() {
  return new Promise((resolve, reject) => {
    const pathSep = os.platform() === 'win32' ? ';' : ':';
    const classPaths = [
      '../src/main/resources/',
      './lib/*',
      `optimize-backend-${backendVersion}.jar`,
      '../../client/demo-data/',
    ].join(pathSep);

    const engineEnv = {
      platform: platformEnv,
      cloud: cloudEnv,
      'self-managed': selfManagedEnv,
    };

    backendProcess = spawnWithArgs(
      `java -cp ${classPaths}  -Xms1g -Xmx1g -XX:MaxMetaspaceSize=256m -Dfile.encoding=UTF-8 org.camunda.optimize.Main`,
      {
        cwd: path.resolve(__dirname, '..', '..', 'backend', 'target'),
        shell: true,
        env: {
          ...process.env,
          ...commonEnv,
          ...engineEnv[mode],
        },
      }
    );

    backendProcess.stdout.on('data', (data) => server.addLog(data, 'backend'));
    backendProcess.stderr.on('data', (data) => server.addLog(data, 'backend', true));
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

function restartBackend() {
  if (buildBackendProcess) {
    buildBackendProcess.kill();
  }
  if (backendProcess) {
    backendProcess.kill();
  }
  setupEnvironment().then(startBackend);
}

async function setupEnvironment() {
  if (ciMode) {
    return;
  }

  await Promise.all([
    startDocker().then(() => mode === 'platform' && loadPlatformDemoData()),
    buildBackend().catch(() => {
      console.log('Optimize build interrupted');
    }),
  ]);
}

function buildBackend() {
  return new Promise((resolve, reject) => {
    buildBackendProcess = spawnWithArgs(
      'mvn clean install -DskipTests -Dskip.docker -Dskip.fe.build -pl backend,qa/data-generation -am',
      {
        cwd: path.resolve(__dirname, '..', '..'),
        shell: true,
      }
    );

    buildBackendProcess.stdout.on('data', (data) => server.addLog(data, 'backend'));
    buildBackendProcess.stderr.on('data', (data) => server.addLog(data, 'backend', true));
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

async function setLicense() {
  await fetch('http://localhost:8090/api/license/validate-and-store', {
    method: 'POST',
    body: license,
  });
}

function loadPlatformDemoData() {
  return new Promise(async (resolve) => {
    await downloadFile('gs://optimize-data/optimize_data-e2e.sqlc', 'databaseDumps/dump.sqlc');

    dataGeneratorProcess = spawnWithArgs(
      'docker exec postgres pg_restore --clean --if-exists -v -h localhost -U camunda -d engine dump/dump.sqlc'
    );

    dataGeneratorProcess.stdout.on('data', (data) => {
      server.addLog(data.toString(), 'dataGenerator');
    });

    dataGeneratorProcess.stderr.on('data', (data) => {
      server.addLog(data.toString(), 'dataGenerator', true);
    });

    dataGeneratorProcess.on('exit', () => {
      platformDataGenerationComplete = true;
      dataGeneratorProcess = null;
      resolve();
      spawnWithArgs('rm -rf databaseDumps/', {shell: true});
    });
  });
}

function generateData() {
  dataGeneratorProcess = runScript('generate-data');

  dataGeneratorProcess.on('exit', () => {
    dataGeneratorProcess = null;
  });
}

function ingestEventData() {
  const eventIngestProcess = runScript('ingest-event-data');

  eventIngestProcess.on('exit', () => {
    eventIngestionComplete = true;
  });
}

function setWhatsNewSeenStateForAllUsers() {
  // wait for users to be generated before setting the what's new seen state
  setTimeout(() => {
    if (!platformDataGenerationComplete) {
      return setWhatsNewSeenStateForAllUsers();
    }

    const seenStateProcess = runScript('set-whatsnew-seen-state');

    seenStateProcess.on('exit', () => {
      seenStateInitializationComplete = true;
    });
  }, 1000);
}

function downloadFile(downloadUrl, filePath) {
  return new Promise(async (resolve) => {
    const downloadFile = spawnWithArgs(`gsutil -q cp ${downloadUrl} ${filePath}`, {shell: true});
    downloadFile.on('close', () => {
      resolve();
    });
  });
}

function startDocker() {
  if (dockerProcess) {
    return Promise.resolve();
  }

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
        COMPOSE_PROFILES: mode,
      },
    });

    dockerProcess.stdout.on('data', (data) => server.addLog(data, 'docker'));
    dockerProcess.stderr.on('data', (data) => server.addLog(data, 'docker', true));

    process.on('SIGINT', stopDocker);
    process.on('SIGTERM', stopDocker);

    // wait for the engine/zeebe rest endpoint to be up before resolving the promise
    serverCheck(
      mode === 'platform' ? 'http://localhost:8080' : 'http://localhost:9600/ready',
      resolve
    );
  });
}

function setVersionInfo() {
  return new Promise((resolve) => {
    fs.readFile(path.resolve(__dirname, '..', '..', 'pom.xml'), 'utf8', (err, data) => {
      xml2js.parseString(data, {explicitArray: false}, (err, data) => {
        if (err) {
          return console.error(err);
        }

        backendVersion = data.project.version;
        const properties = data.project.properties;
        elasticSearchVersion = properties['elasticsearch.version'];
        cambpmVersion = properties['camunda.engine.version'];
        zeebeVersion = properties['zeebe.version'];
        resolve();
      });
    });
  });
}

function isDataGenerationCompleted() {
  return (
    platformDataGenerationComplete && eventIngestionComplete && seenStateInitializationComplete
  );
}

function stopDocker() {
  const dockerStopProcess = spawnWithArgs('docker-compose rm -sfv', {
    cwd: path.resolve(__dirname, '..'),
    shell: true,
  });

  dockerStopProcess.on('close', () => {
    dockerProcess = null;
    process.exit();
  });
}

function serverCheck(url, onComplete) {
  setTimeout(async () => {
    try {
      const response = await fetch(url);
      if (!response.ok) {
        return serverCheck(url, onComplete);
      }
    } catch (e) {
      return serverCheck(url, onComplete);
    }
    onComplete();
  }, 1000);
}

function runScript(scriptName) {
  const startedProcess = spawnWithArgs('node scripts/' + scriptName);

  startedProcess.stdout.on('data', (data) => server.addLog(data, 'dataGenerator'));
  startedProcess.stderr.on('data', (data) => server.addLog(data, 'dataGenerator', true));

  process.on('SIGINT', () => startedProcess.kill('SIGINT'));
  process.on('SIGTERM', () => startedProcess.kill('SIGTERM'));

  return startedProcess;
}

function spawnWithArgs(commandString, options) {
  const args = commandString.split(' ');
  const command = args.splice(0, 1)[0];
  return spawn(command, args, options);
}
