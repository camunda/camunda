import {addModuleLoader} from 'dynamicLoader';

addModuleLoader('processDisplay', () => {
  return new Promise(resolve => {
    require.ensure(['./processDisplay', 'bpmn-js'], () => {
      const {ProcessDisplay, reducer} = require('./processDisplay');

      resolve({component: ProcessDisplay, reducer: reducer});
    });
  });
});
