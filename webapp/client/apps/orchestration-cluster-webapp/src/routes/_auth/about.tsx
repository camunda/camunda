/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute} from '@tanstack/react-router';

type AboutData = {
	message: string;
};

export const Route = createFileRoute('/_auth/about')({
	loader: async (): Promise<AboutData> => {
		const response = await fetch('/api/about');

		if (!response.ok) {
			throw new Error('Failed to load about data');
		}

		return response.json() as Promise<AboutData>;
	},
	component: About,
	errorComponent: AboutError,
});

function About() {
	const {message} = Route.useLoaderData();

	return (
		<main>
			<h1>About</h1>
			<p>{message}</p>
		</main>
	);
}

function AboutError() {
	return (
		<main>
			<h1>About</h1>
			<p>Unable to load about data</p>
		</main>
	);
}
