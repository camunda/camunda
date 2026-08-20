/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {spawn} from 'child_process';
import {dirname, resolve as _resolve} from 'path';
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
let camundaVersion;

const commonEnv = {
  OPTIMIZE_API_ACCESS_TOKEN: 'secret',
  CAMUNDA_OPTIMIZE_DATABASE: database,
};

const cloudEnv = {
  SPRING_PROFILES_ACTIVE: 'cloud',
  ZEEBE_IMPORT_ENABLED: 'true',

  // Not auth config. Optimize's own SaaS access control reads these and fails startup on a
  // blank value, and the M2M Accounts client needs the token url and audience.
  CAMUNDA_OPTIMIZE_CLIENT_CLUSTERID: 'optimize-e2e-cloud',
  CAMUNDA_OPTIMIZE_AUTH0_ORGANIZATION: 'f4e522a8-f642-4293-b5cb-1d14e1730534',
  CAMUNDA_OPTIMIZE_AUTH0_TOKEN_URL: 'https://login.cloud.dev.ultrawombat.com/oauth/token',
  CAMUNDA_OPTIMIZE_M2M_ACCOUNTS_URL: 'https://accounts.cloud.dev.ultrawombat.com',
  CAMUNDA_OPTIMIZE_M2M_ACCOUNTS_AUTH0_AUDIENCE: 'cloud.dev.ultrawombat.com',

  CAMUNDA_SECURITY_AUTHENTICATION_METHOD: 'OIDC',
  CAMUNDA_SECURITY_SAAS_CLUSTERID: 'optimize-e2e-cloud',
  CAMUNDA_SECURITY_SAAS_ORGANIZATIONID: 'f4e522a8-f642-4293-b5cb-1d14e1730534',
  CAMUNDA_SECURITY_AUTHENTICATION_OIDC_CLIENTID: '4ySAuc47zUsrQVHzQGTTPSDCiecSoqnp',
  CAMUNDA_SECURITY_AUTHENTICATION_OIDC_CLIENTSECRET: process.env.AUTH0_CLIENTSECRET,
  CAMUNDA_SECURITY_AUTHENTICATION_OIDC_ISSUERURI: 'https://weblogin.cloud.dev.ultrawombat.com/',
  CAMUNDA_SECURITY_AUTHENTICATION_OIDC_JWKSETURI:
    'https://camunda-excitingdev.eu.auth0.com/.well-known/jwks.json',
  CAMUNDA_SECURITY_AUTHENTICATION_OIDC_ORGANIZATIONID: 'f4e522a8-f642-4293-b5cb-1d14e1730534',
  // Placeholder form on purpose, this is byte for byte what the bridge derives today.
  CAMUNDA_SECURITY_AUTHENTICATION_OIDC_REDIRECTURI:
    '{baseScheme}://{baseHost}{basePort}/sso-callback?uuid=optimize-e2e-cloud',
  // Login id_token carries the client id as its only aud, bearer tokens carry the resource one.
  CAMUNDA_SECURITY_AUTHENTICATION_OIDC_AUDIENCES:
    'optimize.dev.ultrawombat.com,4ySAuc47zUsrQVHzQGTTPSDCiecSoqnp',
  CAMUNDA_SECURITY_AUTHENTICATION_OIDC_AUTHORIZEREQUEST_ADDITIONALPARAMETERS_AUDIENCE:
    'cloud.dev.ultrawombat.com',

  CAMUNDA_OPTIMIZE_UI_LOGOUT_HIDDEN: 'true',
  CAMUNDA_OPTIMIZE_NOTIFICATIONS_URL: 'https://notifications.cloud.dev.ultrawombat.com',
};

const selfManagedEnv = {
  SPRING_PROFILES_ACTIVE: 'ccsm',
  CAMUNDA_OPTIMIZE_ZEEBE_ENABLED: 'true',
  CAMUNDA_OPTIMIZE_ZEEBE_REST_ADDRESS: 'http://localhost:8080',
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
  CAMUNDA_OPTIMIZE_IMPORT_DATA_SKIP_DATA_AFTER_NESTED_DOC_LIMIT_REACHED: 'true',
  OPTIMIZE_LOG_LEVEL: 'DEBUG',
  SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI:
    'http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/certs',
  MANAGEMENT_SERVER_PORT: '19600',
};

const server = createServer({showLogsInTerminal: ciMode}, {restartBackend});

setVersionInfo()
  .then(setupEnvironment)
  .then(startBackend)
  .catch((err) => {
    // surface the fail-fast message from setVersionInfo as a clear line, not an unhandled rejection.
    // startBackend/setupEnvironment reject with a bare exit code rather than an Error, so fall back
    // to the raw value when there is no message.
    console.error(err.message ?? err);
    process.exit(1);
  });

