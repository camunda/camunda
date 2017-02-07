var elasticClient = require('./elasticsearch');
var chalk = require('chalk');

elasticClient
  .populateData()
  .then(function() {
    console.log(chalk.black.bgYellow('added demo data'));
  })
  .catch(function(error) {
    console.log(chalk.red('Could not add demo data'), error);
  });
