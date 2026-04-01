/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect, APIRequestContext} from '@playwright/test';
import {assertStatusCode, assertInvalidArgument} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {evaluateExpression, EXPRESSION_URL} from '@requestHelpers';

type ExpressionTestCase = {
  description: string;
  expression: string;
  variables: Record<string, unknown>;
  result?: unknown;
  warnings?: string[];
  errorDetail?: string;
};

const successTestCases: ExpressionTestCase[] = [
  {
    description: 'Should evaluate literal number addition - Success',
    expression: '=1 + 2',
    variables: {},
    result: 3,
  },
  {
    description: 'Should evaluate literal string concatenation - Success',
    expression: '="foo" + "bar"',
    variables: {},
    result: 'foobar',
  },
  {
    description: 'Should evaluate boolean logic - Success',
    expression: '=true and false',
    variables: {},
    result: false,
  },
  {
    description: 'Should evaluate null literal - Success',
    expression: '=x',
    variables: {x: null},
    result: null,
  },
  {
    description: 'Should evaluate simple variable read - Success',
    expression: '=x',
    variables: {x: 5},
    result: 5,
  },
  {
    description: 'Should evaluate variable arithmetic - Success',
    expression: '=x + 1',
    variables: {x: 5},
    result: 6,
  },
  {
    description: 'Should evaluate string variable concatenation - Success',
    expression: '=firstName + " " + lastName',
    variables: {firstName: 'Jane', lastName: 'Doe'},
    result: 'Jane Doe',
  },
  {
    description: 'Should evaluate nested context comparison - Success',
    expression: '=person.age >= 18',
    variables: {person: {age: 18}},
    result: true,
  },
  {
    description: 'Should evaluate deep property access - Success',
    expression: '=person.address.city',
    variables: {person: {address: {city: 'Berlin'}}},
    result: 'Berlin',
  },
  {
    description: 'Should evaluate list sum - Success',
    expression: '=sum(numbers)',
    variables: {numbers: [1, 2, 3]},
    result: 6,
  },
  {
    description: 'Should evaluate list filter - Success',
    expression: '=numbers[ item > 2 ]',
    variables: {numbers: [1, 2, 3, 4]},
    result: [3, 4],
  },
  {
    description: 'Should evaluate quantifier expression - Success',
    expression: '=some n in numbers satisfies n > 3',
    variables: {numbers: [1, 2, 3, 4]},
    result: true,
  },
  {
    description: 'Should evaluate comparison and logic - Success',
    expression: '=x > 10 and y < 5',
    variables: {x: 11, y: 4},
    result: true,
  },
  {
    description: 'Should evaluate equality with null - Success',
    expression: '=x = null',
    variables: {x: null},
    result: true,
  },
  {
    description: 'Should evaluate if-then-else - Success',
    expression: '=if x > 5 then "big" else "small"',
    variables: {x: 10},
    result: 'big',
  },
  {
    description: 'Should evaluate date literal function comparison - Success',
    expression: '=date("2024-01-01") < date("2024-02-01")',
    variables: {},
    result: true,
  },
  {
    description: 'Should evaluate date variable comparison - Success',
    expression: '=date(dateVar) > date("2024-01-01")',
    variables: {dateVar: '2024-02-01'},
    result: true,
  },
  {
    description: 'Should evaluate string function - Success',
    expression: '=substring(text, 2, 3)',
    variables: {text: 'camunda'},
    result: 'amu',
  },
  {
    description: 'Should evaluate mixed types with context - Success',
    expression: '=person.age + bonus',
    variables: {person: {age: 30}, bonus: 5},
    result: 35,
  },
  {
    description: 'Should evaluate collection of contexts filter - Success',
    expression: '=employees[department = "QA"].name',
    variables: {
      employees: [
        {name: 'Alice', department: 'QA'},
        {name: 'Bob', department: 'Eng'},
      ],
    },
    result: ['Alice'],
  },
];

const warningTestCases: ExpressionTestCase[] = [
  {
    description: 'Should reject unknown variable - Warning',
    expression: '=x + 1',
    variables: {},
    warnings: ["No variable found with name 'x'", "Can't add '1' to 'null'"],
  },
  {
    description: 'Should reject type mismatch - Warning',
    expression: '=x + 1',
    variables: {x: 'foo'},
    warnings: ["Can't add '1' to '\"foo\"'"],
  },
  {
    description: 'Should reject wrong type for list operation - Warning',
    expression: '=sum(numbers)',
    variables: {numbers: ['a', 'b']},
    warnings: [
      "Failed to invoke function 'sum': expected number but found '\"a\"'",
    ],
  },
  {
    description: 'Should reject bad date literal - Warning',
    expression: '=date(meow)',
    variables: {meow: '2024-13-01'},
    warnings: [
      "Failed to invoke function 'date': Failed to parse date from '2024-13-01'",
    ],
  },
  {
    description: 'Should reject unexpected null in arithmetic - Warning',
    expression: '=x + 1',
    variables: {x: null},
    warnings: ["Can't add '1' to 'null'"],
  },
];

const errorTestCases: ExpressionTestCase[] = [
  {
    description:
      'Should return warning when invalid FEEL syntax (trailing operator) - Error',
    expression: '=x +',
    variables: {x: 1},
    errorDetail: 'Failed to parse expression',
  },
  {
    description:
      'Should return warning when invalid FEEL syntax (missing else) - Error',
    expression: '=if x > 5 then "big"',
    variables: {x: 10},
    errorDetail: 'Failed to parse expression',
  },
];

test.describe('Expression API Tests', () => {
  for (const tc of successTestCases) {
    test(tc.description, async ({request}) => {
      const response = await evaluateExpression(
        request,
        tc.expression,
        tc.variables,
      );
      await assertStatusCode(response, 200);
      await validateResponse(
        {path: EXPRESSION_URL, method: 'POST', status: '200'},
        response,
      );
      const body = await response.json();
      expect(body.expression).toBe(tc.expression);
      expect(body.result).toEqual(tc.result);
    });
  }

  for (const tc of warningTestCases) {
    test(tc.description, async ({request}) => {
      const response = await evaluateExpression(
        request,
        tc.expression,
        tc.variables,
      );
      await assertStatusCode(response, 200);
      await validateResponse(
        {path: EXPRESSION_URL, method: 'POST', status: '200'},
        response,
      );
      const body = await response.json();
      const warnings = [];
      for (const w of body.warnings) {
        warnings.push(w.message);
      }
      for (const expected of tc.warnings!) {
        expect(warnings).toContain(expected);
      }
      expect(body.result).toBeNull();
    });
  }

  for (const tc of errorTestCases) {
    test(tc.description, async ({request}) => {
      const response = await evaluateExpression(
        request,
        tc.expression,
        tc.variables,
      );
      await assertInvalidArgument(response, 400, tc.errorDetail!);
    });
  }
});
