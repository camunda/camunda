/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {RouteContext, ResponseFieldSpec} from './types';

interface ValidationOptions {
  arraySampleLimit: number;
}

const isPrimitive = (t: string): t is 'string' | 'number' | 'boolean' =>
  t === 'string' || t === 'number' || t === 'boolean';

const extractArrayInner = (typeStr: string): string | null => {
  const m = /^array<(.+)>$/.exec(typeStr.trim());
  return m ? m[1].trim() : null;
};

const shouldTreatAsEnum = (typeStr: string): boolean => /Enum$/.test(typeStr);

const shouldTreatAsObject = (spec: ResponseFieldSpec): boolean => {
  if (spec.children) return true;
  if (spec.type === 'object') return true;
  if (/^[A-Z][A-Za-z0-9]+$/.test(spec.type) && !shouldTreatAsEnum(spec.type)) {
    return true;
  }
  return false;
};

const jsonPointerEscape = (seg: string) =>
  seg.replace(/~/g, '~0').replace(/\//g, '~1');

const toExpectedDescriptor = (spec: ResponseFieldSpec): string => {
  const inner = extractArrayInner(spec.type);
  if (inner) return `array<${inner}>`;
  if (spec.wrapper && spec.underlyingPrimitive) {
    return `${spec.underlyingPrimitive} (wrapper ${spec.rawRefName || spec.type})`;
  }
  if (spec.enumValues?.length) {
    return `enum<${spec.underlyingPrimitive || 'string'}>[${spec.enumValues.join('|')}]`;
  }
  if (spec.children || spec.type === 'object') return 'object';
  return spec.type;
};

const actualDescriptor = (val: unknown): string => {
  if (val === null) return 'null';
  if (Array.isArray(val)) return 'array';
  return typeof val;
};

export function validateResponseShape(routeCtx: RouteContext, body: unknown) {
  if (!routeCtx.route || routeCtx.requiredFields.length === 0) return; // no spec
  if (body === null || typeof body !== 'object' || Array.isArray(body)) {
    throw new Error(
      `Expected object response body to validate route required fields for ${routeCtx.route}`,
    );
  }
  const options: ValidationOptions = {arraySampleLimit: 25};
  const errors: string[] = [];

  const checkPrimitiveType = (val: unknown, expected: string): boolean =>
    typeof val === expected;

  const validateGroup = (
    obj: Record<string, unknown>,
    specs: ResponseFieldSpec[],
    pointer: string,
    mode: 'required' | 'optional-present',
    optionalSpecsForExtras?: ResponseFieldSpec[],
  ) => {
    if (mode === 'required') {
      const allowed = new Set<string>();
      for (const s of specs) allowed.add(s.name);
      if (optionalSpecsForExtras) {
        for (const s of optionalSpecsForExtras) allowed.add(s.name);
      }
      for (const key of Object.keys(obj)) {
        if (!allowed.has(key)) {
          const fieldPath = pointer
            ? `${pointer}/${jsonPointerEscape(key)}`
            : `/${jsonPointerEscape(key)}`;
          errors.push(`[EXTRA] ${fieldPath} is not declared in spec`);
        }
      }
    }
    for (const spec of specs) {
      const fieldPath = pointer
        ? `${pointer}/${jsonPointerEscape(spec.name)}`
        : `/${jsonPointerEscape(spec.name)}`;
      const exists = spec.name in obj;
      if (!exists) {
        if (mode === 'required') {
          errors.push(
            `[MISSING] ${fieldPath} expected ${toExpectedDescriptor(spec)}`,
          );
        }
        continue;
      }
      const value = (obj as Record<string, unknown>)[spec.name];
      if (value === null) {
        errors.push(
          `[TYPE] ${fieldPath} expected ${toExpectedDescriptor(spec)} but got null`,
        );
        continue;
      }
      const inner = extractArrayInner(spec.type);
      if (inner) {
        if (!Array.isArray(value)) {
          errors.push(
            `[TYPE] ${fieldPath} expected array<${inner}> but got ${actualDescriptor(value)}`,
          );
        } else if (value.length > 0) {
          const sample = (value as unknown[]).slice(
            0,
            options.arraySampleLimit,
          );
          if (isPrimitive(inner)) {
            for (let i = 0; i < sample.length; i++) {
              const el = sample[i];
              if (typeof el !== inner) {
                errors.push(
                  `[TYPE] ${fieldPath}/${i} expected ${inner} but got ${actualDescriptor(el)}`,
                );
              }
            }
          } else if (spec.children) {
            for (let i = 0; i < sample.length; i++) {
              const el = sample[i];
              if (el === null || typeof el !== 'object' || Array.isArray(el)) {
                errors.push(
                  `[TYPE] ${fieldPath}/${i} expected object but got ${actualDescriptor(el)}`,
                );
                continue;
              }
              validateGroup(
                el as Record<string, unknown>,
                spec.children.required || [],
                `${fieldPath}/${i}`,
                'required',
                spec.children.optional,
              );
              if (spec.children.optional?.length) {
                validateGroup(
                  el as Record<string, unknown>,
                  spec.children.optional,
                  `${fieldPath}/${i}`,
                  'optional-present',
                );
              }
            }
          }
        }
        continue;
      }
      if (
        spec.wrapper &&
        spec.underlyingPrimitive &&
        isPrimitive(spec.underlyingPrimitive)
      ) {
        if (!checkPrimitiveType(value, spec.underlyingPrimitive)) {
          errors.push(
            `[TYPE] ${fieldPath} expected ${toExpectedDescriptor(spec)} but got ${actualDescriptor(value)}`,
          );
        }
        if (spec.enumValues && typeof value === 'string') {
          if (!spec.enumValues.includes(value)) {
            errors.push(
              `[ENUM] ${fieldPath} value ${JSON.stringify(value)} not in [${spec.enumValues.join(', ')}]`,
            );
          }
        }
        continue;
      }
      if (isPrimitive(spec.type)) {
        if (!checkPrimitiveType(value, spec.type)) {
          errors.push(
            `[TYPE] ${fieldPath} expected ${toExpectedDescriptor(spec)} but got ${actualDescriptor(value)}`,
          );
        }
        if (spec.enumValues && typeof value === 'string') {
          if (!spec.enumValues.includes(value)) {
            errors.push(
              `[ENUM] ${fieldPath} value ${JSON.stringify(value)} not in [${spec.enumValues.join(', ')}]`,
            );
          }
        }
        continue;
      }
      if (shouldTreatAsEnum(spec.type)) {
        if (typeof value !== 'string') {
          errors.push(
            `[TYPE] ${fieldPath} expected ${toExpectedDescriptor(spec)} but got ${actualDescriptor(value)}`,
          );
        }
        if (spec.enumValues && typeof value === 'string') {
          if (!spec.enumValues.includes(value)) {
            errors.push(
              `[ENUM] ${fieldPath} value ${JSON.stringify(value)} not in [${spec.enumValues.join(', ')}]`,
            );
          }
        }
        continue;
      }
      if (shouldTreatAsObject(spec)) {
        if (typeof value !== 'object' || Array.isArray(value)) {
          errors.push(
            `[TYPE] ${fieldPath} expected object but got ${actualDescriptor(value)}`,
          );
          continue;
        }
        if (spec.children) {
          validateGroup(
            value as Record<string, unknown>,
            spec.children.required || [],
            fieldPath,
            'required',
            spec.children.optional,
          );
          if (spec.children.optional?.length) {
            validateGroup(
              value as Record<string, unknown>,
              spec.children.optional,
              fieldPath,
              'optional-present',
            );
          }
        }
        continue;
      }
    }
  };

  validateGroup(
    body as Record<string, unknown>,
    routeCtx.requiredFields,
    '',
    'required',
    routeCtx.optionalFields,
  );
  if (routeCtx.optionalFields.length) {
    validateGroup(
      body as Record<string, unknown>,
      routeCtx.optionalFields,
      '',
      'optional-present',
    );
  }
  if (errors.length) {
    const preview = errors.slice(0, 15).join('\n');
    const extra =
      errors.length > 15 ? `\n...and ${errors.length - 15} more` : '';
    throw new Error(
      `Response shape errors for route ${routeCtx.method || '*'} ${routeCtx.status || '*'} ${routeCtx.route}:\n${preview}${extra}`,
    );
  }
}
