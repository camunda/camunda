'use strict';

var data = require('./data');
var elasticsearch = require('elasticsearch');
var client = new elasticsearch.Client({
  host: 'localhost:9200',
  // log: 'trace' // uncomment this for elastic search debug logs
});

module.exports = {
  removeIndex: removeIndex,
  populateData: populateData
};

function removeIndex() {
  return client.indices.delete({
    index: 'optimize'
  });
}

function populateData() {
  return data
    .getData()
    .then(function(demoData) {
      var bulkBody = Object
        .keys(demoData)
        .reduce(function(bulkBody, type) {
          var values = demoData[type];

          return values.reduce(function(bulkBody, value) {
            var operation = {
              index: {
                _index: 'optimize',
                _type: type
              }
            };

            if (value.id || value.activityInstanceId) {
              operation.index._id = value.id;
            }

            if (value.state && value.activityInstanceId) {
              operation.index._id = value.activityInstanceId + '_' + value.state;
            }

            return bulkBody.concat([
              operation,
              value
            ]);
          }, bulkBody);
        }, []);

      return client.bulk({
        body: bulkBody
      });
    });
}
