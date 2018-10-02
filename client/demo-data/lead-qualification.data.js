const {
  getResource,
  generateInstances,
  getEngineValue,
  getRandomValue,
  range,
  formatDate,
  generateStringValue
} = require('./helpers');

exports.definition = getResource('lead-qualification.bpmn');

exports.instances = generateInstances(1000, index => {
  return {
    IntegerVar: {
      value: index + 1,
      type: 'Integer'
    },
    DoubleVar: {
      value: 100 * Math.random(),
      type: 'Double'
    },
    StringVar: getEngineValue(generateStringValue()),
    ResearchLead: {
      value: Math.floor(10000 * Math.random()),
      type: 'Long'
    },
    GetMasterData: {
      value: Math.floor(20000 * Math.random()),
      type: 'Long'
    },
    AssignLeadAuto: {
      value: Math.floor(30000 * Math.random()),
      type: 'Long'
    },
    AssignLeadMan: {
      value: Math.floor(30000 * Math.random()),
      type: 'Long'
    },
    TriggerEvalProc: {
      value: Math.floor(40000 * Math.random()),
      type: 'Long'
    },
    DoBasicLeadQual: {
      value: Math.floor(50000 * Math.random()),
      type: 'Long'
    },
    NotifyAccountManager: {
      value: Math.floor(60000 * Math.random()),
      type: 'Long'
    },
    ScheduleDiscoveryCall: {
      value: Math.floor(70000 * Math.random()),
      type: 'Long'
    },
    ConductDiscoveryCall: {
      value: Math.floor(80000 * Math.random()),
      type: 'Long'
    },
    CreateUnqualifiedLead: {
      value: Math.floor(90000 * Math.random()),
      type: 'Long'
    },
    ReviewSuggestion: {
      value: Math.floor(100000 * Math.random()),
      type: 'Long'
    },
    CreateOpp: {
      value: Math.floor(110000 * Math.random()),
      type: 'Long'
    },
    CreateSQL: {
      value: Math.floor(120000 * Math.random()),
      type: 'Long'
    },
    DateVar: {
      type: 'Date',
      value: formatDate(
        new Date(new Date().getTime() - Math.floor(Math.random() * new Date().getTime()))
      )
    },
    qualified: getEngineValue(Math.random() < 0.1),
    sdrAvailable: getEngineValue(Math.round(Math.random() * 2) % 2 === 0),
    authority: getEngineValue(Math.round(Math.random() * 2) % 2 === 0),
    need: getEngineValue(Math.round(Math.random() * 2) % 2 === 0),
    landingPage: getRandomValue(['Download', 'notDownload']),
    crmLeadAppearance: getRandomValue(['oppOrSQL', 'new', 'nothing']),
    basicQualificationResult: getRandomValue(['callThem', 'doSomething']),
    responseResult: getRandomValue(['putIntoPipedrive', 'positive']),
    dcOutcome: getRandomValue(['backburner', 'something']),
    reviewDcOutcome: getRandomValue(['opp', 'sql', 'backburner']),
    timeline: getRandomValue(['linetime', 'timeline']),
    budget: getEngineValue(4000),
    amUser: getEngineValue('demo'),
    sdrUser: getEngineValue('sdrUser'),
    sdrAssigner: getRandomValue(range(1, 5).map(x => `s-${x}`))
  };
});
