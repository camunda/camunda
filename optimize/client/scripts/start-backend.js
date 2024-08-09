/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const {spawn} = require('child_process');
const path = require('path');
const os = require('os');
const fetch = require('node-fetch');
const fs = require('fs');
const xml2js = require('xml2js');

const createServer = require('./managementServer/server.js');

// argument to determine if we are in CI mode
const ciMode = process.argv.indexOf('ci') > -1;
if (!ciMode) {
  require('dotenv').config();
}

let mode = 'self-managed';
if (process.argv.indexOf('cloud') > -1) {
  mode = 'cloud';
}

// if we are in ci mode we assume data generation is already complete

let backendProcess;
let buildBackendProcess;
let dockerProcess;

let backendVersion;
let elasticSearchVersion;
let zeebeVersion;
let identityVersion;

const commonEnv = {
  OPTIMIZE_API_ACCESS_TOKEN: 'secret',
  OPTIMIZE_SUPER_USER_IDS: '[demo]',
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
  CAMUNDA_OPTIMIZE_UI_LOGOUT_HIDDEN: 'true',
  CAMUNDA_OPTIMIZE_NOTIFICATIONS_URL: 'https://notifications.cloud.dev.ultrawombat.com',
};

const selfManagedEnv = {
  SPRING_PROFILES_ACTIVE: 'ccsm',
  ZEEBE_IMPORT_ENABLED: 'true',
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
  CAMUNDA_OPTIMIZE_ZEEBE_PARTITION_COUNT: '2',
  CAMUNDA_OPTIMIZE_IDENTITY_BASE_URL: 'http://localhost:8081/',
  OPTIMIZE_ELASTICSEARCH_HOST: 'localhost',
  OPTIMIZE_ELASTICSEARCH_HTTP_PORT: '9200',
  CAMUNDA_OPTIMIZE_API_AUDIENCE: 'optimize',
  CAMUNDA_OPTIMIZE_IMPORT_DATA_SKIP_DATA_AFTER_NESTED_DOC_LIMIT_REACHED: true,
  SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI:
    'http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/certs',
};

const server = createServer({showLogsInTerminal: ciMode}, {restartBackend});

setVersionInfo().then(setupEnvironment).then(startBackend);

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
      cloud: cloudEnv,
      'self-managed': selfManagedEnv,
    };

    backendProcess = spawnWithArgs(
      `java -cp ${classPaths}  -Xms1g -Xmx1g -XX:MaxMetaspaceSize=256m -Dfile.encoding=UTF-8 io.camunda.optimize.Main`,
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
    startDocker(),
    buildBackend().catch(() => {
      console.log('Optimize build interrupted');
    }),
  ]);
}

function buildBackend() {
  return new Promise((resolve, reject) => {
    buildBackendProcess = spawnWithArgs(
      'mvn clean install -DskipTests -Dskip.docker -Dskip.fe.build -pl optimize/backend -am',
      {
        cwd: path.resolve(__dirname, '..', '..', '..'),
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

function startDocker() {
  if (dockerProcess) {
    return Promise.resolve();
  }

  return new Promise((resolve) => {
    dockerProcess = spawnWithArgs('docker-compose up --force-recreate --no-color', {
      cwd: path.resolve(__dirname, '..'),
      shell: true,
      env: {
        ...process.env, // https://github.com/nodejs/node/issues/12986#issuecomment-301101354
        ES_VERSION: elasticSearchVersion,
        ZEEBE_VERSION: zeebeVersion,
        IDENTITY_VERSION: identityVersion,
        COMPOSE_PROFILES: mode,
      },
    });

    dockerProcess.stdout.on('data', (data) => server.addLog(data, 'docker'));
    dockerProcess.stderr.on('data', (data) => server.addLog(data, 'docker', true));

    process.on('SIGINT', stopDocker);
    process.on('SIGTERM', stopDocker);

    // wait for the zeebe rest endpoint to be up before resolving the promise
    serverCheck('http://localhost:9600/ready', resolve);
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
        elasticSearchVersion = properties['elasticsearch8.test.version'];
        zeebeVersion = properties['zeebe.version'];
        identityVersion = properties['identity.version'];
        resolve();
      });
    });
  });
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

function spawnWithArgs(commandString, options) {
  const args = commandString.split(' ');
  const command = args.splice(0, 1)[0];
  return spawn(command, args, options);
}
