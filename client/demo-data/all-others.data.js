const {getResource, generateInstances} = require('./helpers');

exports.resources = [
  getResource('lead-qualification-version1.bpmn'),
  getResource('sub-process.bpmn'),
];

exports.instances =  generateInstances('sub-process.bpmn', 10, (index) => {
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
    handleTask: handleTask
  };
});

// Return object with variables to complete task or undefined to do nothing
function handleTask() {
  return {};
}
