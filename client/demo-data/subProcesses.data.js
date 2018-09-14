const {getResource, generateInstances} = require('./helpers');

const resource = 'subProcesses.bpmn';

exports.resources = [getResource(resource)];

exports.instances = generateInstances(resource, 10, index => {
  return {
    variables: {},
    handleTask: handleTask
  };
});

// Return object with variables to complete task or undefined to do nothing
function handleTask() {
  return undefined;
}
