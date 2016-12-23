import {addModuleLoader} from 'dynamicLoader';

addModuleLoader('b', () => {
  return new Promise(resolve => {
    require.ensure(['./b', 'bpmn-js'], () => {
      const bModule = require('./b');

      resolve({component: bModule.BComponent, reducer: bModule.reducer});
    });
  });
});
