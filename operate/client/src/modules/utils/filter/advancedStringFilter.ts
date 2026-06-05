/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import type {QueryProcessInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {parseIds} from './index';

const VALUE_SEPARATOR = '_';
const FILTER_SEPARATOR = '___';

// `AdvancedStringFilter` type derived because it is not exposed by @camunda/camunda-api-zod-schemas.
type AdvancedStringFilter = Extract<
  NonNullable<QueryProcessInstancesRequestBody['filter']>['businessId'],
  object
>;
type AdvancedStringFilterOperator = keyof AdvancedStringFilter;

const advancedStringFilterSchema = z.object({
  $eq: z.string().optional(),
  $neq: z.string().optional(),
  $exists: z.stringbool().optional(),
  $in: z.array(z.string().min(1)).min(1).optional(),
  $notIn: z.array(z.string().min(1)).min(1).optional(),
  $like: z.string().optional(),
}) satisfies z.ZodType<AdvancedStringFilter>;

/** Turns an operator value pair into a URL-ready string. */
function encodeFilterOperation(
  operator: AdvancedStringFilterOperator,
  rawValue: string,
) {
  return `${operator.slice(1)}${VALUE_SEPARATOR}${rawValue}`;
}

/** Splits a URL-ready string into an operator value pair and verifies the operator is valid. */
function splitEncodedFilterOperation(encodedOperation: string) {
  const separatorIndex = encodedOperation.indexOf(VALUE_SEPARATOR);
  if (separatorIndex === -1) {
    return null;
  }

  const operator = `$${encodedOperation.slice(0, separatorIndex)}`;
  const value = encodedOperation.slice(separatorIndex + 1);
  const result = z.keyof(advancedStringFilterSchema).safeParse(operator);
  if (!result.success) {
    return null;
  }
  return {operator: result.data, value};
}

/**
 * Bidirectional codec for URL-ready-encoded filters that are optimized for URL
 * readability. The encoded format is `"<slug>_<value>___<slug>_<value>..."`
 * where slug is a filter without the `$`. Decoding applies per-operator transforms
 * (wildcard wrapping for `$like`, splitting for `$in`/`$notIn`, etc.).
 */
const advancedStringFilterCodec = z.codec(
  z.string(),
  advancedStringFilterSchema,
  {
    decode: (filterString, ctx) => {
      const filter: z.input<typeof advancedStringFilterSchema> = {};
      for (const raw of filterString.split(FILTER_SEPARATOR)) {
        const splitOperation = splitEncodedFilterOperation(raw);
        if (splitOperation === null) {
          ctx.issues.push({
            code: 'custom',
            message: `Missing "${VALUE_SEPARATOR}" separator in advanced filter value`,
            input: raw,
          });
          return z.NEVER;
        }

        const {operator, value} = splitOperation;
        switch (operator) {
          case '$eq':
          case '$neq':
          case '$exists': {
            filter[operator] = value;
            continue;
          }
          case '$like': {
            // Wrap the value with wildcards to apply fuzzy-matching for the user.
            filter[operator] = `*${value}*`;
            continue;
          }
          case '$in':
          case '$notIn': {
            filter[operator] = parseIds(value);
            continue;
          }
          default: {
            ctx.issues.push({
              code: 'custom',
              message: `Unknown advanced filter operator "${operator}"`,
              input: raw,
            });
            return z.NEVER;
          }
        }
      }
      return filter;
    },
    encode: (filter) => {
      return Object.keys(filter)
        .map((operator) => {
          switch (operator) {
            case '$eq':
            case '$neq':
            case '$exists': {
              const value = filter[operator]!;
              return encodeFilterOperation(operator, value);
            }
            case '$like': {
              const value = filter[operator]!;
              // Strip the wildcards added on decode so the URL stays clean.
              const stripped = value.replace(/^\*/, '').replace(/\*$/, '');
              return encodeFilterOperation(operator, stripped);
            }
            case '$in':
            case '$notIn': {
              const value = filter[operator]!.join(' ');
              return encodeFilterOperation(operator, value);
            }
            default: {
              return null;
            }
          }
        })
        .filter((part) => part !== null)
        .join(FILTER_SEPARATOR);
    },
  },
);

export {
  advancedStringFilterCodec,
  encodeFilterOperation,
  splitEncodedFilterOperation,
};
export type {AdvancedStringFilterOperator, AdvancedStringFilter};
