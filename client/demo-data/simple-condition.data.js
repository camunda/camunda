const {getResource, generateInstances, getEngineValue} = require('./helpers');

const resource = 'simple-condition.bpmn';

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
function handleTask(index, task) {
  if (task.taskDefinitionKey === 'Task1') {
    return {
      approved: getEngineValue(index % 2 === 0)
    };
  } else if (task.taskDefinitionKey === 'Task2' || index % 3 === 0) {
    return {
      someVar: getEngineValue('whatever')
    };
  }
}
