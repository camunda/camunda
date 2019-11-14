import config from '../config';
import { login } from '../utils';

import * as Header from './Header.elements.js';
import * as Dashboard from './Dashboard.elements.js';
import * as Instances from './Instances.Elements.js';

fixture('Dashboard')
  .page(config.endpoint)

test('Instances Statistics', async t => {  
  await login(t);

  await t    
    .click(Dashboard.activeInstancesLink)
    .expect(Instances.filtersRunningActiveChk.checked).ok()
    .expect(Instances.filtersRunningIncidentsChk.checked).notOk();   
    
  await t
    .click(Header.dashboardLink);

  await t    
    .click(Dashboard.incidentInstancesLink)
    .expect(Instances.filtersRunningActiveChk.checked).notOk()
    .expect(Instances.filtersRunningIncidentsChk.checked).ok();        
});