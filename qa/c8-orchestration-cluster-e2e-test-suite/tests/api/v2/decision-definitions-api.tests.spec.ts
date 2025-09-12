/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {
  buildUrl,
  jsonHeaders,
  assertRequiredFields,
  assertUnauthorizedRequest,
  assertPaginatedRequest,
  assertNotFoundRequest,
  assertEqualsForKeys,
  assertBadRequest,
  textXMLHeaders,
} from '../../../utils/http';
import {defaultAssertionOptions} from '../../../utils/constants';
import {
  decisionDefinitionRequiredFields,
  EVALUATE_DECISION_EXPECTED_BODY,
  EVALUATED_DECISION_EXPECTED_BODY,
  evaluateDecisionRequiredFields,
} from '../../../utils/beans/requestBeans';
import {
  assertDecisionDefinitionInResponse,
  DECISION_DEFINITION_RESPONSE_FROM_DEPLOYMENT,
  deployDecisionAndStoreResponse,
} from '../../../utils/requestHelpers';
import {DecisionDeployment} from '@camunda8/sdk/dist/c8/lib/C8Dto';
import fs from 'fs';

test.describe.parallel('Decision Definitions Search API Tests', () => {
  const state: Record<string, unknown> = {};
  let decisionDefinition1: DecisionDeployment;
  let decisionDefinition2: DecisionDeployment;
  let expectedBody1: Record<string, unknown>;
  let expectedBody2: Record<string, unknown>;

  test.beforeAll(async () => {
    await deployDecisionAndStoreResponse(
      state,
      '1',
      './resources/simpleDecisionTable1.dmn',
    );
    await deployDecisionAndStoreResponse(
      state,
      '2',
      './resources/simpleDecisionTable2.dmn',
    );
    decisionDefinition1 = state['decisionDefinition1'] as DecisionDeployment;
    decisionDefinition2 = state['decisionDefinition2'] as DecisionDeployment;
    expectedBody1 =
      DECISION_DEFINITION_RESPONSE_FROM_DEPLOYMENT(decisionDefinition1);
    expectedBody2 =
      DECISION_DEFINITION_RESPONSE_FROM_DEPLOYMENT(decisionDefinition2);
  });

  test('Search Decision Definitions', async ({request}) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );

      await assertPaginatedRequest(res, {
        itemLengthGreaterThan: 0,
        totalItemGreaterThan: 0,
      });
      const json = await res.json();
      assertDecisionDefinitionInResponse(
        json,
        expectedBody1,
        decisionDefinition1.decisionDefinitionKey as string,
      );
      assertDecisionDefinitionInResponse(
        json,
        expectedBody2,
        decisionDefinition2.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions by decisionDefinitionId', async ({
    request,
  }) => {
    const filter = {
      decisionDefinitionId: decisionDefinition1.decisionDefinitionId,
    };

    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {filter},
        },
      );

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertDecisionDefinitionInResponse(
        json,
        expectedBody1,
        decisionDefinition1.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions by name', async ({request}) => {
    const filter = {
      name: decisionDefinition1.name as string,
    };

    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {filter},
        },
      );

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertDecisionDefinitionInResponse(
        json,
        expectedBody1,
        decisionDefinition1.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions by version', async ({request}) => {
    const filter = {
      version: 1,
    };
    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {filter},
        },
      );

      await assertPaginatedRequest(res, {
        itemLengthGreaterThan: 1,
        totalItemGreaterThan: 1,
      });
      const json = await res.json();
      assertDecisionDefinitionInResponse(
        json,
        expectedBody1,
        decisionDefinition1.decisionDefinitionKey as string,
      );
      assertDecisionDefinitionInResponse(
        json,
        expectedBody2,
        decisionDefinition2.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions by decisionRequirementsId', async ({
    request,
  }) => {
    const filter = {
      decisionRequirementsId:
        decisionDefinition2.decisionRequirementsId as string,
    };

    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {filter},
        },
      );

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertDecisionDefinitionInResponse(
        json,
        expectedBody2,
        decisionDefinition2.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions by decisionDefinitionKey', async ({
    request,
  }) => {
    const filter = {
      decisionDefinitionKey:
        decisionDefinition2.decisionDefinitionKey as string,
    };

    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {filter},
        },
      );

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertDecisionDefinitionInResponse(
        json,
        expectedBody2,
        decisionDefinition2.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions by decisionRequirementsKey', async ({
    request,
  }) => {
    const filter = {
      decisionRequirementsKey:
        decisionDefinition1.decisionRequirementsKey as string,
    };

    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {filter},
        },
      );

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertDecisionDefinitionInResponse(
        json,
        expectedBody1,
        decisionDefinition1.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions by tenantId', async ({request}) => {
    const filter = {
      tenantId: '<default>',
    };
    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {filter},
        },
      );

      await assertPaginatedRequest(res, {
        itemLengthGreaterThan: 1,
        totalItemGreaterThan: 1,
      });
      const json = await res.json();
      assertDecisionDefinitionInResponse(
        json,
        expectedBody1,
        decisionDefinition1.decisionDefinitionKey as string,
      );
      assertDecisionDefinitionInResponse(
        json,
        expectedBody2,
        decisionDefinition2.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions by multiple filters', async ({request}) => {
    const filter = {
      decisionRequirementsId:
        decisionDefinition2.decisionRequirementsId as string,
      decisionDefinitionId: decisionDefinition2.decisionDefinitionId as string,
      name: decisionDefinition2.name as string,
      version: decisionDefinition2.version as number,
    };

    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/search', {}),
        {
          headers: jsonHeaders(),
          data: {filter},
        },
      );

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      assertDecisionDefinitionInResponse(
        json,
        expectedBody2,
        decisionDefinition2.decisionDefinitionKey as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Decision Definitions - No Matching Item', async ({request}) => {
    const filter = {decisionDefinitionId: 'non-existent-id'};

    const res = await request.post(
      buildUrl('/decision-definitions/search', {}),
      {
        headers: jsonHeaders(),
        data: {filter},
      },
    );
    await assertPaginatedRequest(res, {
      itemsLengthEqualTo: 0,
      totalItemsEqualTo: 0,
    });
  });

  test('Search Decision Definitions Unauthorized', async ({request}) => {
    const res = await request.post(
      buildUrl('/decision-definitions/search', {}),
      {
        headers: {},
        data: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Get Decision Definition', async ({request}) => {
    await expect(async () => {
      const res = await request.get(
        buildUrl('/decision-definitions/{decisionDefinitionKey}', {
          decisionDefinitionKey:
            decisionDefinition1.decisionDefinitionKey as string,
        }),
        {headers: jsonHeaders()},
      );

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, decisionDefinitionRequiredFields);
      assertEqualsForKeys(
        json,
        expectedBody1,
        decisionDefinitionRequiredFields,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Get Decision Definition Not Found', async ({request}) => {
    const nonExistentKey = '11111';
    const res = await request.get(
      buildUrl('/decision-definitions/{decisionDefinitionKey}', {
        decisionDefinitionKey: nonExistentKey,
      }),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Decision Definition with key '${nonExistentKey}' not found`,
    );
  });

  test('Get Decision Definition Bad Request', async ({request}) => {
    const invalidKey = 'abc';
    const res = await request.get(
      buildUrl('/decision-definitions/{decisionDefinitionKey}', {
        decisionDefinitionKey: invalidKey,
      }),
      {headers: jsonHeaders()},
    );
    await assertBadRequest(res, `Failed to convert 'decisionDefinitionKey'`);
  });

  test('Get Decision Definition Unauthorized', async ({request}) => {
    const res = await request.get(
      buildUrl('/decision-definitions/{decisionDefinitionKey}', {
        decisionDefinitionKey:
          decisionDefinition1.decisionDefinitionKey as string,
      }),
      {headers: {}},
    );
    await assertUnauthorizedRequest(res);
  });

  test('Get Decision Definition XML', async ({request}) => {
    await expect(async () => {
      const res = await request.get(
        buildUrl('/decision-definitions/{decisionDefinitionKey}/xml', {
          decisionDefinitionKey:
            decisionDefinition1.decisionDefinitionKey as string,
        }),
        {headers: textXMLHeaders()},
      );

      expect(res.status()).toBe(200);
      expect(await res.text()).toEqual(
        fs.readFileSync('./resources/simpleDecisionTable1.dmn', 'utf-8'),
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Get Decision Definition XML Not Found', async ({request}) => {
    const nonExistentKey = '11111';
    const res = await request.get(
      buildUrl('/decision-definitions/{decisionDefinitionKey}/xml', {
        decisionDefinitionKey: nonExistentKey,
      }),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Decision Definition with key '${nonExistentKey}' not found`,
    );
  });

  test('Get Decision Definition XML Bad Request', async ({request}) => {
    const invalidKey = 'abc';
    const res = await request.get(
      buildUrl('/decision-definitions/{decisionDefinitionKey}/xml', {
        decisionDefinitionKey: invalidKey,
      }),
      {headers: jsonHeaders()},
    );
    await assertBadRequest(res, `Failed to convert 'decisionDefinitionKey'`);
  });

  test('Get Decision Definition XML Unauthorized', async ({request}) => {
    const res = await request.get(
      buildUrl('/decision-definitions/{decisionDefinitionKey}/xml', {
        decisionDefinitionKey:
          decisionDefinition1.decisionDefinitionKey as string,
      }),
      {headers: {}},
    );
    await assertUnauthorizedRequest(res);
  });

  test('Evaluate Decision Definition by decisionDefinitionKey For Input 8.8', async ({
    request,
  }) => {
    const matchedRule = {
      output: '"8.8-generation"',
      ruleId: 'DecisionRule_1d9up3i',
      outputId: 'Output_1',
      outputName: 'baseGenerationId',
      outputValue: '"8.8-generation"',
      input: [
        {
          inputId: 'Input_1',
          inputName: 'trainName',
          inputValue: '"8.8"',
        },
      ],
      ruleIndex: 5,
    };
    const expectedBody = EVALUATE_DECISION_EXPECTED_BODY(
      decisionDefinition2,
      matchedRule.output,
    );
    const evaluatedDecisionExpectedBody = EVALUATED_DECISION_EXPECTED_BODY(
      decisionDefinition2,
      matchedRule,
    );
    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/evaluation'),
        {
          headers: jsonHeaders(),
          data: {
            decisionDefinitionKey:
              decisionDefinition2.decisionDefinitionKey as string,
            variables: {
              trainName: '8.8',
            },
          },
        },
      );

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, evaluateDecisionRequiredFields);
      assertEqualsForKeys(json, expectedBody, Object.keys(expectedBody));
      expect(json['evaluatedDecisions'].length).toBe(1);
      const evaluatedDecisionActualBody = json['evaluatedDecisions'][0];
      assertEqualsForKeys(
        evaluatedDecisionActualBody,
        evaluatedDecisionExpectedBody,
        Object.keys(evaluatedDecisionExpectedBody),
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Evaluate Decision Definition by decisionDefinitionKey For Input 8.7', async ({
    request,
  }) => {
    const matchedRule = {
      output: '"8.7-generation"',
      ruleId: 'DecisionRule_0k6wq2l',
      outputId: 'Output_1',
      outputName: 'baseGenerationId',
      outputValue: '"8.7-generation"',
      input: [
        {inputId: 'Input_1', inputName: 'trainName', inputValue: '"8.7"'},
      ],
      ruleIndex: 4,
    };
    const expectedBody = EVALUATE_DECISION_EXPECTED_BODY(
      decisionDefinition2,
      matchedRule.output,
    );
    const evaluatedDecisionExpectedBody = EVALUATED_DECISION_EXPECTED_BODY(
      decisionDefinition2,
      matchedRule,
    );
    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/evaluation'),
        {
          headers: jsonHeaders(),
          data: {
            decisionDefinitionKey:
              decisionDefinition2.decisionDefinitionKey as string,
            variables: {
              trainName: '8.7',
            },
          },
        },
      );

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, evaluateDecisionRequiredFields);
      assertEqualsForKeys(json, expectedBody, Object.keys(expectedBody));
      expect(json['evaluatedDecisions'].length).toBe(1);
      const evaluatedDecisionActualBody = json['evaluatedDecisions'][0];
      assertEqualsForKeys(
        evaluatedDecisionActualBody,
        evaluatedDecisionExpectedBody,
        Object.keys(evaluatedDecisionExpectedBody),
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Evaluate Decision Definition by decisionDefinitionKey For Input 8.6', async ({
    request,
  }) => {
    const matchedRule = {
      output: '"8.6-generation"',
      ruleId: 'DecisionRule_0z5aeou',
      outputId: 'Output_1',
      outputName: 'baseGenerationId',
      outputValue: '"8.6-generation"',
      input: [
        {inputId: 'Input_1', inputName: 'trainName', inputValue: '"8.6"'},
      ],
      ruleIndex: 3,
    };
    const expectedBody = EVALUATE_DECISION_EXPECTED_BODY(
      decisionDefinition2,
      matchedRule.output,
    );
    const evaluatedDecisionExpectedBody = EVALUATED_DECISION_EXPECTED_BODY(
      decisionDefinition2,
      matchedRule,
    );
    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/evaluation'),
        {
          headers: jsonHeaders(),
          data: {
            decisionDefinitionKey:
              decisionDefinition2.decisionDefinitionKey as string,
            variables: {
              trainName: '8.6',
            },
          },
        },
      );

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, evaluateDecisionRequiredFields);
      assertEqualsForKeys(json, expectedBody, Object.keys(expectedBody));
      expect(json['evaluatedDecisions'].length).toBe(1);
      const evaluatedDecisionActualBody = json['evaluatedDecisions'][0];
      assertEqualsForKeys(
        evaluatedDecisionActualBody,
        evaluatedDecisionExpectedBody,
        Object.keys(evaluatedDecisionExpectedBody),
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Evaluate Decision Definition by decisionDefinitionKey For Non Existent Input', async ({
    request,
  }) => {
    const nonExistentInput = '8.11111';
    const matchedRule = {
      output: 'null',
      input: [
        {
          inputId: 'Input_1',
          inputName: 'trainName',
          inputValue: `"${nonExistentInput}"`,
        },
      ],
      ruleIndex: 3,
    };
    const expectedBody = EVALUATE_DECISION_EXPECTED_BODY(
      decisionDefinition2,
      'null',
    );
    const evaluatedDecisionExpectedBody = EVALUATED_DECISION_EXPECTED_BODY(
      decisionDefinition2,
      matchedRule,
      true,
    );
    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/evaluation'),
        {
          headers: jsonHeaders(),
          data: {
            decisionDefinitionKey:
              decisionDefinition2.decisionDefinitionKey as string,
            variables: {
              trainName: nonExistentInput,
            },
          },
        },
      );

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, evaluateDecisionRequiredFields);
      assertEqualsForKeys(json, expectedBody, Object.keys(expectedBody));
      expect(json['evaluatedDecisions'].length).toBe(1);
      const evaluatedDecisionActualBody = json['evaluatedDecisions'][0];
      assertEqualsForKeys(
        evaluatedDecisionActualBody,
        evaluatedDecisionExpectedBody,
        Object.keys(evaluatedDecisionExpectedBody),
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Evaluate Decision Definition by decisionDefinitionId For Input With status VIP and Score 50', async ({
    request,
  }) => {
    const matchedRule = {
      output: 'true',
      ruleId: 'DecisionRule_0zglss8',
      outputId: 'Output_1',
      outputName: 'Is Eligible for Upgrade',
      outputValue: 'true',
      ruleIndex: 1,
      input: [
        {
          inputId: 'InputClause_1bnwsvl',
          inputName: 'User Status',
          inputValue: '"VIP"',
        },
        {
          inputId: 'InputClause_0adc4dq',
          inputName: 'Engagement Score',
          inputValue: '50',
        },
        {
          inputId: 'Input_1',
          inputName: 'Is User under 18',
          inputValue: 'null',
        },
        {
          inputId: 'InputClause_0nktm14',
          inputName: 'Student',
          inputValue: 'null',
        },
      ],
    };
    const expectedBody = EVALUATE_DECISION_EXPECTED_BODY(
      decisionDefinition1,
      matchedRule.output,
    );
    const evaluatedDecisionExpectedBody = EVALUATED_DECISION_EXPECTED_BODY(
      decisionDefinition1,
      matchedRule,
    );
    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/evaluation'),
        {
          headers: jsonHeaders(),
          data: {
            decisionDefinitionId:
              decisionDefinition1.decisionDefinitionId as string,
            variables: {
              userStatus: 'VIP',
              CalculateEngagementScore: 50,
            },
          },
        },
      );

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, evaluateDecisionRequiredFields);
      assertEqualsForKeys(json, expectedBody, Object.keys(expectedBody));
      expect(json['evaluatedDecisions'].length).toBe(1);
      const evaluatedDecisionActualBody = json['evaluatedDecisions'][0];
      assertEqualsForKeys(
        evaluatedDecisionActualBody,
        evaluatedDecisionExpectedBody,
        Object.keys(evaluatedDecisionExpectedBody),
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Evaluate Decision Definition by decisionDefinitionId For Input With status Regular and Score 40', async ({
    request,
  }) => {
    const matchedRule = {
      output: 'false',
      ruleId: 'DecisionRule_0b2bzdh',
      outputId: 'Output_1',
      outputName: 'Is Eligible for Upgrade',
      outputValue: 'false',
      ruleIndex: 2,
      input: [
        {
          inputId: 'InputClause_1bnwsvl',
          inputName: 'User Status',
          inputValue: '"Regular"',
        },
        {
          inputId: 'InputClause_0adc4dq',
          inputName: 'Engagement Score',
          inputValue: '40',
        },
        {
          inputId: 'Input_1',
          inputName: 'Is User under 18',
          inputValue: 'null',
        },
        {
          inputId: 'InputClause_0nktm14',
          inputName: 'Student',
          inputValue: 'null',
        },
      ],
    };
    const expectedBody = EVALUATE_DECISION_EXPECTED_BODY(
      decisionDefinition1,
      matchedRule.output,
    );
    const evaluatedDecisionExpectedBody = EVALUATED_DECISION_EXPECTED_BODY(
      decisionDefinition1,
      matchedRule,
    );
    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/evaluation'),
        {
          headers: jsonHeaders(),
          data: {
            decisionDefinitionId:
              decisionDefinition1.decisionDefinitionId as string,
            variables: {
              userStatus: 'Regular',
              CalculateEngagementScore: 40,
            },
          },
        },
      );

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, evaluateDecisionRequiredFields);
      assertEqualsForKeys(json, expectedBody, Object.keys(expectedBody));
      expect(json['evaluatedDecisions'].length).toBe(1);
      const evaluatedDecisionActualBody = json['evaluatedDecisions'][0];
      assertEqualsForKeys(
        evaluatedDecisionActualBody,
        evaluatedDecisionExpectedBody,
        Object.keys(evaluatedDecisionExpectedBody),
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Evaluate Decision Definition by decisionDefinitionId For Non Existent Input', async ({
    request,
  }) => {
    const nonExistentInput = '8.11111';
    const matchedRule = {
      output: 'null',
      input: [
        {
          inputId: 'Input_1',
          inputName: 'trainName',
          inputValue: `"${nonExistentInput}"`,
        },
      ],
      ruleIndex: 3,
    };
    const expectedBody = EVALUATE_DECISION_EXPECTED_BODY(
      decisionDefinition2,
      'null',
    );
    const evaluatedDecisionExpectedBody = EVALUATED_DECISION_EXPECTED_BODY(
      decisionDefinition2,
      matchedRule,
      true,
    );
    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/evaluation'),
        {
          headers: jsonHeaders(),
          data: {
            decisionDefinitionId:
              decisionDefinition2.decisionDefinitionId as string,
            variables: {
              trainName: nonExistentInput,
            },
          },
        },
      );

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, evaluateDecisionRequiredFields);
      assertEqualsForKeys(json, expectedBody, Object.keys(expectedBody));
      expect(json['evaluatedDecisions'].length).toBe(1);
      const evaluatedDecisionActualBody = json['evaluatedDecisions'][0];
      assertEqualsForKeys(
        evaluatedDecisionActualBody,
        evaluatedDecisionExpectedBody,
        Object.keys(evaluatedDecisionExpectedBody),
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Evaluate Decision Definition Invalid Data', async ({request}) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl('/decision-definitions/evaluation'),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );

      await assertBadRequest(
        res,
        'At least one of [decisionDefinitionId, decisionDefinitionKey] is required.',
        'INVALID_ARGUMENT',
      );
    }).toPass(defaultAssertionOptions);
  });
});
