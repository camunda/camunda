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
 * {@code callTimeout} bounds each read of the vault, retries and signing in included, and {@code
 * attemptTimeout} each HTTP attempt to the vault within it, and connecting to sign in. Only
 * throttling (408, 429), server errors (500, 502, 503, 504), network errors and timeouts are
 * retried, at most {@code maxRetries} times, with a jittered backoff; a rejected sign-in is not
 * retried, and the waits between retries, backoff and {@code Retry-After} alike, must fit in half
 * of {@code callTimeout}, or the vault's answer is reported rather than waited out.
 *
 * <p>A missing secret is reported as not found, a 403 as access denied (with one WARN per store
 * naming the role the identity may lack) and a 400 as an invalid reference. A 403 from the vault's
 * firewall or private-endpoint rules, a failed sign-in, any other answer, or no answer within
 * {@code callTimeout} makes the store unavailable. A message carries Key Vault's own status, error
 * code and message when the vault answered, and the network error otherwise, see {@link
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
 * unchanged, and an empty value is reported as not found. A reference that is empty or whose Key
 * Vault name, path prefix included, is not 1 to 127 letters, digits and hyphens is refused without
 * calling the vault; a disabled secret, one at or past its expiry date or one not active yet is
 * refused as not found, naming the reason. The dates are compared with the clock of the node
 * reading the vault. A value cached before its secret expired or was disabled can still be served
 * until the next read of the vault drops it, at the latest when its cache entry expires ({@code
 * camunda.secrets.cache.ttl}). A name Key Vault cannot hold, such as {@code API_KEY}, is refused
 * with a hint to use container mode.
 *
 * <p>In container mode, one read of the container secret's latest version serves every reference of
 * a call. Its value must be a flat JSON object of string values, and each reference is a field
 * name, which Key Vault's naming rules do not limit. Invalid JSON or a value that is not an object
 * fails every reference as an invalid reference, a missing or {@code null} field is not found and a
 * field that is not a string is an invalid reference; a message names the container secret, never
 * its content. A disabled, expired or not yet active container secret is refused, as in one-by-one
 * mode, for every reference. Listing is not implemented yet: no names are listed.
 *
 * <p>See {@link io.camunda.secretstore.azure.AzureKeyVaultStoreConfig} for every setting and its
 * default.
 */
@NullMarked
package io.camunda.secretstore.azure;

import org.jspecify.annotations.NullMarked;
