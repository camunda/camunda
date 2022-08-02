/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {BaseSpinner} from './styled';

type Props = {
  'data-testid'?: string;
};

const Spinner: React.FC<Props> = (props) => {
  return <BaseSpinner {...props} />;
};

export {Spinner};
