const {getResource, generateInstances} = require('./helpers');

const resource = 'obtain-schufa.bpmn';

exports.resources = [
  getResource(resource)
];

exports.instances = generateInstances(resource, 10, (index) => {
  return {
    variables: {
      var1: {
        value: index + 1,
        type: 'Integer'
      },
      var2: {
        value: Math.random(),
        type: 'Double'
      }
    },
    handleTask
  };
});

// Return object with variables to complete task or undefined to do nothing
function handleTask(task) {
  if (task.taskDefinitionKey === 'UserTask_1') {
    return {
      var3: {
        type: 'String',
        value: 'some variable ' + Math.random()
      }
    };
  }
};
