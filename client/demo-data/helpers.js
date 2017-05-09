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
