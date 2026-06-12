/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Dropdown, Layer, type OnChangeData, SwitcherDivider} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import {languageItems, type SelectionOption} from '#/shared/i18n';
import styles from './Header.module.scss';

const LanguageSelector: React.FC = () => {
	const {i18n, t} = useTranslation();
	const selectedLanguage = i18n.resolvedLanguage;

	const handleLanguageChange = (e: OnChangeData<SelectionOption>) => {
		const newLanguage = e.selectedItem?.id;

		if (newLanguage !== undefined) {
			i18n.changeLanguage(newLanguage);
			localStorage.setItem('language', newLanguage);
		}
	};

	return (
		<Layer>
			<SwitcherDivider />
			<div className={styles.languageDropdownPadding}>
				<Dropdown
					id="language-dropdown"
					label={t('languageSelectorLabel')}
					titleText={t('languageSelectorTitle')}
					items={languageItems}
					itemToString={(item) => (item ? item.label : '')}
					onChange={handleLanguageChange}
					selectedItem={languageItems.find((item) => item.id === selectedLanguage)}
				/>
			</div>
		</Layer>
	);
};

export {LanguageSelector};
