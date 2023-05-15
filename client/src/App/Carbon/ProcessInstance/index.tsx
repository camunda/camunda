/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {InstanceDetail} from '../Layout/InstanceDetail';

const ProcessInstance: React.FC = () => {
  return (
    <>
      <VisuallyHiddenH1>Operate Process Instance</VisuallyHiddenH1>
      <InstanceDetail
        header={<div>header</div>}
        topPanel={<div>top panel</div>}
        bottomPanel={<div>bottom panel</div>}
        id="process"
      />
    </>
  );
};

export {ProcessInstance};
