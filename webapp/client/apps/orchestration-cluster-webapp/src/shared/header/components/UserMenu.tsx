/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {observer} from 'mobx-react-lite';
import {HeaderGlobalAction, Menu, MenuItem, MenuItemDivider, MenuItemRadioGroup} from '@carbon/react';
import {ArrowRight, UserAvatar} from '@carbon/react/icons';
import {useC3Profile} from '@camunda/camunda-composite-components';
import {themeStore} from '#/shared/theme/theme';
import {languageItems} from '#/shared/i18n';

const themeOptions = [
	['light', 'tasklist.headerThemeLight'],
	['system', 'tasklist.headerThemeSystem'],
	['dark', 'tasklist.headerThemeDark'],
] as const;

type Props = {
	userName: string;
	canLogout: boolean;
	onLogout: () => void;
};

const UserMenu: React.FC<Props> = observer(({userName, canLogout, onLogout}) => {
	const {t, i18n} = useTranslation();
	const {selectedTheme, changeTheme} = themeStore;
	const {onThemeChange} = useC3Profile();
	const triggerRef = useRef<HTMLDivElement>(null);
	const [position, setPosition] = useState<{x: [number, number]; y: [number, number]} | null>(null);
	const label = t('headerUserMenuLabel', {name: userName});

	return (
		<>
			<div ref={triggerRef} onMouseDown={(event) => event.preventDefault()}>
				<HeaderGlobalAction
					aria-label={label}
					tooltipAlignment="end"
					isActive={position !== null}
					aria-haspopup
					aria-expanded={position !== null}
					onClick={() => {
						const rect = triggerRef.current?.getBoundingClientRect();
						setPosition(
							position !== null || rect === undefined ? null : {x: [rect.left, rect.right], y: [rect.top, rect.bottom]},
						);
					}}
				>
					<UserAvatar size={20} />
				</HeaderGlobalAction>
			</div>
			<Menu
				label={label}
				menuAlignment="bottom-end"
				open={position !== null}
				onClose={() => setPosition(null)}
				x={position?.x}
				y={position?.y}
			>
				<MenuItem label={userName} disabled />
				<MenuItemDivider />
				<MenuItemRadioGroup
					label={t('tasklist.headerThemeLabel')}
					items={themeOptions.map(([value]) => value)}
					itemToString={(item) => t(themeOptions.find(([value]) => value === item)?.[1] ?? '')}
					selectedItem={selectedTheme}
					onChange={(item) => {
						const theme = themeOptions.find(([value]) => value === item)?.[0];
						if (theme !== undefined) {
							changeTheme(theme);
							onThemeChange(theme);
						}
					}}
				/>
				<MenuItemDivider />
				<MenuItemRadioGroup
					label={t('languageSelectorTitle')}
					items={languageItems.map(({id}) => id)}
					itemToString={(item) => languageItems.find(({id}) => id === item)?.label ?? ''}
					selectedItem={i18n.resolvedLanguage}
					onChange={(item) => {
						const language = String(item);
						i18n.changeLanguage(language);
						localStorage.setItem('language', language);
					}}
				/>
				<MenuItemDivider />
				{window.Osano?.cm === undefined ? null : (
					<MenuItem
						label={t('headerCookiePreferencesLabel')}
						onClick={() => {
							window.Osano?.cm?.showDrawer('osano-cm-dom-info-dialog-open');
						}}
					/>
				)}
				<MenuItem
					label={t('headerTermsOfUseLabel')}
					onClick={() => {
						window.open('https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/', '_blank');
					}}
				/>
				<MenuItem
					label={t('headerPrivacyPolicyLabel')}
					onClick={() => {
						window.open('https://camunda.com/legal/privacy/', '_blank');
					}}
				/>
				<MenuItem
					label={t('headerImprintLabel')}
					onClick={() => {
						window.open('https://camunda.com/legal/imprint/', '_blank');
					}}
				/>
				{import.meta.env.VITE_VERSION ? <MenuItem label={`Version ${import.meta.env.VITE_VERSION}`} disabled /> : null}
				{canLogout ? (
					<>
						<MenuItemDivider />
						<MenuItem label={t('headerLogOutLabel')} renderIcon={ArrowRight} onClick={onLogout} />
					</>
				) : null}
			</Menu>
		</>
	);
});

export {UserMenu};
