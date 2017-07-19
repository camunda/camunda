const xmlJs = require('xml-js');
const path = require('path');
const fs = require('fs');

const pomFilePath = path.resolve(
  __dirname,
  '..',
  '..',
  'pom.xml'
);

const pom = xmlJs.xml2js(
  fs.readFileSync(pomFilePath),
  {
    compact: true
  }
);

module.exports = pom.project.version._text;
