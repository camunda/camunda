const {getResource, generateInstances, getEngineValue} = require('./helpers');

const resource = 'lead-qualification.bpmn';

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
    handleTask: handleTask
  };
});

// Return object with variables to complete task or undefined to do nothing
function handleTask() {
  return {
    qualified: getEngineValue(Math.round(Math.random() * 2) % 2 === 0),
    sdrAvailable: getEngineValue(Math.round(Math.random() * 2) % 2 === 0),
    landingPage: getEngineValue('Download'),
    crmLeadAppearance: getEngineValue('new'),
    basicQualificationResult: getEngineValue('done'),
    responseResult: getEngineValue('positive'),
    dcOutcome: getEngineValue('dsds'),
    reviewDcOutcome: getEngineValue('opp'),
    authority: getEngineValue(true),
    need: getEngineValue(true),
    timeline: getEngineValue('timeline'),
    budget: getEngineValue(4000),
    amUser: getEngineValue('demo'),
    sdrUser: getEngineValue('sdrUser'),
    sdrAssigner: getEngineValue('sdrAssigner}')
  };
}
