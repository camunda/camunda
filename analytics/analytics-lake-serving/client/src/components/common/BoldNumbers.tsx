/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { Fragment } from "react";

// Longest/most-specific alternative first, so e.g. "82%" isn't left as a bare "82" once the "%"
// is (correctly) not consumed by a later, more general alternative. Covers every numeric shape the
// finding-text templates and the digest composer actually emit: signed percentages ("+82%"),
// multiplier lifts ("3.7×"), clock times ("14:20"), thousands-separated counts/currency
// ("1,481", "€4,000"), and plain integers/decimals.
const NUMERIC_TOKEN =
  /([+-]?\d[\d,]*(?:\.\d+)?%|\d+(?:\.\d+)?×|\d{1,2}:\d{2}|€\s?\d[\d,]*(?:\.\d+)?|\d[\d,]*(?:\.\d+)?)/g;

/**
 * Renders a plain sentence with its numeric tokens bolded -- the design sketch's finding cards
 * bold every number in an asserted sentence ("stepped up <strong>+82%</strong> around
 * <strong>14:20</strong>") while leaving the surrounding prose at normal weight. A single shared
 * regex-based splitter rather than per-template markup, so every sentence source (the per-kind
 * templates in lib/findingText.ts, the digest composer's stitched sentence, the two ask-why
 * question handlers' synthetic findings) gets the same treatment for free.
 */
export function BoldNumbers({ text }: { text: string }) {
  const parts = text.split(NUMERIC_TOKEN);
  return (
    <>
      {parts.map((part, i) =>
        // split() with a capturing group alternates plain/matched segments starting at index 0
        // (plain) -- odd indices are always the captured numeric tokens.
        i % 2 === 1 ? (
          <strong key={i} className="font-semibold">
            {part}
          </strong>
        ) : (
          <Fragment key={i}>{part}</Fragment>
        ),
      )}
    </>
  );
}
