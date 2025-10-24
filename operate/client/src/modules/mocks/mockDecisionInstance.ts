/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {GetDecisionInstanceResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';

const invoiceClassification: GetDecisionInstanceResponseBody = {
  decisionEvaluationInstanceKey: '3ß945876576324-1',
  decisionEvaluationKey: '3ß945876576324',
  tenantId: '<default>',
  decisionDefinitionKey: '111',
  decisionDefinitionId: 'invoiceClassification',
  state: 'EVALUATED',
  decisionDefinitionName: 'Invoice Classification',
  decisionDefinitionVersion: 1,
  evaluationDate: '2022-01-20T13:26:52.531+0000',
  processInstanceKey: '666',
  processDefinitionKey: '666',
  elementInstanceKey: '37423847',
  evaluationFailure: '',
  rootDecisionDefinitionKey: '111',
  evaluatedInputs: [
    {inputId: '0', inputName: 'Age', inputValue: '16'},
    {inputId: '1', inputName: 'Stateless Person', inputValue: 'false'},
    {inputId: '2', inputName: 'Parent Norwegian', inputValue: '"missing data"'},
    {inputId: '3', inputName: 'Previously Norwegian', inputValue: 'true'},
    {inputId: '4503599627370696-bz', inputName: 'bz', inputValue: '"value_bz"'},
    {inputId: '4503599627370696-ca', inputName: 'ca', inputValue: '"value_ca"'},
    {inputId: '4503599627370696-cb', inputName: 'cb', inputValue: '"value_cb"'},
    {inputId: '4503599627370696-cc', inputName: 'cc', inputValue: '"value_cc"'},
    {inputId: '4503599627370696-cd', inputName: 'cd', inputValue: '"value_cd"'},
    {inputId: '4503599627370696-ce', inputName: 'ce', inputValue: '"value_ce"'},
    {inputId: '4503599627370696-cf', inputName: 'cf', inputValue: '"value_cf"'},
    {inputId: '4503599627370696-cg', inputName: 'cg', inputValue: '"value_cg"'},
    {inputId: '4503599627370696-ch', inputName: 'ch', inputValue: '"value_ch"'},
    {inputId: '4503599627370696-ci', inputName: 'ci', inputValue: '"value_ci"'},
    {inputId: '4503599627370696-cj', inputName: 'cj', inputValue: '"value_cj"'},
    {inputId: '4503599627370696-ck', inputName: 'ck', inputValue: '"value_ck"'},
    {inputId: '4503599627370696-cl', inputName: 'cl', inputValue: '"value_cl"'},
    {inputId: '4503599627370696-cm', inputName: 'cm', inputValue: '"value_cm"'},
    {inputId: '4503599627370696-cn', inputName: 'cn', inputValue: '"value_cn"'},
    {inputId: '4503599627370696-co', inputName: 'co', inputValue: '"value_co"'},
    {inputId: '4503599627370696-cp', inputName: 'cp', inputValue: '"value_cp"'},
    {inputId: '4503599627370696-cq', inputName: 'cq', inputValue: '"value_cq"'},
    {inputId: '4503599627370696-cr', inputName: 'cr', inputValue: '"value_cr"'},
    {inputId: '4503599627370696-cs', inputName: 'cs', inputValue: '"value_cs"'},
    {inputId: '4503599627370696-ct', inputName: 'ct', inputValue: '"value_ct"'},
    {inputId: '4503599627370696-cu', inputName: 'cu', inputValue: '"value_cu"'},
    {inputId: '4503599627370696-cv', inputName: 'cv', inputValue: '"value_cv"'},
    {inputId: '4503599627370696-cw', inputName: 'cw', inputValue: '"value_cw"'},
    {inputId: '4503599627370696-cx', inputName: 'cx', inputValue: '"value_cx"'},
    {inputId: '4503599627370696-cy', inputName: 'cy', inputValue: '"value_cy"'},
    {inputId: '4503599627370696-cz', inputName: 'cz', inputValue: '"value_cz"'},
    {inputId: '4503599627370696-da', inputName: 'da', inputValue: '"value_da"'},
    {inputId: '4503599627370696-db', inputName: 'db', inputValue: '"value_db"'},
    {inputId: '4503599627370696-dc', inputName: 'dc', inputValue: '"value_dc"'},
    {inputId: '4503599627370696-dd', inputName: 'dd', inputValue: '"value_dd"'},
    {inputId: '4503599627370696-de', inputName: 'de', inputValue: '"value_de"'},
    {inputId: '4503599627370696-df', inputName: 'df', inputValue: '"value_df"'},
    {inputId: '4503599627370696-dg', inputName: 'dg', inputValue: '"value_dg"'},
    {inputId: '4503599627370696-dh', inputName: 'dh', inputValue: '"value_dh"'},
    {inputId: '4503599627370696-di', inputName: 'di', inputValue: '"value_di"'},
    {inputId: '4503599627370696-dj', inputName: 'dj', inputValue: '"value_dj"'},
    {inputId: '4503599627370696-dk', inputName: 'dk', inputValue: '"value_dk"'},
    {inputId: '4503599627370696-dl', inputName: 'dl', inputValue: '"value_dl"'},
    {inputId: '4503599627370696-dm', inputName: 'dm', inputValue: '"value_dm"'},
    {inputId: '4503599627370696-dn', inputName: 'dn', inputValue: '"value_dn"'},
    {inputId: '4503599627370696-do', inputName: 'do', inputValue: '"value_do"'},
    {inputId: '4503599627370696-dp', inputName: 'dp', inputValue: '"value_dp"'},
    {inputId: '4503599627370696-dq', inputName: 'dq', inputValue: '"value_dq"'},
    {inputId: '4503599627370696-dr', inputName: 'dr', inputValue: '"value_dr"'},
    {inputId: '4503599627370696-ds', inputName: 'ds', inputValue: '"value_ds"'},
    {inputId: '4503599627370696-dt', inputName: 'dt', inputValue: '"value_dt"'},
    {inputId: '4503599627370696-du', inputName: 'du', inputValue: '"value_du"'},
    {inputId: '4503599627370696-dv', inputName: 'dv', inputValue: '"value_dv"'},
  ],
  matchedRules: [
    {
      ruleId: 'row-49839158-1',
      ruleIndex: 1,
      evaluatedOutputs: [
        {
          outputId: '0',
          outputName: 'Age requirements satisfied',
          outputValue: '"missing data"',
        },
      ],
    },
    {
      ruleId: 'row-49839158-2',
      ruleIndex: 4,
      evaluatedOutputs: [
        {
          outputId: '1',
          outputName: 'paragraph',
          outputValue: '"sbl §17"',
        },
      ],
    },
  ],
  decisionDefinitionType: 'DECISION_TABLE',
  result: JSON.stringify({
    areAgeRequirementsSatisfied: 'satisfied',
    paragraph: 'sbl §17',
  }),
};

const assignApproverGroup: GetDecisionInstanceResponseBody = {
  decisionEvaluationInstanceKey: '29283472932831-1',
  decisionEvaluationKey: '29283472932831',
  tenantId: '<default>',
  decisionDefinitionKey: '111',
  decisionDefinitionId: 'invoice-assign-approver',
  state: 'FAILED',
  decisionDefinitionName: 'Assign Approver Group',
  decisionDefinitionVersion: 1,
  evaluationDate: '2022-01-20T13:26:52.531+0000',
  processInstanceKey: '777',
  processDefinitionKey: '777',
  elementInstanceKey: '2347238947239',
  evaluationFailure: 'An error occurred',
  rootDecisionDefinitionKey: '111',
  evaluatedInputs: [
    {
      inputId: '0',
      inputName: 'Age',
      inputValue: '21',
    },
  ],
  matchedRules: [
    {
      ruleId: 'row-49839158-1',
      ruleIndex: 1,
      evaluatedOutputs: [
        {
          outputId: '0',
          outputName: 'paragraph',
          outputValue: '"sbl §382"',
        },
      ],
    },
  ],
  decisionDefinitionType: 'DECISION_TABLE',
  result: '',
};

const assignApproverGroupWithoutVariables: GetDecisionInstanceResponseBody = {
  ...assignApproverGroup,
  evaluatedInputs: [],
  matchedRules: [],
};

const literalExpression: GetDecisionInstanceResponseBody = {
  decisionEvaluationInstanceKey: '247986278462738-1',
  decisionEvaluationKey: '247986278462738',
  tenantId: '<default>',
  decisionDefinitionKey: '111',
  decisionDefinitionId: 'calc-key-figures',
  state: 'EVALUATED',
  decisionDefinitionName: 'Calculate Credit History Key Figures',
  decisionDefinitionVersion: 1,
  evaluationDate: '2022-01-20T13:26:52.531+0000',
  processInstanceKey: '42',
  processDefinitionKey: '42',
  elementInstanceKey: '623426348231',
  evaluationFailure: '',
  rootDecisionDefinitionKey: '111',
  evaluatedInputs: [],
  matchedRules: [],
  decisionDefinitionType: 'LITERAL_EXPRESSION',
  result: '',
};

export {
  invoiceClassification,
  assignApproverGroup,
  assignApproverGroupWithoutVariables,
  literalExpression,
};
