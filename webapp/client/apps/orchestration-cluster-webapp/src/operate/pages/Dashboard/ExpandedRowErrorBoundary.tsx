/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Component} from 'react';
import type {ReactNode} from 'react';

type Props = {children: ReactNode};
type State = {hasError: boolean};

class ExpandedRowErrorBoundary extends Component<Props, State> {
	state: State = {hasError: false};

	static getDerivedStateFromError(): State {
		return {hasError: true};
	}

	render() {
		if (this.state.hasError) {
			return null;
		}
		return this.props.children;
	}
}

export {ExpandedRowErrorBoundary};
