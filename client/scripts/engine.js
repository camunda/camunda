/* eslint no-console: 0 */

const download = require('download');
const shell = require('shelljs');
const path = require('path');
const CamundaClient = require('camunda-bpm-sdk-js').Client;
const chalk = require('chalk');
const utils = require('./utils');
let {c7ports} = require('./config');

const maxConnections = 5;
const communityUrl = 'https://camunda.org/release/camunda-bpm/tomcat/7.8/camunda-bpm-tomcat-7.8.0-alpha2.tar.gz';
const tmpDir = path.resolve(__dirname, '..', 'tmp');
// it can be configured, but it would pain in ass, so it is better
// to just remove it than configure it to be inside tmpDir
const databaseDir = path.resolve(__dirname, '..', 'camunda-h2-dbs');
const demoDataDir = path.resolve(__dirname, '..', 'demo-data');

if (process.argv[2] === 'start') {
  const portsArg = process.argv[3];

  if (portsArg && portsArg.length) {
    const ports = portsArg.split(',').map(port => +port);

    if (ports.every(port => !isNaN(port))) {
      c7ports = ports;
    }
  }

  init(c7ports).catch(console.error);
}

exports.init = init;

function init(ports = c7ports) {
  shell.rm('-rf', tmpDir);
  shell.rm('-rf', databaseDir);

  return download(communityUrl, tmpDir)
    .then(() => {
      console.log(chalk.blue(`Engine downloaded to ${tmpDir}!`));

      return utils.runInSequence(ports, startEngine, 1);
    });
}

