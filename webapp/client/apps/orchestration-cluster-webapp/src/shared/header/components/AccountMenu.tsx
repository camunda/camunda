/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Check, LogOut} from 'lucide-react';
import {useTranslation} from 'react-i18next';
import {observer} from 'mobx-react-lite';
import {
	Avatar,
	Button,
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuItem,
	DropdownMenuLabel,
	DropdownMenuRadioGroup,
	DropdownMenuRadioItem,
	DropdownMenuSeparator,
	DropdownMenuTrigger,
} from '@camunda/design-system';
import {themeStore} from '#/shared/theme/theme';
import {languageItems} from '#/shared/i18n';
import styles from './AccountMenu.module.scss';

type Props = {
	displayName: string;
	canLogout: boolean;
	onLogout: () => void;
	// window.Osano is only ever present once the third-party cookie-consent
	// script has loaded — same gate Header.tsx used for the Carbon path.
	onOpenCookiePreferences?: () => void;
};

// DS-only. The packaged @camunda/design-system UserMenu always renders its
// own "Log out" item — it has no way to omit it — but canLogout can be false
// (some auth setups can't log out client-side), and the Carbon path hides the
// button entirely in that case. Composed by hand from DropdownMenu primitives
// instead, so the logout item stays conditional like the original.
const AccountMenu: React.FC<Props> = observer(({displayName, canLogout, onLogout, onOpenCookiePreferences}) => {
	const {t, i18n} = useTranslation();
	const {selectedTheme, changeTheme} = themeStore;
	const selectedLanguage = i18n.resolvedLanguage;

	return (
		<DropdownMenu>
			<DropdownMenuTrigger asChild>
				<Button variant="ghost" size="icon" className="rounded-full" aria-label={t('headerSettingsLabel')}>
					<Avatar name={displayName} size="sm" aria-hidden />
				</Button>
			</DropdownMenuTrigger>
			<DropdownMenuContent align="end" className="w-56">
				<DropdownMenuLabel className="font-normal">{displayName}</DropdownMenuLabel>

				<DropdownMenuSeparator />
				<DropdownMenuLabel>{t('tasklist.headerThemeLabel')}</DropdownMenuLabel>
				<DropdownMenuRadioGroup
					value={selectedTheme}
					onValueChange={(value) => {
						if (value === 'system' || value === 'dark' || value === 'light') {
							changeTheme(value);
						}
					}}
				>
					{(
						[
							['system', t('tasklist.headerThemeSystem')],
							['light', t('tasklist.headerThemeLight')],
							['dark', t('tasklist.headerThemeDark')],
						] as const
					).map(([value, label]) => (
						<DropdownMenuRadioItem key={value} value={value} className={styles.option}>
							{label}
							{selectedTheme === value ? <Check className={styles.optionCheck} aria-hidden /> : null}
						</DropdownMenuRadioItem>
					))}
				</DropdownMenuRadioGroup>

				<DropdownMenuSeparator />
				<DropdownMenuLabel>{t('languageSelectorTitle')}</DropdownMenuLabel>
				<DropdownMenuRadioGroup
					value={selectedLanguage}
					onValueChange={(newLanguage) => {
						i18n.changeLanguage(newLanguage);
						localStorage.setItem('language', newLanguage);
					}}
				>
					{languageItems.map((item) => (
						<DropdownMenuRadioItem key={item.id} value={item.id} className={styles.option}>
							{item.label}
							{selectedLanguage === item.id ? <Check className={styles.optionCheck} aria-hidden /> : null}
						</DropdownMenuRadioItem>
					))}
				</DropdownMenuRadioGroup>

				<DropdownMenuSeparator />
				{onOpenCookiePreferences ? (
					<DropdownMenuItem onSelect={onOpenCookiePreferences}>{t('headerCookiePreferencesLabel')}</DropdownMenuItem>
				) : null}
				<DropdownMenuItem
					onSelect={() => {
						window.open(
							'https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/',
							'_blank',
							'noopener,noreferrer',
						);
					}}
				>
					{t('headerTermsOfUseLabel')}
				</DropdownMenuItem>
				<DropdownMenuItem
					onSelect={() => {
						window.open('https://camunda.com/legal/privacy/', '_blank', 'noopener,noreferrer');
					}}
				>
					{t('headerPrivacyPolicyLabel')}
				</DropdownMenuItem>
				<DropdownMenuItem
					onSelect={() => {
						window.open('https://camunda.com/legal/imprint/', '_blank', 'noopener,noreferrer');
					}}
				>
					{t('headerImprintLabel')}
				</DropdownMenuItem>

				{canLogout ? (
					<>
						<DropdownMenuSeparator />
						<DropdownMenuItem onSelect={onLogout}>
							<LogOut aria-hidden />
							{t('headerLogOutLabel')}
						</DropdownMenuItem>
					</>
				) : null}
			</DropdownMenuContent>
		</DropdownMenu>
	);
});

export {AccountMenu};
