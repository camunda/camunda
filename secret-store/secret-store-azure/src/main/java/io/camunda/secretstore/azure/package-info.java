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
 * {@code callTimeout} bounds each {@code resolve} or {@code list} call, retries included, and
 * {@code attemptTimeout} each HTTP attempt within it; only throttling, server errors, network
 * errors and timeouts are retried, at most {@code maxRetries} times, honouring {@code Retry-After},
 * with a backoff that fits in half of {@code callTimeout}.
 *
 * <p>A missing secret is reported as not found, a 403 as access denied (with one WARN per store
 * naming the role the identity may lack) and a 400 as an invalid reference. A failed sign-in, any
 * other answer, or no answer within {@code callTimeout} makes the store unavailable. Every message
 * carries Key Vault's own status, error code and message, see {@link
 * io.camunda.secretstore.azure.AzureKeyVaultSecretStore}.
 *
 * <p>Developer logins (Azure CLI, IDEs, PowerShell, Azure Developer CLI) are never used, see {@link
 * io.camunda.secretstore.azure.AzureCredentialChain}.
 *
 * <p>When it is built, the store checks once that it can reach, sign in to and read its vault,
 * waiting at most {@code callTimeout}. It logs one INFO line on success, or one WARN line naming
 * the cause, and the cluster starts either way, see {@link
 * io.camunda.secretstore.azure.StartupCheck}.
 *
 * <p>In one-by-one mode, a reference reads the latest version of its Key Vault secret. The name is
 * passed as written, with no case change and no mapping of {@code _} to {@code -}; since Key Vault
 * names ignore case, two spellings of one name read the same secret. The value is returned
 * unchanged, and an empty value is reported as not found. A reference whose Key Vault name, path
 * prefix included, is not 1 to 127 letters, digits and hyphens is refused without calling the
 * vault; a disabled secret, an expired one or one not active yet is refused as not found, naming
 * the reason. Container mode and listing are not implemented yet (camunda/camunda#67226): every
 * name is reported as not found and no names are listed.
 *
 * <p>See {@link io.camunda.secretstore.azure.AzureKeyVaultStoreConfig} for every setting and its
 * default.
 */
@NullMarked
package io.camunda.secretstore.azure;

import org.jspecify.annotations.NullMarked;
