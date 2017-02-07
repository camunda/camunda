var xml2js = require('xml2js');
var fs = require('fs');

module.exports = {
  getBpmnEntries: getBpmnEntries
}

function getBpmnEntries() {
  var bpmnFiles =[
     fs.readFileSync(__dirname + '/diagram_1.bpmn', 'utf8'),
     fs.readFileSync(__dirname + '/diagram_2.bpmn', 'utf8'),
     fs.readFileSync(__dirname + '/version1.bpmn', 'utf8')
  ];

  return Promise.all(
    bpmnFiles.map(describeBpmn)
  );
}

function describeBpmn(text) {
  return parseXmlString(text)
    .then(function(parsed) {
      var process = parsed['bpmn:definitions']['bpmn:process'][0];
      var processKey = process.$.id;

      var activities = Object
        .keys(process)
        .filter(function(key) {
          return key !== '$';
        })
        .reduce(function(activities, key) {
          var tasks = process[key];

          return activities.concat(
            tasks.map(function(task) {
              return task.$.id
            })
          );
        }, []);

      return {
        xml: text.toString(),
        key: processKey,
        activities: activities
      };
    });
}

function parseXmlString(text) {
  return new Promise(function(resolve, reject) {
    xml2js.parseString(text, function(error, result) {
      if (error) {
        reject(error);
      }

      resolve(result);
    });
  });
}
