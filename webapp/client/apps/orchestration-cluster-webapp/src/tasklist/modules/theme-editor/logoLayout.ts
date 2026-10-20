/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * The header gives the logo a fixed 56px column, and the logo itself gets a 40px box inside it.
 * A wider logo, such as a symbol with the company name, overflows to the right, so whatever
 * follows the logo has to move over by the overflow.
 *
 * This works around the design system header, whose logo column does not grow with its content.
 * Delete it if the header ever sizes that column to fit the logo.
 */
const LOGO_BOX_WIDTH = 40;

/** How far to push the breadcrumb right so a logo wider than its box does not cover it. */
function getBreadcrumbOffset(logoWidth: number) {
	return Math.max(0, Math.round(logoWidth) - LOGO_BOX_WIDTH);
}

export {getBreadcrumbOffset};