function startEngine(c7port) {
  const extractTarget = path.resolve(tmpDir, `engine_${c7port}`);
  const engineUrl = `http://localhost:${c7port}`;
  const camAPI = new CamundaClient({
    mock: false,
    apiUri: engineUrl + '/engine-rest'
  });
  const deploymentService = new camAPI.resource('deployment');
  const processDefinitionService = new camAPI.resource('process-definition');
  const taskService = new camAPI.resource('task');
  const variableService = new camAPI.resource('variable');

  return extractEngine()
    .then(changeServerConfig)
    .then(startServer)
    .then(deployDefinitions)
    .then(startInstances)
    .then(completeTasks);

  function extractEngine() {
    const archive = utils.findFile(tmpDir, 'tar.gz');

    console.log(`Extracting ${archive} to ${extractTarget}...`);

    return utils
      .extract(archive, extractTarget)
      .then(() => console.log(chalk.green('Engine extracted')));
  }

  function changeServerConfig() {
    const configFile = utils.findPath(extractTarget, [
      'server',
      /tomcat/,
      'conf',
      'server.xml'
    ]);

    const startScript = path.resolve(
      extractTarget,
      utils.isWindows ? 'start-camunda.bat' : 'start-camunda.sh'
    );

    utils.changeFile(configFile, [
      {
        regexp: /port="8080"/g,
        replacement: `port="${c7port}"`
      },
      {
        regexp: /port="8005"/g,
        replacement: `port="${c7port + 1000}"`
      },
      {
        regexp: /port="8009"/g,
        replacement: `port="${c7port + 1500}"`
      },
      {
        regexp: /redirectPort="8443"/g,
        replacement: `redirectPort="${c7port + 2500}"`
      }
    ]);

    utils.changeFile(startScript, {
      regexp: /http:\/\/localhost:8080/g,
      replacement: `http://localhost:${c7port}`
    });
  }

  function startServer() {
    const startScript = utils.findPath(extractTarget, [
      'server',
      /tomcat/,
      'bin',
      utils.isWindows ? 'catalina.bat' : 'catalina.sh'
    ]);

    console.log(`Starting new instance of engine for ${c7port}...`);

    shell.exec(startScript + ' run', {async:true});

    return utils.waitForServer(engineUrl);
  }

  function deployDefinitions() {
    const demoDataModules = utils
      .findFile(demoDataDir, '.data.js', true)
      .sort();

    return utils.runInSequence(
      demoDataModules,
      (file) => {
        const module = require(file);

        return deploy(module)
          .then(matchResourcesWithIds(module))
          .then(module => {
            console.log(chalk.green(`DEPLOYED(${c7port}): `), file);

            return module;
          });
      },
      maxConnections
    );
  }

  function deploy(module) {
    return deploymentService.create({
      deploymentName: 'deployment ' + Math.random(),
      files: module.resources
    });
  }

  function matchResourcesWithIds(module) {
    const {resources} = module;

    return ({deployedProcessDefinitions}) => {
      if (!deployedProcessDefinitions) {
        throw new Error('Deployment failed');
      }

      return Object.assign({}, module, {
        resources: resources.map((resource) => {
          const {name} = resource;
          const id = Object
            .keys(deployedProcessDefinitions)
            .find(key => {
              const {resource} = deployedProcessDefinitions[key];

              return resource === name;
            });

          return Object.assign({id}, resource);
        })
      });
    };
  }

  function startInstances(modules) {
    return utils.runInSequence(
      modules,
      startModuleInstances,
      maxConnections
    );
  }

  function startModuleInstances(module) {
    const {resources, instances} = module;

    return utils.runInSequence(
      instances,
      (instanceDefinition) => {
        const {resource, variables} = instanceDefinition;
        const {id, taskIterationLimit} = resources.find(({name}) => name === resource);

        return processDefinitionService.start({
          id,
          variables
        }).then(instance => {
          console.log(chalk.green(`INSTANCE STARTED (${c7port})`), instance.id);

          return Object.assign({taskIterationLimit}, instanceDefinition, instance);
        });
      },
      maxConnections
    ).then(instances => {
      return Object.assign({}, module, {instances});
    });
  }

  function completeTasks(modules) {
    return utils.runInSequence(
      modules,
      completeModuleTasks,
      maxConnections
    );
  }

  function completeModuleTasks({instances}) {
    return utils.runInSequence(
      instances,
      // it may look bit strange, but map passes index as second argument to called function
      // which is not desired here, hence this strange arrow function is needed
      instance => completeInstanceTasks(instance),
      maxConnections
    );
  }

  function completeInstanceTasks(instance, iteration = 0) {
    let skipped = false;

    return Promise.all([getTaskList(instance), getVariables(instance)])
    .then(([tasks, previousVariables]) => {
      return utils.runInSequence(
        tasks,
        task => {
          const variables = instance.handleTask(task, previousVariables);

          if (variables) {
            return taskService.complete({
              id: task.id,
              variables
            });
          }

          skipped = true;

          return Promise.resolve();
        },
        maxConnections
      );
    })
    .then(() => taskService.count({processInstanceId: instance.id}))
    .then(count => {
      if (!skipped && count > 0 && instance.taskIterationLimit >= iteration) {
        return completeInstanceTasks(instance, iteration + 1);
      }

      console.log(chalk.green(`INSTANCE TASKS COMPLETED (${c7port})`), instance.id);
    })
    .catch(error => {
      console.error(error);

      if (instance.taskIterationLimit < iteration) {
        console.log(`Skipping ${instance.id} ...`);

        return;
      }

      console.log(`Retrying... (${instance.taskIterationLimit - iteration})`);

      return utils
        .delay(1000)
        .then(() => completeInstanceTasks(instance, iteration + 1));
    });
  }

  function getTaskList(instance) {
    return taskService.list({
      processInstanceId: instance.id,
      maxResults: 9999
    })
    .then(({_embedded: {task}}) => task);
  }

  function getVariables(instance) {
    return variableService.list({
      maxResults: 9999,
      processInstanceIdIn: [instance.id]
    }).then(({items: variables}) => {
      return variables.reduce((variables, variable) => {
        variables[variable.name] = variable.value;

        return variables;
      }, {});
    });
  }
}
