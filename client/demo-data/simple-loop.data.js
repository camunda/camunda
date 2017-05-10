const {getResource, generateInstances, getEngineValue} = require('./helpers');

const resource = 'simple-loop.bpmn';

exports.resources = [
  getResource(resource)
];

exports.instances = generateInstances(resource, 40, (index) => {
  return {
    variables: {
      var1: getEngineValue(index + 1),
      var2: getEngineValue(Math.random())
    },
    handleTask: handleTask.bind(null, index)
  };
});

// Return object with variables to complete task or undefined to do nothing
function handleTask(index, task, variables) {
  if (task.taskDefinitionKey === 'Task1') {
    return {
      approved: getEngineValue(index % 2 === 0)
    };
  }

  return {
    someVar: getEngineValue('whatever'),
    var1: getEngineValue(variables.var1 + 100)
  };
}
