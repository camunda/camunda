/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type DisposableData<Data> = Data & AsyncDisposable;

/** Attaches a `Symbol.asyncDispose` property to the given object. */
function makeDisposable<Data extends object>(
  data: Data,
  dispose: () => Promise<void>,
): DisposableData<Data> {
  const disposable = data as DisposableData<Data>;
  disposable[Symbol.asyncDispose] = dispose;
  return disposable;
}

export {makeDisposable, type DisposableData};
