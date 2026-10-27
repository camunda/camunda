/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// NOTE: ExportFilterHint is deliberately NOT re-exported from the 'components' barrel
// (modules/components/index.tsx). That barrel is heavily self-referential — dozens of its members
// import from 'components' — and adding this component there makes it resolve to `undefined` when
// imported through the barrel (verified: every other member resolves; this one does not, regardless
// of export order or re-export style). Consumers must import it directly from
// 'components/ExportFilterHint' instead. See PR camunda/camunda#55692 for the investigation.
export {default as ExportFilterHint} from './ExportFilterHint';
