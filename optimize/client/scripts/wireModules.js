const path = require('path');
const fs = require('fs');

const srcDirectory = path.resolve(__dirname, '..', 'src');

if (!fs.existsSync(srcDirectory + '/node_modules')) {
  fs.symlinkSync(srcDirectory + '/modules', srcDirectory + '/node_modules', 'dir');
}
