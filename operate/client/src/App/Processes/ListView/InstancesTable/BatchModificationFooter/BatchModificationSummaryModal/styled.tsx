/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';
import {DataTable as BaseDataTable} from 'modules/components/DataTable';

const Title = styled.h4`
  ${styles.productiveHeading01};
  margin-top: var(--cds-spacing-08);
  margin-bottom: var(--cds-spacing-06);
`;

const DataTable = styled(BaseDataTable)`
  max-height: 185px;
`;

export {Title, DataTable};
