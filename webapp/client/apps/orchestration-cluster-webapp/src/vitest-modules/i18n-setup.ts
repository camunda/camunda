/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import i18n from 'i18next';
import {initReactI18next} from 'react-i18next';
import en from '#/shared/i18n/locales/en.json';

if (!i18n.isInitialized) {
	i18n.use(initReactI18next).init({
		lng: 'en',
		resources: {en},
		interpolation: {escapeValue: false},
	});
}
