/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * A {@link io.camunda.secretstore.SecretStore} backed by Azure Key Vault.
 *
 * <p>A reference name is looked up in the vault at {@code vaultUrl}. By default every reference is
 * its own Key Vault secret, named {@code pathPrefix + name}. When {@code containerSecretId} is set,
 * every reference is instead a key of one JSON Key Vault secret, named {@code pathPrefix +
 * containerSecretId}.
 *
 * <p>The store signs in with managed identity (system-assigned, or the user-assigned identity named
 * by {@code managedIdentityClientId}), workload identity, or a service principal supplied through
 * the standard {@code AZURE_*} environment variables; no credential is accepted in the store's
 * configuration. {@code authorityHost} selects the Microsoft Entra authority for sovereign clouds.
 * {@code maxRetries}, {@code callTimeout} and {@code attemptTimeout} bound each call to the vault.
 *
 * <p>Developer logins (Azure CLI, IDEs, PowerShell, Azure Developer CLI) are never used, see {@link
 * io.camunda.secretstore.azure.AzureCredentialChain}.
 *
 * <p>Reading the vault is not implemented yet (camunda/camunda#67226): until then every name is
 * reported as not found and no names are listed.
 *
 * <p>See {@link io.camunda.secretstore.azure.AzureKeyVaultStoreConfig} for every setting and its
 * default.
 */
@NullMarked
package io.camunda.secretstore.azure;

import org.jspecify.annotations.NullMarked;
