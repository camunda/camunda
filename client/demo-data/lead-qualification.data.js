const {getResource, generateInstances, getEngineValue, getRandomValue, range} = require('./helpers');

const resource = 'lead-qualification.bpmn';

exports.resources = [
  getResource(resource)
];

const generateStringValue = () => {
  let str = '';
  const cons = ['B','C','D','F','G','H','J','K','L','M','N','P','Q','R','S','T','V','W','X','Z'];
  const voc = ['A', 'E', 'I', 'O', 'U', 'Y'];
  str+= Math.random() < 0.1 ? voc[Math.floor(Math.random() * 6)] : '';
  for (i = 0; i < 2 + Math.floor(Math.random() * 5); i++) {
    str+= cons[Math.floor(Math.random() * 20)] + voc[Math.floor(Math.random() * 6)]
    str+= Math.random() < 0.15 ? voc[Math.floor(Math.random() * 6)] : '';
    str+= Math.random() < 0.4 ? cons[Math.floor(Math.random() * 20)] : '';
  }
  return str;
}

exports.instances = generateInstances(resource, 1000, (index) => {
  return {
    variables: {
      Integer: {
        value: index + 1,
        type: 'Integer'
      },
      Double: {
        value: 100 * Math.random(),
        type: 'Double'
      },
      String: getEngineValue(generateStringValue()),
      UserTask_0w1r7lc: {
        value: Math.floor(10000 * Math.random()),
        type: 'Long'
      },
      ServiceTask_4: {
        value: Math.floor(20000 * Math.random()),
        type: 'Long'
      },
      CallActivity_0cw79oq: {
        value: Math.floor(30000 * Math.random()),
        type: 'Long'
      },
      ServiceTask_0j2w5af: {
        value: Math.floor(40000 * Math.random()),
        type: 'Long'
      },
      UserTask_1g1zsp8: {
        value: Math.floor(50000 * Math.random()),
        type: 'Long'
      },
      SendTask_01ry1oz: {
        value: Math.floor(60000 * Math.random()),
        type: 'Long'
      },
      CallActivity_1utbinl: {
        value: Math.floor(70000 * Math.random()),
        type: 'Long'
      },
      UserTask_0abh7j4: {
        value: Math.floor(80000 * Math.random()),
        type: 'Long'
      },
      ServiceTask_08dofa0: {
        value: Math.floor(90000 * Math.random()),
        type: 'Long'
      },
      UserTask_1d75hsy: {
        value: Math.floor(100000 * Math.random()),
        type: 'Long'
      },
      UserTask_1btv59s: {
        value: Math.floor(110000 * Math.random()),
        type: 'Long'
      },
      ServiceTask_2: {
        value: Math.floor(120000 * Math.random()),
        type: 'Long'
      },
      qualified: getEngineValue(Math.random() < 0.1),
      sdrAvailable: getEngineValue(Math.round(Math.random() * 2) % 2 === 0),
      authority: getEngineValue(Math.round(Math.random() * 2) % 2 === 0),
      need: getEngineValue(Math.round(Math.random() * 2) % 2 === 0),
      landingPage: getRandomValue(['Download', 'notDownload']),
      crmLeadAppearance: getRandomValue(['oppOrSQL','new','nothing']),
      basicQualificationResult: getRandomValue(['callThem', 'doSomething']),
      responseResult: getRandomValue(['putIntoPipedrive', 'positive']),
      dcOutcome: getRandomValue(['backburner', 'something']),
      reviewDcOutcome: getRandomValue(['opp', 'sql', 'backburner']),
      timeline: getRandomValue(['linetime', 'timeline']),
      budget: getEngineValue(4000),
      amUser: getEngineValue('demo'),
      sdrUser: getEngineValue('sdrUser'),
      sdrAssigner: getRandomValue(range(1, 5).map(x => `s-${x}`))
    },
    handleTask: handleTask
  };
});

// Return object with variables to complete task or undefined to do nothing
function handleTask() {
  return undefined;
}
