/* eslint no-console: 0 */

const shell = require('shelljs');
const path = require('path');
const fs = require('fs');
const CamundaClient = require('camunda-bpm-sdk-js').Client;
const chalk = require('chalk');
const utils = require('./utils');
let {c7port} = require('./config');

const maxConnections = 5;
// it can be configured, but it would pain in ass, so it is better
// to just remove it than configure it to be inside tmpDir
const demoDataDir = path.resolve(__dirname, '..', 'demo-data');


exports.deployEngineData = deployEngineData;

function deployEngineData() {
  const engineUrl = `http://localhost:${c7port}`;
  const camAPI = new CamundaClient({
    mock: false,
    apiUri: engineUrl + '/engine-rest'
  });
  const deploymentService = new camAPI.resource('deployment');
  const processDefinitionService = new camAPI.resource('process-definition');
  const taskService = new camAPI.resource('task');
  const variableService = new camAPI.resource('variable');

  return new Promise((resolve, reject) => {
    resolve();
  })
    .then(startServer)
    .then(deployDefinitions)
    .then(startInstances)
    .then(completeTasks)
    .catch(console.error);

  function startServer() {
    console.log('Wait for engine rest endpoint to be up!')
    return utils.waitForServer(engineUrl);
}

  function deployDefinitions() {
    console.log('Deploy definitions!')
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
    console.log('Start instances!')
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
    console.log('Complete tasks!')
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
