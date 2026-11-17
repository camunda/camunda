/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Stand-in for `@camunda/design-system` under Jest. The design system reaches its
 * dependencies (radix-ui, cmdk) through `package.json` "exports", which Jest 27 does
 * not read, so importing the real barrel fails to resolve. Every Optimize test renders
 * shallowly and therefore never runs a design-system component — only names it — so a
 * stub per export is enough here, while `tsc` keeps checking against the real types.
 */

const {createElement, forwardRef} = require('react');

const stubs = new Map();

function stub(name) {
  if (!stubs.has(name)) {
    const Stub = forwardRef((props, ref) => createElement('div', {...props, ref}));
    Stub.displayName = name;
    stubs.set(name, Stub);
  }

  return stubs.get(name);
}

const CAMUNDA_APP_KEYS = [
  'operate',
  'tasklist',
  'optimize',
  'admin',
  'identity',
  'modeler',
  'console',
];

const overrides = {
  __esModule: true,
  // Reads as "no sidebar context", the branch that leaves the content offset at zero.
  useSidebar: () => null,
  useIsMobile: () => false,
  cn: (...classNames) => classNames.filter(Boolean).join(' '),
  camundaAppIcons: Object.fromEntries(CAMUNDA_APP_KEYS.map((key) => [key, stub(`${key}Icon`)])),
};

module.exports = new Proxy(overrides, {
  get(target, property) {
    if (property in target) {
      return target[property];
    }

    // Anything non-string (or a thenable probe) must not look like a component.
    if (typeof property !== 'string' || property === 'then') {
      return undefined;
    }

    return stub(property);
  },
});
