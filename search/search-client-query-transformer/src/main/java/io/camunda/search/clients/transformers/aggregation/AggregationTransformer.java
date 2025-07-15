/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import io.camunda.search.aggregation.AggregationBase;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;

public interface AggregationTransformer<A extends AggregationBase>
    extends ServiceTransformer<Tuple<A, ServiceTransformers>, List<SearchAggregator>> {}
