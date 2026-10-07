/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
	Button,
	Card,
	CardContent,
	CardDescription,
	CardFooter,
	CardHeader,
	CardTitle,
	Separator,
} from '@camunda/design-system';
import {ArrowLeft, Moon, RotateCcw, Shuffle, Sun} from '@camunda/design-system/icons';
import {Link} from '@tanstack/react-router';
import {observer} from 'mobx-react-lite';
import {useTranslation} from 'react-i18next';
import {
	ACCENT_COLOR_OPTIONS,
	BASE_COLOR_OPTIONS,
	PRIMARY_COLOR_OPTIONS,
	RADIUS_OPTIONS,
} from '#/tasklist/modules/theme-editor/presets';
import {themeEditorStore} from '#/tasklist/modules/theme-editor/themeEditorStore';
import {ExportDialog} from './ExportDialog';
import {ThemePicker} from './ThemePicker';

type Props = {
	mode: 'light' | 'dark';
	isResetDialogOpen: boolean;
	onToggleMode: () => void;
	onResetDialogOpenChange: (open: boolean) => void;
};

function renderShortcut(keys: string) {
	return (
		<kbd className="ml-auto rounded border border-border px-1 font-mono text-xs text-neutral-foreground-subtle">
			{keys}
		</kbd>
	);
}

const Customizer: React.FC<Props> = observer(({mode, isResetDialogOpen, onToggleMode, onResetDialogOpenChange}) => {
	const {t} = useTranslation();
	const {config, isDefault, setConfig, setOverride, clearOverride, shuffle, reset} = themeEditorStore;

	return (
		<Card size="sm" className="flex max-h-full min-h-0 flex-col self-start">
			<CardHeader>
				<Button asChild variant="ghost" size="sm" className="-ml-2 w-fit">
					<Link to="/tasklist">
						<ArrowLeft aria-hidden />
						{t('tasklist.themeEditorBack')}
					</Link>
				</Button>
				<CardTitle>{t('tasklist.themeEditorTitle')}</CardTitle>
				<CardDescription>{t('tasklist.themeEditorDescription')}</CardDescription>
			</CardHeader>
			<CardContent className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto">
				<ThemePicker
					label={t('tasklist.themeEditorBase')}
					value={config.base}
					options={BASE_COLOR_OPTIONS}
					onSelect={(base) => setConfig({base})}
					onPreview={(base) => setOverride({base})}
					onPreviewEnd={clearOverride}
				/>
				<ThemePicker
					label={t('tasklist.themeEditorPrimary')}
					value={config.primary}
					options={PRIMARY_COLOR_OPTIONS}
					onSelect={(primary) => setConfig({primary})}
					onPreview={(primary) => setOverride({primary})}
					onPreviewEnd={clearOverride}
				/>
				<ThemePicker
					label={t('tasklist.themeEditorAccent')}
					value={config.accent}
					options={ACCENT_COLOR_OPTIONS}
					onSelect={(accent) => setConfig({accent})}
					onPreview={(accent) => setOverride({accent})}
					onPreviewEnd={clearOverride}
				/>
				<Separator className="my-1" />
				<ThemePicker
					label={t('tasklist.themeEditorRadius')}
					value={config.radius}
					options={RADIUS_OPTIONS}
					variant="radius"
					onSelect={(radius) => setConfig({radius})}
					onPreview={(radius) => setOverride({radius})}
					onPreviewEnd={clearOverride}
				/>
			</CardContent>
			<CardFooter className="flex flex-col gap-2">
				<Button type="button" variant="secondary" className="w-full" onClick={onToggleMode}>
					{mode === 'dark' ? <Sun aria-hidden /> : <Moon aria-hidden />}
					{mode === 'dark' ? t('tasklist.themeEditorLightMode') : t('tasklist.themeEditorDarkMode')}
					{renderShortcut('D')}
				</Button>
				<Button type="button" variant="secondary" className="w-full" onClick={shuffle}>
					<Shuffle aria-hidden />
					{t('tasklist.themeEditorShuffle')}
					{renderShortcut('R')}
				</Button>
				<Button
					type="button"
					variant="secondary"
					className="w-full"
					disabled={isDefault}
					onClick={() => onResetDialogOpenChange(true)}
				>
					<RotateCcw aria-hidden />
					{t('tasklist.themeEditorReset')}
					{renderShortcut('⇧R')}
				</Button>
				<ExportDialog />
			</CardFooter>
			<AlertDialog open={isResetDialogOpen} onOpenChange={onResetDialogOpenChange}>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>{t('tasklist.themeEditorResetTitle')}</AlertDialogTitle>
						<AlertDialogDescription>{t('tasklist.themeEditorResetDescription')}</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel>{t('tasklist.themeEditorCancel')}</AlertDialogCancel>
						<AlertDialogAction onClick={reset}>{t('tasklist.themeEditorReset')}</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</Card>
	);
});

export {Customizer};
