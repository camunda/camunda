/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {C4Dictionary} from '@camunda/design-system';

type ShadcnDictionaryKey =
	| 'appHeader.skipToContent'
	| 'appSidebar.closeNavigation'
	| 'appSidebar.collapse'
	| 'appSidebar.collapseText'
	| 'appSidebar.expand'
	| 'dialog.close'
	| 'navBreadcrumb.actionsLabel'
	| 'navBreadcrumb.hiddenLevels'
	| 'navBreadcrumb.switchContext'
	| 'notifications.dismissAll'
	| 'notifications.dismissItem'
	| 'notifications.totalCount'
	| 'notifications.triggerLabel'
	| 'notifications.unreadCount'
	| 'notifications.unreadPrefix'
	| 'sheet.close'
	| 'sidebarProvider.closeNavigation'
	| 'sidebarProvider.openNavigation'
	| 'toast.dismiss';

type ShadcnDictionary = Pick<C4Dictionary, ShadcnDictionaryKey>;
type TranslatedLocale = 'de' | 'es' | 'fr';
type C4Locale = 'en' | TranslatedLocale;

const C4_DICTIONARIES: Record<TranslatedLocale, ShadcnDictionary> = {
	de: {
		'appHeader.skipToContent': 'Zum Hauptinhalt springen',
		'appSidebar.closeNavigation': 'Navigation schließen',
		'appSidebar.collapse': 'Seitenleiste einklappen',
		'appSidebar.collapseText': 'Einklappen',
		'appSidebar.expand': 'Seitenleiste erweitern',
		'dialog.close': 'Schließen',
		'navBreadcrumb.actionsLabel': 'Aktionen für {label}',
		'navBreadcrumb.hiddenLevels':
			'{count, plural, one {# ausgeblendete Ebene anzeigen} other {# ausgeblendete Ebenen anzeigen}}',
		'navBreadcrumb.switchContext': 'Kontext wechseln',
		'notifications.dismissAll': 'Alle entfernen',
		'notifications.dismissItem': '{title} entfernen',
		'notifications.totalCount': '{count, plural, one {# Benachrichtigung} other {# Benachrichtigungen}}',
		'notifications.triggerLabel':
			'{label}, {count, plural, =0 {keine ungelesenen Benachrichtigungen} one {# ungelesene Benachrichtigung} other {# ungelesene Benachrichtigungen}}',
		'notifications.unreadCount':
			'{count, plural, one {# ungelesene Benachrichtigung} other {# ungelesene Benachrichtigungen}}',
		'notifications.unreadPrefix': 'Ungelesen. ',
		'sheet.close': 'Schließen',
		'sidebarProvider.closeNavigation': 'Navigation schließen',
		'sidebarProvider.openNavigation': 'Navigation öffnen',
		'toast.dismiss': 'Schließen',
	},
	es: {
		'appHeader.skipToContent': 'Ir al contenido principal',
		'appSidebar.closeNavigation': 'Cerrar navegación',
		'appSidebar.collapse': 'Contraer barra lateral',
		'appSidebar.collapseText': 'Contraer',
		'appSidebar.expand': 'Expandir barra lateral',
		'dialog.close': 'Cerrar',
		'navBreadcrumb.actionsLabel': 'Acciones de {label}',
		'navBreadcrumb.hiddenLevels': '{count, plural, one {Mostrar # nivel oculto} other {Mostrar # niveles ocultos}}',
		'navBreadcrumb.switchContext': 'Cambiar contexto',
		'notifications.dismissAll': 'Descartar todas',
		'notifications.dismissItem': 'Descartar {title}',
		'notifications.totalCount': '{count, plural, one {# notificación} other {# notificaciones}}',
		'notifications.triggerLabel':
			'{label}, {count, plural, =0 {ninguna notificación sin leer} one {# notificación sin leer} other {# notificaciones sin leer}}',
		'notifications.unreadCount': '{count, plural, one {# notificación sin leer} other {# notificaciones sin leer}}',
		'notifications.unreadPrefix': 'Sin leer. ',
		'sheet.close': 'Cerrar',
		'sidebarProvider.closeNavigation': 'Cerrar navegación',
		'sidebarProvider.openNavigation': 'Abrir navegación',
		'toast.dismiss': 'Cerrar',
	},
	fr: {
		'appHeader.skipToContent': 'Aller au contenu principal',
		'appSidebar.closeNavigation': 'Fermer la navigation',
		'appSidebar.collapse': 'Réduire la barre latérale',
		'appSidebar.collapseText': 'Réduire',
		'appSidebar.expand': 'Développer la barre latérale',
		'dialog.close': 'Fermer',
		'navBreadcrumb.actionsLabel': 'Actions pour {label}',
		'navBreadcrumb.hiddenLevels': '{count, plural, one {Afficher # niveau masqué} other {Afficher # niveaux masqués}}',
		'navBreadcrumb.switchContext': 'Changer de contexte',
		'notifications.dismissAll': 'Tout supprimer',
		'notifications.dismissItem': 'Supprimer {title}',
		'notifications.totalCount': '{count, plural, one {# notification} other {# notifications}}',
		'notifications.triggerLabel':
			'{label}, {count, plural, =0 {aucune notification non lue} one {# notification non lue} other {# notifications non lues}}',
		'notifications.unreadCount': '{count, plural, one {# notification non lue} other {# notifications non lues}}',
		'notifications.unreadPrefix': 'Non lue. ',
		'sheet.close': 'Fermer',
		'sidebarProvider.closeNavigation': 'Fermer la navigation',
		'sidebarProvider.openNavigation': 'Ouvrir la navigation',
		'toast.dismiss': 'Fermer',
	},
};

function isTranslatedLocale(locale: string | undefined): locale is TranslatedLocale {
	const supportedLocales = ['de', 'es', 'fr'];
	return supportedLocales.includes(locale ?? '');
}

function getC4Locale(language: string | undefined): C4Locale {
	const baseLanguage = language?.toLowerCase().split('-')[0];

	return isTranslatedLocale(baseLanguage) ? baseLanguage : 'en';
}

function getC4Dictionary(locale: C4Locale): ShadcnDictionary | undefined {
	return locale === 'en' ? undefined : C4_DICTIONARIES[locale];
}

export {getC4Dictionary, getC4Locale};
