const path = require('path');
const fs = require('fs');

exports.getResource = file => {
  file = path.resolve(__dirname, file);

  return {
    content: fs.readFileSync(file),
    name: path.basename(file)
  };
};

exports.formatDate = date => {
  let month = date.getMonth() + 1;
  month = month < 10 ? '0' + month : '' + month;
  return `${date.getFullYear()}-${month}-${date.getDate()}T${date.getHours()}:${date.getMinutes()}:${date.getSeconds()}.${date.getMilliseconds()}+0200`;
};

exports.generateStringValue = () => {
  let str = '';
  const cons = [
    'B',
    'C',
    'D',
    'F',
    'G',
    'H',
    'J',
    'K',
    'L',
    'M',
    'N',
    'P',
    'Q',
    'R',
    'S',
    'T',
    'V',
    'W',
    'X',
    'Z'
  ];
  const voc = ['A', 'E', 'I', 'O', 'U', 'Y'];
  str += Math.random() < 0.1 ? voc[Math.floor(Math.random() * 6)] : '';
  for (let i = 0; i < 2 + Math.floor(Math.random() * 5); i++) {
    str += cons[Math.floor(Math.random() * 20)] + voc[Math.floor(Math.random() * 6)];
    str += Math.random() < 0.15 ? voc[Math.floor(Math.random() * 6)] : '';
    str += Math.random() < 0.4 ? cons[Math.floor(Math.random() * 20)] : '';
  }
  return str;
};

exports.generateInstances = (length, variables = {}) => {
  const instances = [];
  let getProperties;

  if (typeof variables === 'function') {
    getProperties = variables;
  } else {
    getProperties = () => variables;
  }

  for (let i = 0; i < length; i++) {
    instances.push(getProperties(i));
  }

  return instances;
};

exports.getEngineValue = (value, type) => {
  if (!type) {
    switch (typeof value) {
      case 'number':
        type = 'Double';
        break;
      case 'string':
        type = 'String';
        break;
      case 'boolean':
        type = 'Boolean';
        break;
    }
  }

  return {value, type};
};

exports.getRandomValue = (values, type) => {
  const index = Math.floor(Math.random() * values.length);

  return exports.getEngineValue(values[index], type);
};

exports.range = (start, end) => {
  const values = [];

  for (let i = start; i <= end; i++) {
    values.push(i);
  }

  return values;
};
