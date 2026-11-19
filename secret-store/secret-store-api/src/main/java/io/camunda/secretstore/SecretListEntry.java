/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

/**
 * A secret name together with the state its store reports for it.
 *
 * @param name the secret name as the store holds it
 * @param status the state of the secret in the store
 */
public record SecretListEntry(String name, SecretStatus status) {}
