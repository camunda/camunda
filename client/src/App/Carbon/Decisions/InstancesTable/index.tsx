/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {PanelHeader} from 'modules/components/Carbon/PanelHeader';

const InstancesTable: React.FC = () => {
  return (
    <section>
      <PanelHeader title="Decision Instances" count={123} hasTopBorder />
      <div>instances table</div>
    </section>
  );
};

export {InstancesTable};
