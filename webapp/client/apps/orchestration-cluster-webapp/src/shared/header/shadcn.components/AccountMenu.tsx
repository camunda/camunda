/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {observer} from 'mobx-react-lite';
import {LogOut} from 'lucide-react';
import {
	Avatar,
	Button,
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuItem,
	DropdownMenuLabel,
	DropdownMenuSeparator,
	DropdownMenuTrigger,
	Label,
	RadioGroup,
	RadioGroupItem,
	Text,
} from '@camunda/design-system';
import {languageItems} from '#/shared/i18n';
import {themeStore} from '#/shared/theme/theme';
import {useCallback} from 'react';

const themeOptions = [
	['light', 'tasklist.headerThemeLight'],
	['system', 'tasklist.headerThemeSystem'],
	['dark', 'tasklist.headerThemeDark'],
] as const;

type Props = {
	displayName: string;
	canLogout: boolean;
	onLogout: () => void;
	onOpenCookiePreferences?: () => void;
};

const AccountMenu: React.FC<Props> = observer(({displayName, canLogout, onLogout, onOpenCookiePreferences}) => {
	const {t, i18n} = useTranslation();
	const {selectedTheme, changeTheme} = themeStore;
	const selectedLanguage = i18n.language.split('-')[0];
	const handleLegalTermsClick = useCallback(() => {
		window.open(
			'https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/',
			'_blank',
			'noopener,noreferrer',
		);
	}, []);
	const handlePrivacyPolicyClick = useCallback(() => {
		window.open('https://camunda.com/legal/privacy/', '_blank', 'noopener,noreferrer');
	}, []);
	const handleImprintClick = useCallback(() => {
		window.open('https://camunda.com/legal/imprint/', '_blank', 'noopener,noreferrer');
	}, []);

	return (
		<DropdownMenu>
			<DropdownMenuTrigger asChild>
				<Button
					type="button"
					variant="ghost"
					size="icon"
					className="rounded-full"
					aria-label={t('headerSettingsLabel')}
				>
					<Avatar name={displayName} size="sm" aria-hidden />
				</Button>
			</DropdownMenuTrigger>
			<DropdownMenuContent align="end" className="w-56">
				<DropdownMenuLabel className="font-normal">{displayName}</DropdownMenuLabel>
				<DropdownMenuSeparator />
				<div className="px-2 py-1.5">
					<Text as="span" variant="label-sm" className="text-neutral-foreground-subtle">
						{t('tasklist.headerThemeLabel')}
					</Text>
					<RadioGroup
						aria-label={t('tasklist.headerThemeLabel')}
						value={selectedTheme}
						onValueChange={(value) => {
							changeTheme(value as typeof selectedTheme);
						}}
						className="mt-2 gap-2"
					>
						{themeOptions.map(([value, label]) => (
							<Label key={value} className="flex items-center gap-2">
								<RadioGroupItem value={value} />
								{t(label)}
							</Label>
						))}
					</RadioGroup>
				</div>

				<DropdownMenuSeparator />
				<div className="px-2 py-1.5">
					<Text as="span" variant="label-sm" className="text-neutral-foreground-subtle">
						{t('languageSelectorTitle')}
					</Text>
					<RadioGroup
						aria-label={t('languageSelectorTitle')}
						value={selectedLanguage}
						onValueChange={(value) => {
							i18n.changeLanguage(value);
							localStorage.setItem('language', value);
						}}
						className="mt-2 gap-2"
					>
						{languageItems.map(({id, label}) => (
							<Label key={id} className="flex items-center gap-2">
								<RadioGroupItem value={id} />
								{label}
							</Label>
						))}
					</RadioGroup>
				</div>

				<DropdownMenuSeparator />
				{onOpenCookiePreferences === undefined ? null : (
					<DropdownMenuItem onClick={onOpenCookiePreferences}>{t('headerCookiePreferencesLabel')}</DropdownMenuItem>
				)}
				<DropdownMenuItem onClick={handleLegalTermsClick}>{t('headerTermsOfUseLabel')}</DropdownMenuItem>
				<DropdownMenuItem onClick={handlePrivacyPolicyClick}>{t('headerPrivacyPolicyLabel')}</DropdownMenuItem>
				<DropdownMenuItem onClick={handleImprintClick}>{t('headerImprintLabel')}</DropdownMenuItem>

				{canLogout ? (
					<>
						<DropdownMenuSeparator />
						<DropdownMenuItem onClick={onLogout}>
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
