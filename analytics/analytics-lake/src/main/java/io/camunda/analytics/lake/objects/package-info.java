/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * Object-centric process mining (OCPM) object-type declarations: configuration-as-code ({@link
 * io.camunda.analytics.lake.objects.ObjectTypes}) describing which process variables and/or
 * message-correlation keys identify an "object" (e.g. an order, a customer), plus, optionally,
 * which process completions close it ({@link io.camunda.analytics.lake.objects.ClosingRule}),
 * compiled and validated into a {@link io.camunda.analytics.lake.objects.CompiledObjectTypes}
 * registry. This package is declarations only — capture (sightings, instance links, object
 * relations, object lifecycle) lives in {@code io.camunda.analytics.lake.translate.LakeTranslator}.
 */
package io.camunda.analytics.lake.objects;
