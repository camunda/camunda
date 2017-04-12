var xml2js = require('xml2js');
var fs = require('fs');
var path = require('path');
var ignoredActivities = ['$', 'bpmn:sequenceFlow', 'bpmn:incoming', 'bpmn:outgoing'];
var recursiveActivities = ['bpmn:subProcess'];
var utils =  require('./utils');

var readdir = utils.readdir;
var parseXmlString = utils.promisify(xml2js.parseString, xml2js);

module.exports = {
  getBpmnEntries: getBpmnEntries
}

function getBpmnEntries() {
  var dataPath = path.resolve(__dirname, '..', 'demo-data');

  return readdir(dataPath)
    .then(function(files) {
      var bpmnFiles = files
        .filter(function(file) {
          return file.slice(file.length - 5) === '.bpmn';
        })
        .map(function(file) {
          return fs.readFileSync(
            path.resolve(dataPath, file),
            'utf8'
          );
        });

      return Promise.all(
        bpmnFiles.map(describeBpmn)
      );
    });
}

function parseProcess(process) {
  return Object
    .keys(process)
    .filter(function(key) {
      return ignoredActivities.indexOf(key) === -1;
    })
    .reduce(function(activities, key) {
      var tasks = process[key];

      activities = activities.concat(
        tasks.map(function(task) {
          return task.$.id
        })
      );

      if(recursiveActivities.indexOf(key) !== -1) {
        tasks.forEach(function(task) {
          activities = activities.concat(parseProcess(task));
        });
      }

      return activities;
    }, []);
}

function describeBpmn(text) {
  return parseXmlString(text)
    .then(function(parsed) {
      var process = parsed['bpmn:definitions']['bpmn:process'][0];
      var processKey = process.$.id;

      var activities = parseProcess(process);

      return {
        xml: text.toString(),
        key: processKey,
        activities: activities
      };
    });
}
