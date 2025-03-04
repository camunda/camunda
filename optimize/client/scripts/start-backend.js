/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {spawn} from 'child_process';
import {resolve as _resolve, dirname} from 'path';
import {readFile} from 'fs';
import {parseString} from 'xml2js';
import {fileURLToPath} from 'url';
import {config} from 'dotenv';

import createServer from './managementServer/server.js';

const __dirname = dirname(fileURLToPath(import.meta.url));

// argument to determine if we are in CI mode
const ciMode = process.argv.indexOf('ci') > -1;
if (!ciMode) {
  config();
}

let mode = 'self-managed';
let database = 'elasticsearch';
if (process.argv.indexOf('cloud') > -1) {
  mode = 'cloud';
}
if (process.argv.indexOf('opensearch') > -1) {
  database = 'opensearch';
}

// if we are in ci mode we assume data generation is already complete

let backendProcess;
let buildBackendProcess;
let dockerProcess;

let backendVersion;
let elasticSearchVersion;
let opensearchVersion;
let zeebeVersion;
let identityVersion;

const commonEnv = {
  OPTIMIZE_API_ACCESS_TOKEN: 'secret',
  CAMUNDA_OPTIMIZE_DATABASE: database,
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
  OPTIMIZE_OPENSEARCH_HOST: 'localhost',
  OPTIMIZE_OPENSEARCH_HTTP_PORT: '9200',
  CAMUNDA_OPTIMIZE_API_AUDIENCE: 'optimize',
  CAMUNDA_OPTIMIZE_IMPORT_DATA_SKIP_DATA_AFTER_NESTED_DOC_LIMIT_REACHED: true,
  SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI:
    'http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/certs',
  MANAGEMENT_SERVER_PORT: '19600',
};

const server = createServer({showLogsInTerminal: ciMode}, {restartBackend});

setVersionInfo().then(setupEnvironment).then(startBackend);

function startBackend() {
  return new Promise((resolve, reject) => {
    const engineEnv = {
      cloud: cloudEnv,
      'self-managed': selfManagedEnv,
    };

    backendProcess = spawnWithArgs(
      `mvn -f optimize/backend/pom.xml exec:java -Dexec.mainClass=io.camunda.optimize.Main`,
      {
        cwd: _resolve(__dirname, '..', '..', '..'),
        shell: true,
        env: {
          ...process.env,
          ...commonEnv,
          ...engineEnv[mode],
          CLASSPATH_PREFIX: 'optimize/client/demo-data'
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
      console.err('Optimize build interrupted');
    }),
  ]);
}

function buildBackend() {
  return new Promise((resolve, reject) => {
    buildBackendProcess = spawnWithArgs(
      'mvn -f optimize/pom.xml clean install -T1C -DskipTests -Dskip.docker -pl backend -am',
      {
        cwd: _resolve(__dirname, '..', '..', '..'),
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
      cwd: _resolve(__dirname, '..'),
      shell: true,
      env: {
        ...process.env, // https://github.com/nodejs/node/issues/12986#issuecomment-301101354
        ES_VERSION: elasticSearchVersion,
        OS_VERSION: opensearchVersion,
        ZEEBE_VERSION: zeebeVersion,
        IDENTITY_VERSION: identityVersion,
        // we assume that the version of operate is the same as zeebe
        OPERATE_VERSION: zeebeVersion,
        // to start only the opensearch services, we create profiles for mode + database
        // so we can better control which services are started
        COMPOSE_PROFILES: [`${mode}:${database}`].join(','),
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
    readFile(_resolve(__dirname, '..', '..', 'pom.xml'), 'utf8', (_err, data) => {
      parseString(data, {explicitArray: false}, (err, data) => {
        if (err) {
          return console.error(err);
        }

        backendVersion = data.project.version;
        const properties = data.project.properties;
        elasticSearchVersion = properties['elasticsearch.test.version'];
        opensearchVersion = properties['opensearch.test.version'];
        zeebeVersion = properties['zeebe.version'];
        identityVersion = properties['identity.version'];
        resolve();
      });
    });
  });
}

function stopDocker() {
  const dockerStopProcess = spawnWithArgs('docker-compose rm -sfv', {
    cwd: _resolve(__dirname, '..'),
    shell: true,
    env: {
      // this ensures that all started containers are stopped
      COMPOSE_PROFILES: [mode, database, `${mode}:${database}`].join(','),
    },
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
    } catch (_e) {
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
