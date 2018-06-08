const path = require('path');

module.exports = {
  components: 'src/modules/components/**/!(index|styled|service|api).js',
  require: [path.join(__dirname, 'src/index.css')]
};
