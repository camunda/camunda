'use strict';

var data = require('./data');
var elasticsearch = require('elasticsearch');
var client;
var NODE_ENV = process.env.NODE_ENV;

module.exports = {
  removeIndex: removeIndex,
  populateData: populateData
};

function initClient() {
  if (!client) {  
    client = new elasticsearch.Client({
      host: 'localhost:9200',
      // log: 'trace' // uncomment this for elastic search debug logs
    });
  }
}

function removeIndex() {
  initClient();

  return client.indices.delete({
    index: 'optimize'
  });
}

function populateData() {
  initClient();

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