function startBackend() {
  return new Promise((resolve, reject) => {
    const engineEnv = {
      cloud: cloudEnv,
      'self-managed': selfManagedEnv,
    };

    backendProcess = spawnWithArgs(
      `./mvnw -f optimize/backend/pom.xml spring-boot:run -Dspring-boot.run.additionalClasspathElements=optimize/client/demo-data`,
      {
        cwd: _resolve(__dirname, '..', '..', '..'),
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
      console.error('Optimize build interrupted');
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
    buildBackendProcess.stderr.on('data', (data) => {
      console.error(`backend build stderr: ${data}`);
      server.addLog(data, 'backend', true);
    });
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
  console.log(`Starting docker with profile ${mode}:${database}...`);
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
        // we assume that the version of operate is the same as zeebe
        CAMUNDA_VERSION: camundaVersion,
        // to start only the opensearch services, we create profiles for mode + database
        // so we can better control which services are started
        COMPOSE_PROFILES: [`${mode}:${database}`].join(','),
      },
    });

    dockerProcess.stdout.on('data', (data) => server.addLog(data, 'docker'));
    dockerProcess.stderr.on('data', (data) => {
      process.stderr.write(`docker stderr: ${data}`);
      server.addLog(data, 'docker', true);
    });

    process.on('SIGINT', stopDocker);
    process.on('SIGTERM', stopDocker);

    waitForDockerDependencies().then(resolve);
  });
}

function waitForDockerDependencies() {
  const dependencies = [
    {url: 'http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=1s'},
  ];

  if (mode === 'self-managed') {
    dependencies.push(
      {url: 'http://localhost:18080/auth'},
      {url: 'http://localhost:9600/ready'},
      // The C8 REST API requires authentication, so a 401/403 still means the gateway on port 8080
      // is up and serving requests. A 5xx (e.g. a gateway that is still starting) is not accepted,
      // so the readiness gate stays meaningful.
      {url: 'http://localhost:8080/v2/topology', acceptStatuses: [401, 403]}
    );
  }

  return Promise.all(
    dependencies.map(({url, acceptStatuses}) => waitForServerCheck(url, acceptStatuses))
  );
}

function waitForServerCheck(url, acceptStatuses) {
  return new Promise((resolve) => serverCheck(url, resolve, acceptStatuses));
}

function parsePomProperties(pomPath) {
  return new Promise((resolve) => {
    readFile(pomPath, 'utf8', (readErr, data) => {
      if (readErr) {
        console.error(`Failed to read ${pomPath}:`, readErr);
        return resolve({properties: {}, version: undefined});
      }
      parseString(data, {explicitArray: false}, (parseErr, parsed) => {
        if (parseErr) {
          console.error(parseErr);
          return resolve({properties: {}, version: undefined});
        }
        resolve({
          properties: parsed?.project?.properties ?? {},
          version: parsed?.project?.version,
        });
      });
    });
  });
}

// Resolves Maven ${...} property placeholders using the given properties map.
function resolvePlaceholders(value, properties) {
  if (typeof value !== 'string') {
    return value;
  }
  let resolved = value;
  for (let i = 0; i < 10 && resolved.includes('${'); i++) {
    resolved = resolved.replace(/\$\{([^}]+)\}/g, (match, key) =>
      properties[key] !== undefined ? properties[key] : match
    );
  }
  return resolved;
}

async function setVersionInfo() {
  const optimizePom = await parsePomProperties(_resolve(__dirname, '..', '..', 'pom.xml'));
  // Some version properties in optimize/pom.xml reference properties defined in the
  // parent pom (e.g. ${version.elasticsearch.container}), so we resolve against both.
  const parentPom = await parsePomProperties(
    _resolve(__dirname, '..', '..', '..', 'parent', 'pom.xml')
  );
  const properties = {...parentPom.properties, ...optimizePom.properties};

  backendVersion = optimizePom.version;
  elasticSearchVersion = resolvePlaceholders(properties['elasticsearch.test.version'], properties);
  opensearchVersion = resolvePlaceholders(properties['opensearch.test.version'], properties);
  camundaVersion = resolvePlaceholders(properties['camunda.docker.version'], properties);

  // Fail fast with a clear message rather than letting an undefined/unresolved version reach
  // docker-compose, where it surfaces as an opaque image-pull failure.
  const unresolved = Object.entries({
    'optimize/pom.xml <version>': backendVersion,
    'elasticsearch.test.version': elasticSearchVersion,
    'opensearch.test.version': opensearchVersion,
    'camunda.docker.version': camundaVersion,
  })
    .filter(([, value]) => !value || String(value).includes('${'))
    .map(([name]) => name);
  if (unresolved.length > 0) {
    throw new Error(
      `Could not resolve version(s) from pom.xml: ${unresolved.join(', ')}. ` +
        'Ensure optimize/pom.xml and parent/pom.xml are readable and define these properties.'
    );
  }

  console.log(
    `Backend version: ${backendVersion}, Elasticsearch version: ${elasticSearchVersion}, Opensearch version: ${opensearchVersion}, Camunda/Identity version: ${camundaVersion}`
  );
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

function serverCheck(url, onComplete, acceptStatuses = []) {
  setTimeout(async () => {
    try {
      const response = await fetch(url);
      if (!response.ok && !acceptStatuses.includes(response.status)) {
        return serverCheck(url, onComplete, acceptStatuses);
      }
    } catch (_e) {
      return serverCheck(url, onComplete, acceptStatuses);
    }
    onComplete();
  }, 1000);
}

function spawnWithArgs(commandString, options) {
  const args = commandString.split(' ');
  const command = args.splice(0, 1)[0];
  return spawn(command, args, options);
}
