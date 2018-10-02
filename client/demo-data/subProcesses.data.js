const {getResource, generateInstances} = require('./helpers');

exports.definition = getResource('subProcesses.bpmn');
exports.instances = generateInstances(10);
