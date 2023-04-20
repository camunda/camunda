/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';
import {PanelHeader} from 'modules/components/Carbon/PanelHeader';

const Decision: React.FC = observer(() => {
  return (
    <section>
      <PanelHeader title="Decision" />
      <div>decisions - diagram</div>
    </section>
  );
});

export {Decision};
