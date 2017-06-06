const path = require('path');
const fs = require('fs');

exports.getResource = (file, {relative = true, taskIterationLimit = 10} = {}) => {
  if (relative) {
    file = path.resolve(__dirname, file);
  }

  return {
    content: fs.readFileSync(
      file
    ),
    name: path.basename(file),
    taskIterationLimit
  };
};

exports.generateInstances = (resource, length, getProperties) => {
  const instances = [];

  for (let i = 0; i < length; i++) {
    instances.push(
      Object.assign(
        {resource},
        getProperties(i)
      )
    );
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
